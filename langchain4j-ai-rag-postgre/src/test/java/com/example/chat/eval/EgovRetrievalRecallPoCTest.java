package com.example.chat.eval;

import com.example.chat.config.EgovHybridContentRetriever;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #55 후속 retrieval recall@k 최소 PoC.
 *
 * <p>메인테이너 제약에 맞춰 (1) 테스트 내부의 고정 corpus와 QA 시드셋을 사용하고,
 * (2) 검색 청크의 {@code id} 메타데이터로 원문을 역추적하며, (3) 평균 recall@3 하한을
 * 회귀 게이트로 검증한다. Docker 미가용 환경에서는 자동 skip된다.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class EgovRetrievalRecallPoCTest {

    private static final String TABLE = "document_embeddings";
    private static final int TOP_K = 3;

    private static final String REDIS_DOCUMENT =
            "# 리액티브 레디스\n리액티브 레디스 캐시 조회는 ReactiveRedisTemplate으로 키 값과 만료 시간을 관리합니다. "
            + "논블로킹 세션 저장소는 요청 스레드를 점유하지 않습니다.\n\n"
            + "Lettuce 백프레셔 대량 데이터 처리는 리액티브 스트림으로 부하를 조절합니다. "
            + "연결 팩토리와 직렬화 설정을 표준 구성으로 제공합니다.";
    private static final String PGVECTOR_DOCUMENT =
            "# PostgreSQL 벡터 검색\npgvector 코사인 거리 벡터 검색은 임베딩 유사도 순서로 문서 청크를 조회합니다. "
            + "HNSW 인덱스와 차원 설정을 적용해 검색 성능을 관리합니다.";
    private static final String CRYPTO_DOCUMENT =
            "# 데이터 암호화\nARIA PBE 민감 설정 암호화는 대칭키 암복호화 서비스로 환경 설정 값을 보호합니다. "
            + "비밀번호는 단방향 해시 다이제스트로 저장합니다.";
    private static final String BATCH_DOCUMENT =
            "# 배치 처리\n배치 재시작 체크포인트 처리는 실패한 작업의 실행 위치를 보존합니다. "
            + "청크 단위 커밋과 재시도 정책으로 대량 업무를 안정적으로 수행합니다.";

    private static final List<SeedQuestion> QA_SEEDS = List.of(
            new SeedQuestion("리액티브 레디스 캐시 조회", "doc-reactive-redis.md"),
            new SeedQuestion("Lettuce 백프레셔 대량 데이터 처리", "doc-reactive-redis.md"),
            new SeedQuestion("pgvector 코사인 거리 벡터 검색", "doc-pgvector.md"),
            new SeedQuestion("ARIA PBE 민감 설정 암호화", "doc-crypto.md"),
            new SeedQuestion("배치 재시작 체크포인트 처리", "doc-batch.md"));

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static DataSource dataSource;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager txManager;

    @BeforeAll
    static void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        dataSource = ds;
        jdbc = new JdbcTemplate(ds);
        txManager = new DataSourceTransactionManager(ds);

        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        jdbc.execute("CREATE TABLE " + TABLE + " (embedding_id serial primary key, text text, metadata jsonb)");
        jdbc.execute("CREATE INDEX idx_doc_emb_trgm ON " + TABLE + " USING gin (text gin_trgm_ops)");

        for (String chunk : REDIS_DOCUMENT.split("\\n\\n")) {
            insert("doc-reactive-redis.md", "reactive-redis.md", chunk);
        }
        insert("doc-pgvector.md", "pgvector.md", PGVECTOR_DOCUMENT);
        insert("doc-crypto.md", "crypto.md", CRYPTO_DOCUMENT);
        insert("doc-batch.md", "batch.md", BATCH_DOCUMENT);
    }

    static void insert(String id, String fileName, String text) {
        String metadata = "{\"id\": \"" + id + "\", \"source\": \"" + fileName
                + "\", \"file_name\": \"" + fileName + "\"}";
        jdbc.update("INSERT INTO " + TABLE + "(text, metadata) VALUES (?, ?::jsonb)", text, metadata);
    }

    private EgovHybridContentRetriever retriever(double threshold) {
        ContentRetriever emptyDense = q -> List.of();
        return new EgovHybridContentRetriever(
                emptyDense, jdbc, txManager, TABLE, 1.0, 1.0, threshold, TOP_K);
    }

    @Test
    @DisplayName("고정 corpus와 QA 시드셋의 평균 retrieval recall@3을 평가한다")
    void evaluatesRecallAtThree() {
        EgovHybridContentRetriever retriever = retriever(0.30);
        double recallSum = 0.0;

        for (SeedQuestion seed : QA_SEEDS) {
            List<String> retrievedDocIds = retriever.retrieve(Query.from(seed.question())).stream()
                    .map(Content::textSegment)
                    .map(segment -> segment.metadata().getString("id"))
                    .distinct()
                    .toList();
            double recall = recallAtK(retrievedDocIds, List.of(seed.goldDocId()));
            recallSum += recall;
            System.out.println("[recall@3] 질의='" + seed.question() + "' gold=" + seed.goldDocId()
                    + " retrieved=" + retrievedDocIds + " recall=" + recall);
        }

        double averageRecall = recallSum / QA_SEEDS.size();
        System.out.println("[평균 recall@3] " + averageRecall);
        assertThat(averageRecall).isGreaterThanOrEqualTo(0.5);
    }

    private static double recallAtK(List<String> retrieved, List<String> relevant) {
        long hit = relevant.stream().filter(retrieved::contains).count();
        return (double) hit / relevant.size();
    }

    private record SeedQuestion(String question, String goldDocId) {
    }
}
