import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mongodb.*;
import com.mongodb.MongoClient;
import com.mongodb.client.*;

import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import helper.CorsFilter;
import service.*;
import model.*;
import db.initializeDB;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import org.bson.Document;
import utils.*;

import java.lang.reflect.Array;
import java.util.*;

import static spark.Spark.*;

public class App {

    public static void main(String[] args) {

        MongoClient mongoClient = new MongoClient(new MongoClientURI("mongodb://localhost:27017"));
        CodecRegistry pojoCodecRegistry = org.bson.codecs.configuration.CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), org.bson.codecs.configuration.CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        MongoDatabase database = mongoClient.getDatabase("infoq").withCodecRegistry(pojoCodecRegistry);

        boolean flag = initializeDB.checkDB(mongoClient);

        initializeDB.createCounter(database, flag);
        initializeDB.createData(database, flag);
        initializeDB.createUrl(database, flag);
        initializeDB.createLike(database, flag);
        initializeDB.createUser(database, flag);

        initializeLists.generateLists();

        final LikeService likeService = new LikeService();
        final UrlService urlService = new UrlService();
        final DataService dataService = new DataService();
        final ArticleService articleService = new ArticleService();
        final UserService userService = new UserService();
        final Crawler crawler = new Crawler();

        final CorsFilter corsFilter = new CorsFilter();
        final JwtAuthentication jwtAuthentication = new JwtAuthentication();

        corsFilter.apply();

        before("/v1/*", (request, response) -> {
            String jwt = jwtAuthentication.resolveToken(request);

            // JWT unsuccessfully authenticated
            // TODO : decodeJWT'den exception donunce program nasil davraniyor?
            if (jwt.isEmpty() && !jwtAuthentication.decodeJWT(jwt)) {
                halt(404, "Jwt unsuccessfuly authenticated");
            }
        });

//        before((request, response) -> {
//           log.trace("request : {}", request);
//        });


        // Get datas stored in Data collection
        get("/v1/datas", (request, response) -> {
            response.type("application/json");

            return new Gson().toJson(
                    new StandardResponse(StatusResponse.SUCCESS, StatusResponse.SUCCESS.getStatusCode(),
                            StatusResponse.SUCCESS.getMessage(),new Gson().toJsonTree(dataService.getDatas(database))));
        });

        // Reads each url from text file and then inserts the urls into the Url collection
        get("/v1/urls", (request, response) -> {
            response.type("application/json");

            ArrayList<String> list = crawler.fileToList();

            for (String url : list) {
                Url link = crawler.urlToUrlCollection(url);
                urlService.addUrl(link, database);
            }

            return new Gson().toJson(
                    new StandardResponse(StatusResponse.SUCCESS, StatusResponse.SUCCESS.getStatusCode(),
                            StatusResponse.SUCCESS.getMessage()));
        });

        post("/v1/urls", (request, response) -> {
            response.type("application/json");

            Url url = new Gson().fromJson(request.body(), Url.class);
            urlService.addUrl(url, database);

            return new Gson().toJson(
                    new StandardResponse(StatusResponse.SUCCESS, StatusResponse.SUCCESS.getStatusCode(),
                            StatusResponse.SUCCESS.getMessage()));
        });

        // Reads each url from database, crawls it and then
        // -> Appends each formatted data into articles.csv file
        // -> Inserts each formatted data into Data collection
        // Use /crawl for generating .csv file which contains all articles
        get("/v1/crawl", (request, response) -> {
            response.type("application/json");

            ArrayList<String> urls = urlService.getUrlsAsList(database);

            for (String url : urls) {
                int articleID = counterValue(database);
                Data data = crawler.urlToData(url, articleID);
                crawler.writeDatas(data);
                dataService.addData(data, database);
            }

            return new Gson().toJson(
                    new StandardResponse(StatusResponse.SUCCESS, StatusResponse.SUCCESS.getStatusCode(),
                            StatusResponse.SUCCESS.getMessage(), new Gson().toJsonTree(dataService.getDatas(database))));
        });

        get("/v1/recommend", (request, response) -> {

            response.type("application/json");

            initializeLists.recommendedArticles.clear();

            addLikesToDatabase(urlService, crawler, likeService, database);
            getRecommendedArticles(likeService, articleService, database);
            recommendedArticlesToList(articleService, dataService, database);


            return new Gson().toJson(
                    new StandardResponse(StatusResponse.SUCCESS, StatusResponse.SUCCESS.getStatusCode(),
                            StatusResponse.SUCCESS.getMessage(),
                            new Gson().toJsonTree(initializeLists.recommendedArticles)));
        });

        post("/signin", (request, response) -> {

            try {
                response.type("application/json");
                System.out.println("reach");
                User reqUser = new Gson().fromJson(request.body(), User.class);

                // Creating static list for jwt. This will change in the future with the extension of domain models.
                List<String> roles = new ArrayList<>();
                roles.add("admin");

                Optional<User> user = userService.findUserByUsername(reqUser.getUsername(), database);

                System.out.println(user.toString());

                System.out.println(user.get());

                // Kullanici bulunamadi, kayitli degil.
                if (!user.isPresent()) {
                    return new Gson().toJsonTree(
                            new StandardResponse(StatusResponse.ERROR, StatusResponse.ERROR.getStatusCode(),
                                    StatusResponse.ERROR.getMessage())
                    );
                }

                String token = jwtAuthentication.createToken(reqUser.getUsername(), roles);

                return new Gson().toJson(
                        new StandardResponse(StatusResponse.SUCCESS, StatusResponse.SUCCESS.getStatusCode(),
                                StatusResponse.SUCCESS.getMessage(), new Gson().toJsonTree(token))
                );
            } catch (Exception e) {
                e.printStackTrace();
            }

            return new Gson().toJson(
                    new StandardResponse(StatusResponse.ERROR, StatusResponse.ERROR.getStatusCode(),
                            StatusResponse.ERROR.getMessage())
            );
        });

