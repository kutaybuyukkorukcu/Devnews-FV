package service;

import domain.Article;
import domain.User;
import org.assertj.core.api.AtomicReferenceArrayAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import repository.ArticleRepository;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(MockitoJUnitRunner.class)
public class ArticleServiceTest {

    @Mock
    ArticleRepository articleRepository;

    @InjectMocks
    ArticleService articleService;

    @Test
    public void test_addArticle_whenNoArticlesArePresent() {


        doThrow(new NullPointerException())
                .doNothing()
                .when(articleService).addArticle(null);

        verify(articleRepository).add(null);
        verifyNoMoreInteractions(articleService);
    }

    @Test
    public void test_addArticle_whenOneArticleIsPresent() {

        Article article = new Article(1,"Whats new with Java 11", "Development", "Author",
                "Development|Java", "www.infoq.com/Whats-new-with-Java-11", true);

        articleService.addArticle(article);

        verify(articleRepository).add(article);
        verifyNoMoreInteractions(articleService);
    }

    @Test
    public void test_getArticles_whenFindAllIsNotPresent() {

        when(articleRepository.findAll()).thenReturn(null);

        List<Article> articleList = articleService.getArticles();
        List<Article> expectedArticleList = new ArrayList<>();

        assertThat(articleList).isEqualTo(expectedArticleList);

        verify(articleRepository).findAll();
        verifyNoMoreInteractions(articleService);
    }

    @Test
    public void test_getArticles_whenFindAllIsPresent() {
        List<Article> articleList = new ArrayList<>();

        when(articleRepository.findAll()).thenReturn(articleList);

        List<Article> expectedArticleList = articleService.getArticles();

        verify(articleRepository).findAll();

        assertThat(articleList).isEqualTo(expectedArticleList);
        verifyNoMoreInteractions(articleService);
    }
}
