package com.example.chat.config.etl.writers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;

import com.example.chat.config.EgovVectorStoreConfig;

import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;

/**
 * {@link EgovVectorStoreWriter}가 재색인 시 기존 청크를 먼저 지우는지 검증한다.
 *
 * <p>벡터 저장소와 Redis 클라이언트는 대역으로 두고 호출 순서·삭제 질의의 내용을 확인한다.
 * 실제 RediSearch 질의 해석은 {@link EgovVectorStoreWriterRedisDbTest}가 검증한다.</p>
 */
class EgovVectorStoreWriterStaleChunkTest {

    private static final String INDEX = "test-index";

    private final RedisVectorStore store = mock(RedisVectorStore.class);
    private final JedisPooled jedis = mock(JedisPooled.class);

    EgovVectorStoreWriterStaleChunkTest() {
        // 조회 결과가 비어 있으면 삭제 루프는 바로 끝난다.
        SearchResult empty = mock(SearchResult.class);
        when(empty.getDocuments()).thenReturn(List.of());
        when(store.getJedis()).thenReturn(jedis);
        when(jedis.ftSearch(eq(INDEX), any(Query.class))).thenReturn(empty);
    }

    private Document chunk(String originalId, String text) {
        Map<String, Object> metadata = new HashMap<>();
        if (originalId != null) {
            metadata.put(EgovVectorStoreConfig.ORIGINAL_ID_FIELD, originalId);
        }
        metadata.put("source", "sample.md");
        return new Document(text, metadata);
    }

    @Test
    @DisplayName("저장 전에 같은 원본 문서의 기존 청크를 먼저 지운다")
    void deletesBeforeAdding() {
        EgovVectorStoreWriter writer = new EgovVectorStoreWriter(store, INDEX);

        writer.accept(List.of(chunk("doc-a", "첫 번째 청크"), chunk("doc-a", "두 번째 청크")));

        InOrder order = inOrder(jedis, store);
        order.verify(jedis).ftSearch(eq(INDEX), any(Query.class));
        order.verify(store).add(anyList());
    }

    @Test
    @DisplayName("spring-ai의 필터 삭제는 쓰지 않는다")
    void doesNotUseSpringAiFilterDelete() {
        EgovVectorStoreWriter writer = new EgovVectorStoreWriter(store, INDEX);

        writer.accept(List.of(chunk("doc-a.md", "본문")));

        verify(store, never()).delete(any(Filter.Expression.class));
    }

    @Test
    @DisplayName("한 배치에 여러 문서가 있어도 삭제 질의는 한 번에 묶는다")
    void groupsDistinctOriginalIdsIntoOneQuery() {
        assertThat(EgovVectorStoreWriter.buildOriginalIdQuery(List.of("doc-a", "pdf-b_1", "pdf-b_2")))
                .isEqualTo("@original_id:{doc\\-a | pdf\\-b_1 | pdf\\-b_2}");
    }

    @Test
    @DisplayName("같은 파일의 다른 페이지는 각각 독립된 원본 문서 id로 다뤄진다")
    void treatsPdfPagesAsSeparateDocuments() {
        // 2페이지만 재색인되는 상황. 1페이지 id는 삭제 대상에 들어가면 안 된다.
        String query = EgovVectorStoreWriter.buildOriginalIdQuery(List.of("pdf-guide_2"));

        assertThat(query).contains("pdf\\-guide_2");
        assertThat(query).doesNotContain("pdf\\-guide_1");
    }

    @Test
    @DisplayName("원본 문서 id가 없으면 삭제를 건너뛰고 저장은 진행한다")
    void skipsDeleteWhenOriginalIdMissing() {
        EgovVectorStoreWriter writer = new EgovVectorStoreWriter(store, INDEX);

        writer.accept(List.of(chunk(null, "메타데이터 없는 청크")));

        verify(jedis, never()).ftSearch(any(String.class), any(Query.class));
        verify(store).add(anyList());
    }

    @Test
    @DisplayName("원본 문서 id가 200건을 넘으면 삭제 질의를 나눠 보낸다")
    void splitsDeleteIntoBatches() {
        EgovVectorStoreWriter writer = new EgovVectorStoreWriter(store, INDEX);

        List<Document> documents = new ArrayList<>();
        IntStream.range(0, 201).forEach(i -> documents.add(chunk("doc-" + i, "본문 " + i)));

        writer.accept(documents);

        verify(jedis, times(2)).ftSearch(eq(INDEX), any(Query.class));
    }

    @Test
    @DisplayName("TAG 질의 값의 구분자 문자를 이스케이프한다")
    void escapesTagSeparatorCharacters() {
        // RediSearch는 이스케이프하지 않은 하이픈을 구분자로 보고 질의 파싱에 실패한다.
        assertThat(EgovVectorStoreWriter.escapeTagValue("doc-abc.md")).isEqualTo("doc\\-abc\\.md");
        assertThat(EgovVectorStoreWriter.escapeTagValue("hwp-Spring-AI_시험문제_1"))
                .isEqualTo("hwp\\-Spring\\-AI_시험문제_1");
        // 한글·영숫자·밑줄은 그대로 둔다
        assertThat(EgovVectorStoreWriter.escapeTagValue("가나다_123")).isEqualTo("가나다_123");
    }

    @Test
    @DisplayName("빈 배치는 저장소를 건드리지 않는다")
    void emptyBatchTouchesNothing() {
        EgovVectorStoreWriter writer = new EgovVectorStoreWriter(store, INDEX);

        writer.accept(List.of());

        verify(jedis, never()).ftSearch(any(String.class), any(Query.class));
        verify(store, never()).add(anyList());
    }
}
