package com.example.chat.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@code rag.retrieval.hybrid.enabled} 토글에 따른 빈 등록 동작을 검증한다.
 *
 * <p>off(기본) 상태에서는 하이브리드 빈이 등록되지 않아 {@link ContentRetriever}
 * 타입 빈이 dense 하나뿐이므로 빈 모호성(NoUniqueBeanDefinitionException)이
 * 발생하지 않는다. on 상태에서는 dense·hybrid 두 빈이 공존한다.</p>
 */
class EgovHybridRetrieverToggleTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestBeans.class, EgovRagConfig.class);

    @Test
    @DisplayName("토글 off(기본): 하이브리드 빈 미등록, ContentRetriever 는 dense 단일")
    void hybridBeanAbsentWhenDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("hybridContentRetriever");
            assertThat(context).hasBean("contentRetriever");
            assertThat(context.getBeanNamesForType(ContentRetriever.class)).hasSize(1);
        });
    }

    @Test
    @DisplayName("토글 on: dense·hybrid 빈 공존, Qualifier 로 모호성 없이 구분")
    void hybridBeanPresentWhenEnabled() {
        runner.withPropertyValues("rag.retrieval.hybrid.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("contentRetriever");
            assertThat(context).hasBean("hybridContentRetriever");
            assertThat(context.getBeanNamesForType(ContentRetriever.class)).hasSize(2);
            assertThat(context.getBean("hybridContentRetriever"))
                    .isInstanceOf(EgovHybridContentRetriever.class);
        });
    }

    @Configuration
    static class TestBeans {
        @Bean
        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore() {
            return mock(EmbeddingStore.class);
        }

        @Bean
        EmbeddingModel embeddingModel() {
            return mock(EmbeddingModel.class);
        }

        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }
    }
}
