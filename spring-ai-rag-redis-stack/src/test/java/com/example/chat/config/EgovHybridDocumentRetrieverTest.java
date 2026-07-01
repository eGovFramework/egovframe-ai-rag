package com.example.chat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EgovHybridDocumentRetriever} 의 dense + lexical 융합 동작을 검증한다.
 *
 * <p>fake dense 채널({@link DocumentRetriever})과 fake lexical 채널
 * ({@link EgovHybridDocumentRetriever.LexicalSearch})을 주입해 Redis 없이 융합
 * 결과를 결정적으로 단정한다. lexical 채널은 dense 문서와 동일한 id 를 반환하므로
 * 융합 키가 채널 간 일치한다.</p>
 */
class EgovHybridDocumentRetrieverTest {

    private static final String INDEX = "document-index";

    private Document doc(String id, String text) {
        return Document.builder().id(id).text(text).build();
    }

    /** 항상 같은 결과를 반환하는 dense 채널. */
    private DocumentRetriever denseOf(Document... docs) {
        return query -> List.of(docs);
    }

    @Test
    @DisplayName("dense·lexical 이 같은 id 문서를 반환하면 융합 키가 일치해 RRF 보강이 일어난다")
    void crossChannelReinforcementWhenSameId() {
        // SAME 은 양 채널에서 중위권(r1)이지만, 같은 id 로 양쪽에 등장하므로 순위 역수가
        // 합산되어 단일 채널 상위 문서보다 위로 올라와야 한다.
        DocumentRetriever dense = denseOf(
                doc("DTOP", "dense top"),
                doc("SAME", "shared body"));
        // lexical 은 id 순위만 반환한다(상위가 앞).
        EgovHybridDocumentRetriever.LexicalSearch lexical =
                (q, k) -> List.of("LTOP", "SAME");

        EgovHybridDocumentRetriever retriever =
                new EgovHybridDocumentRetriever(dense, lexical, INDEX, 1.0, 1.0, 3);

        List<Document> result = retriever.retrieve(new Query("shared"));

        // SAME: dense r1(1/61) + lexical r1(1/61) = 2/61 > DTOP(1/60)
        assertThat(result.get(0).getId()).isEqualTo("SAME");
        // lexical 단독 키(LTOP)는 dense 본문에 없으므로 결과에서 제외, dense 2건만 재정렬된다.
        assertThat(result).extracting(Document::getId).containsExactly("SAME", "DTOP");
    }

    @Test
    @DisplayName("lexical 가중치를 높이면 lexical 상위 문서가 우선된다")
    void lexicalWeightChangesOrder() {
        // 두 문서 모두 dense 에 존재(본문 보유). lexical 은 LEXHIT 을 상위로 보강한다.
        DocumentRetriever dense = denseOf(
                doc("DENSE_TOP", "dense top"),
                doc("LEXHIT", "lexical hit body"));
        EgovHybridDocumentRetriever.LexicalSearch lexical =
                (q, k) -> List.of("LEXHIT");

        // lexical 가중 3배 -> LEXHIT(dense 1/61 + lexical 3/60) > DENSE_TOP(1/60)
        EgovHybridDocumentRetriever retriever =
                new EgovHybridDocumentRetriever(dense, lexical, INDEX, 1.0, 3.0, 3);

        List<Document> result = retriever.retrieve(new Query("top"));

        assertThat(result.get(0).getId()).isEqualTo("LEXHIT");
    }

    @Test
    @DisplayName("lexical 검색 실패 시 dense 결과만 반환해 RAG 를 보존한다")
    void degradesToDenseOnLexicalFailure() {
        List<Document> denseResult = List.of(doc("D1", "alpha"), doc("D2", "beta"));
        DocumentRetriever dense = query -> denseResult;
        EgovHybridDocumentRetriever.LexicalSearch failing = (q, k) -> {
            throw new RuntimeException("FT.SEARCH unavailable");
        };

        EgovHybridDocumentRetriever retriever =
                new EgovHybridDocumentRetriever(dense, failing, INDEX, 1.0, 1.0, 3);

        List<Document> result = retriever.retrieve(new Query("alpha"));

        assertThat(result).isEqualTo(denseResult);
    }

    @Test
    @DisplayName("lexical 결과가 비어도 dense 순서가 유지된다")
    void emptyLexicalKeepsDenseOrder() {
        DocumentRetriever dense = denseOf(doc("A", "a"), doc("B", "b"), doc("C", "c"));
        EgovHybridDocumentRetriever.LexicalSearch lexical = (q, k) -> List.of();

        EgovHybridDocumentRetriever retriever =
                new EgovHybridDocumentRetriever(dense, lexical, INDEX, 1.0, 1.0, 3);

        List<Document> result = retriever.retrieve(new Query("x"));

        assertThat(result).extracting(Document::getId).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("FT.SEARCH 반환 키 embedding:<id> 의 prefix 를 벗겨 dense id 와 일치시킨다")
    void stripsKeyPrefixToAlignWithDenseId() {
        // RedisVectorStore 는 문서를 'embedding:<id>' 키로 색인한다. stripPrefix 가
        // 이를 순수 id 로 정규화해 dense Document.getId() 와 융합 키가 일치한다.
        assertThat(EgovHybridDocumentRetriever.stripPrefix("embedding:abc-123"))
                .isEqualTo("abc-123");
        // prefix 가 없으면 원본 유지
        assertThat(EgovHybridDocumentRetriever.stripPrefix("abc-123"))
                .isEqualTo("abc-123");
    }
}