        post("/signup", (request, response) -> {

            response.type("application/json");

            User user = new Gson().fromJson(request.body(), User.class);

            userService.createOrUpdateUser(user, database);

            return new Gson().toJson(
                    new StandardResponse(StatusResponse.SUCCESS, StatusResponse.SUCCESS.getStatusCode(),
                            StatusResponse.SUCCESS.getMessage())
            );
        });

        get("/v1/users:id", (request, response) -> {

            response.type("application/json");

            int id = Integer.parseInt(request.params(":id"));

            Optional<User> user= userService.findUser(id, database);

            // empty
            if (!user.isPresent()) {
                // User not found!
                return new Gson().toJson(
                    new StandardResponse(StatusResponse.ERROR, StatusResponse.ERROR.getStatusCode(),
                            StatusResponse.SUCCESS.getMessage())
                );
            }

            return new Gson().toJson(
                    new StandardResponse(StatusResponse.SUCCESS, StatusResponse.SUCCESS.getStatusCode(),
                            StatusResponse.SUCCESS.getMessage(), new Gson().toJsonTree(user.get()))
            );
        });

    }

    /*
    // Create's a collection named counter if there's none.
    // Increments counterValue by 1 and returns it.
    // Purpose of this collection : Defines an articleID for each article.
     */
    public static int counterValue(MongoDatabase database) {
        MongoCollection<Counter> collection = database.getCollection("counter", Counter.class);

        Document query = new Document("counterName", "articleID");
        Document update = new Document();
        Document inside = new Document();
        inside.put("counterValue", 1);
        update.put("$inc", inside);

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.returnDocument(ReturnDocument.AFTER);
        options.upsert(true);

        Counter doc = collection.findOneAndUpdate(query, update, options);
        return doc.getCounterValue();
    }

    // Merhaba hocam kisinin begendigi makalelere gore oneren bir sistem yapmistim.
    // Ayni sekilde devam ediyor sistem ama client tarafindan istek gonderiyor artik kullanici.

    public static void addLikesToDatabase(UrlService urlService, Crawler crawler, LikeService likeService, MongoDatabase database) {
        ArrayList<String> urls = urlService.getUrlsAsList(database);

        for (String url : urls) {
            Like like = crawler.urlToLikeCollection(url, database);
//            like.toString();
//            crawler.writeLikes(like);
            likeService.addLike(like, database);
        }
    }

    public static void getRecommendedArticles(LikeService likeService, ArticleService articleService, MongoDatabase database) {
        ArrayList<Like> likes = likeService.getLikesAsList(database);
        Iterator<Like> iter = likes.iterator();

        while(iter.hasNext()) {
            Like like = iter.next();
            JsonObject jsonObject = articleService.getRecommendations(like.getTitle());

            // == instead of equals() maybe?
            if (like.getMainTopic().equals(MainTopics.DEVELOPMENT.getMainTopic())) {
                articleService.JsonObjectToList(jsonObject, initializeLists.development);
            } else if (like.getMainTopic().equals(MainTopics.ARCHITECTURE.getMainTopic())) {
                articleService.JsonObjectToList(jsonObject, initializeLists.architecture);
            } else if (like.getMainTopic().equals(MainTopics.AI.getMainTopic())) {
                articleService.JsonObjectToList(jsonObject, initializeLists.ai);
            } else if (like.getMainTopic().equals(MainTopics.CULTURE.getMainTopic())) {
                articleService.JsonObjectToList(jsonObject, initializeLists.culture);
            } else if (like.getMainTopic().equals(MainTopics.DEVOPS.getMainTopic())) {
                articleService.JsonObjectToList(jsonObject, initializeLists.devops);
            } else {
                articleService.JsonObjectToList(jsonObject, new ArrayList<Article>());
            }
        }
    }

    public static void recommendedArticlesToList(ArticleService articleService, DataService dataService, MongoDatabase database) {
        initializeLists.development = articleService.returnRecommendations(initializeLists.development);
        initializeLists.architecture = articleService.returnRecommendations(initializeLists.architecture);
        initializeLists.ai = articleService.returnRecommendations(initializeLists.ai);
        initializeLists.culture = articleService.returnRecommendations(initializeLists.culture);
        initializeLists.devops = articleService.returnRecommendations(initializeLists.devops);


        dataService.sendRecommendations(initializeLists.development, initializeLists.recommendedArticles, database);
        dataService.sendRecommendations(initializeLists.architecture, initializeLists.recommendedArticles, database);
        dataService.sendRecommendations(initializeLists.ai, initializeLists.recommendedArticles, database);
        dataService.sendRecommendations(initializeLists.culture, initializeLists.recommendedArticles, database);
        dataService.sendRecommendations(initializeLists.devops, initializeLists.recommendedArticles, database);


//        Mail mail = new Mail();
//        mail.sendMail(initializeLists.recommendedArticles.toString());

        // TODO : recommendedArticles.toString() -> mail formatina donusturecek bir fonksiyon yaz.
    }
}