package com.example.chat.config;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link EgovHybridContentRetriever} 의 dense + lexical 융합 동작을 검증한다.
 *
 * <p>fake dense 채널({@link ContentRetriever})과 lexical 채널({@link JdbcTemplate})을
 * 주입해 외부 DB 없이 융합 결과를 결정적으로 단정한다.</p>
 */
class EgovHybridContentRetrieverTest {

    private static final String TABLE = "document_embeddings";

    private Content content(String id, String text) {
        Metadata metadata = new Metadata();
        if (id != null) {
            metadata.put("id", id);
        }
        return Content.from(TextSegment.from(text, metadata));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Content> stubLexical(JdbcTemplate jdbc, List<Content> lexicalResults) {
        // query(String sql, RowMapper, Object... args) - 가변인자 3개(queryText, queryText, topK)
        when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyString(), anyInt()))
                .thenReturn((List) lexicalResults);
        return lexicalResults;
    }

    @Test
    @DisplayName("양 채널에 공통으로 나온 문서가 융합 상위로 올라온다")
    void commonDocumentRanksHigh() {
        ContentRetriever dense = mock(ContentRetriever.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        // dense: [D1, COMMON, D2]
        when(dense.retrieve(any(Query.class))).thenReturn(List.of(
                content("D1", "dense only one"),
                content("COMMON", "shared document"),
                content("D2", "dense only two")));

        // lexical: [COMMON, L1]
        stubLexical(jdbc, List.of(
                content("COMMON", "shared document"),
                content("L1", "lexical only one")));

        EgovHybridContentRetriever retriever =
                new EgovHybridContentRetriever(dense, jdbc, TABLE, 1.0, 1.0, 3);

        List<Content> result = retriever.retrieve(Query.from("shared"));

        // COMMON 은 dense r1(1/61) + lexical r0(1/60) 으로 최고점
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).textSegment().metadata().getString("id")).isEqualTo("COMMON");
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("lexical 검색 실패 시 dense 결과만 반환해 RAG 를 보존한다")
    void degradesToDenseOnLexicalFailure() {
        ContentRetriever dense = mock(ContentRetriever.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        List<Content> denseResult = List.of(
                content("D1", "alpha"),
                content("D2", "beta"));
        when(dense.retrieve(any(Query.class))).thenReturn(denseResult);

        when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("relation does not exist"));

        EgovHybridContentRetriever retriever =
                new EgovHybridContentRetriever(dense, jdbc, TABLE, 1.0, 1.0, 3);

        List<Content> result = retriever.retrieve(Query.from("alpha"));

        assertThat(result).isEqualTo(denseResult);
    }

    @Test
    @DisplayName("lexical 가중치를 높이면 lexical 단독 문서가 dense 단독 문서보다 우선된다")
    void lexicalWeightChangesOrder() {
        ContentRetriever dense = mock(ContentRetriever.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        when(dense.retrieve(any(Query.class))).thenReturn(List.of(
                content("DENSE_TOP", "dense top")));
        stubLexical(jdbc, List.of(
                content("LEX_TOP", "lexical top")));

        // lexical 가중 3배 -> LEX_TOP(3/60) > DENSE_TOP(1/60)
        EgovHybridContentRetriever retriever =
                new EgovHybridContentRetriever(dense, jdbc, TABLE, 1.0, 3.0, 3);

        List<Content> result = retriever.retrieve(Query.from("top"));

        assertThat(result.get(0).textSegment().metadata().getString("id")).isEqualTo("LEX_TOP");
    }

    @Test
    @DisplayName("id 가 없으면 텍스트 해시로 중복을 제거해 융합한다")
    void deduplicatesByTextHashWhenNoId() {
        ContentRetriever dense = mock(ContentRetriever.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        // 동일 텍스트, id 없음 -> 동일 융합 키
        when(dense.retrieve(any(Query.class))).thenReturn(List.of(
                content(null, "same text body")));
        stubLexical(jdbc, List.of(
                content(null, "same text body")));

        EgovHybridContentRetriever retriever =
                new EgovHybridContentRetriever(dense, jdbc, TABLE, 1.0, 1.0, 3);

        List<Content> result = retriever.retrieve(Query.from("same"));

        // 동일 키로 합쳐져 단일 결과
        assertThat(result).hasSize(1);
        assertThat(result.get(0).textSegment().text()).isEqualTo("same text body");
    }
}
