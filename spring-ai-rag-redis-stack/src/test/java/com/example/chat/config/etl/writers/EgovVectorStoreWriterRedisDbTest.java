package com.example.chat.config.etl.writers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.example.chat.config.EgovVectorStoreConfig;

import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.Query;

/**
 * 실제 redis-stack에 붙여 재색인 시 기존 청크가 지워지는지 검증한다.
 *
 * <p>RediSearch TAG 필터는 질의문을 그대로 서버가 파싱하므로, 필터 문자열이 잘못돼도 대역
 * 저장소로는 드러나지 않는다. 문서 id에 항상 들어가는 하이픈이 이스케이프되지 않으면 서버가
 * 질의 파싱에 실패하고 색인 전체가 중단된다. 이 조건을 실제 엔진으로 고정한다.</p>
 */
@Testcontainers
class EgovVectorStoreWriterRedisDbTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis/redis-stack-server:latest"))
            .withExposedPorts(6379);

    /** 외부 모델 없이 결정적으로 동작하는 임베딩 대역. */
    private static class DeterministicEmbeddingModel implements EmbeddingModel {

        @Override
        public float[] embed(Document document) {
            return embed(document.getText());
        }

        @Override
        public float[] embed(String text) {
            float[] vector = new float[4];
            vector[0] = text.length() % 7;
            vector[1] = 1.0f;
            return vector;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                embeddings.add(new Embedding(embed(request.getInstructions().get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        }
    }

    private RedisVectorStore vectorStore(String indexName) {
        RedisVectorStore store = RedisVectorStore
                .builder(new JedisPooled(REDIS.getHost(), REDIS.getMappedPort(6379)),
                        new DeterministicEmbeddingModel())
                .indexName(indexName)
                .prefix(indexName + ":")
                .initializeSchema(true)
                .metadataFields(RedisVectorStore.MetadataField.tag(EgovVectorStoreConfig.ORIGINAL_ID_FIELD))
                .build();
        store.afterPropertiesSet();
        return store;
    }

    private Document chunk(String originalId, String text) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(EgovVectorStoreConfig.ORIGINAL_ID_FIELD, originalId);
        return new Document(text, metadata);
    }

    private List<Document> storedDocuments(RedisVectorStore store) {
        return store.similaritySearch(SearchRequest.builder()
                .query("본문")
                .topK(100)
                .similarityThresholdAll()
                .build());
    }

    @Test
    @DisplayName("하이픈과 마침표가 든 문서 id로 재색인하면 이전 청크만 지워진다")
    void reindexRemovesOnlyStaleChunks() {
        RedisVectorStore store = vectorStore("reindex-index");
        EgovVectorStoreWriter writer = new EgovVectorStoreWriter(store, "reindex-index");
        String originalId = "doc-Term-level-Queries의-종류.md";

        writer.accept(List.of(chunk(originalId, "v1 첫 번째 본문"), chunk(originalId, "v1 두 번째 본문")));
        assertThat(storedDocuments(store)).hasSize(2);

        writer.accept(List.of(chunk(originalId, "v2 새 본문")));

        List<Document> remaining = storedDocuments(store);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getText()).isEqualTo("v2 새 본문");
    }

    @Test
    @DisplayName("같은 파일의 다른 페이지는 재색인 대상에서 살아남는다")
    void reindexKeepsOtherPagesOfTheSameFile() {
        RedisVectorStore store = vectorStore("page-index");
        EgovVectorStoreWriter writer = new EgovVectorStoreWriter(store, "page-index");

        writer.accept(List.of(chunk("pdf-개발가이드_1", "1페이지 본문"), chunk("pdf-개발가이드_2", "2페이지 본문")));
        assertThat(storedDocuments(store)).hasSize(2);

        writer.accept(List.of(chunk("pdf-개발가이드_2", "2페이지 새 본문")));

        List<String> texts = storedDocuments(store).stream().map(Document::getText).toList();
        assertThat(texts).containsExactlyInAnyOrder("1페이지 본문", "2페이지 새 본문");
    }

    @Test
    @DisplayName("기존 청크가 한 번의 조회 한도(1000건)를 넘어도 모두 지워진다")
    void reindexRemovesChunksBeyondOnePage() {
        RedisVectorStore store = vectorStore("bulk-index");
        EgovVectorStoreWriter writer = new EgovVectorStoreWriter(store, "bulk-index");
        String originalId = "doc-대용량.md";

        List<Document> chunks = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            chunks.add(chunk(originalId, "v1 본문 " + i));
        }
        writer.accept(chunks);
        assertThat(indexedCount(store, "bulk-index")).isEqualTo(1001);

        writer.accept(List.of(chunk(originalId, "v2 본문")));

        assertThat(indexedCount(store, "bulk-index")).isEqualTo(1);
    }

    private long indexedCount(RedisVectorStore store, String indexName) {
        return store.getJedis().ftSearch(indexName, new Query("*").setNoContent().limit(0, 0)).getTotalResults();
    }
}
