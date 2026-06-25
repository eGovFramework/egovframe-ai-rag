package com.example.chat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import redis.clients.jedis.JedisPooled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@code rag.retrieval.hybrid.enabled} 토글에 따른 빈 등록 동작을 검증한다.
 *
 * <p>off(기본) 상태에서는 {@link EgovHybridRedisConfig} 의 빈(JedisPooled·하이브리드
 * retriever)이 등록되지 않아, dense {@link VectorStoreDocumentRetriever} 만 존재한다.
 * on 상태에서는 하이브리드 빈이 추가로 등록된다. JedisPooled 빈은 실제 접속을
 * 열지 않는다(명령 실행 전까지 lazy).</p>
 */
class EgovHybridRedisConfigToggleTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestBeans.class, EgovHybridRedisConfig.class)
            .withPropertyValues(
                    "spring.data.redis.host=localhost",
                    "spring.data.redis.port=6379",
                    "spring.ai.vectorstore.redis.index-name=document-index");

    @Test
    @DisplayName("토글 off(기본): 하이브리드 빈 미등록")
    void hybridBeansAbsentWhenDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(EgovHybridDocumentRetriever.class);
            assertThat(context).doesNotHaveBean(JedisPooled.class);
            assertThat(context).hasSingleBean(VectorStoreDocumentRetriever.class);
        });
    }

    @Test
    @DisplayName("토글 on: 하이브리드 retriever·JedisPooled 빈 등록")
    void hybridBeansPresentWhenEnabled() {
        runner.withPropertyValues("rag.retrieval.hybrid.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(EgovHybridDocumentRetriever.class);
            assertThat(context).hasSingleBean(JedisPooled.class);
            assertThat(context).hasSingleBean(VectorStoreDocumentRetriever.class);
        });
    }

    @Configuration
    static class TestBeans {
        @Bean
        VectorStoreDocumentRetriever vectorStoreDocumentRetriever() {
            return mock(VectorStoreDocumentRetriever.class);
        }
    }
}
