package com.example.chat.config;

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
 * 하이브리드 lexical 검색을 <b>실제 PostgreSQL + pg_trgm</b>에 대해 실행하는 통합 테스트.
 *
 * <p>mock 문자열 단정을 넘어, 프로덕션 SQL({@code text %> ?} / {@code word_similarity(?, text)})이
 * 실 엔진에서 의도대로 동작함을 증명한다. 특히 (1) 대칭 {@code similarity}({@code %})는 큰 청크에서
 * 붕괴해 무력하고 {@code word_similarity}만 관련 문서를 회수한다는 점, (2) {@code %>}의 인자 방향이
 * 뒤바뀌지 않았다는 점, (3) 임계값 {@code set_config(is_local=true)}가 트랜잭션 스코프라 커넥션에
 * 누수되지 않는다는 점을 검증한다.</p>
 *
 * <p>Docker 미가용 환경에서는 {@code @Testcontainers(disabledWithoutDocker = true)}로 자동 skip된다.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class EgovHybridLexicalSearchDbTest {

    private static final String TABLE = "document_embeddings";

    // 앱 실제 청크 크기(~수천 자)를 모사한 긴 한국어 문서 청크. 짧은 질의 대비 similarity가 붕괴한다.
    private static final String REDIS_CHUNK =
            "# egovframe-rte-psl-reactive-redis 스프링 데이터 레디스와 Lettuce를 이용한 리액티브 레디스 접근 모듈입니다. "
            + "논블로킹 방식으로 캐시를 조회하고 저장하며 ReactiveRedisTemplate 을 통해 키 값 연산과 만료 시간을 설정합니다. "
            + "세션 저장소나 조회 결과 캐싱에 활용하고 백프레셔를 지원하는 리액티브 스트림으로 대량 데이터를 처리합니다. ".repeat(6);
    private static final String CRYPTO_CHUNK =
            "# egovframe-rte-fdl-crypto 대칭키 암복호화 모듈로 ARIA 와 PBE 알고리즘을 제공합니다. "
            + "비밀번호는 단방향 해시 다이제스트로 저장하고 설정 파일의 민감한 값은 환경 암호화 서비스로 보호합니다. ".repeat(8);
    private static final String BOILERPLATE_CHUNK =
            "빌드 방법 mvn clean package 테스트 실행 mvn test 라이선스 Apache License 2.0 문의는 이슈 트래커를 이용하세요. ".repeat(4);

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
        insert("redis", REDIS_CHUNK);
        insert("crypto", CRYPTO_CHUNK);
        insert("boiler", BOILERPLATE_CHUNK);
    }

    static void insert(String id, String text) {
        jdbc.update("INSERT INTO " + TABLE + "(text, metadata) VALUES (?, ?::jsonb)",
                text, "{\"id\": \"" + id + "\"}");
    }

    private EgovHybridContentRetriever retriever(double threshold) {
        // dense 채널은 빈 결과 → 융합 결과는 lexical 채널 그대로. 실제 lexical SQL 경로를 실행한다.
        ContentRetriever emptyDense = q -> List.of();
        return new EgovHybridContentRetriever(emptyDense, jdbc, txManager, TABLE, 1.0, 1.0, threshold, 3);
    }

    private static List<String> ids(List<Content> contents) {
        return contents.stream().map(c -> c.textSegment().metadata().getString("id")).toList();
    }

    @Test
    @DisplayName("word_similarity(%>)가 긴 청크에서도 관련 문서를 회수한다 - 인자 방향·연산자 end-to-end")
    void wordSimilarityRetrievesFromLongChunk() {
        List<Content> result = retriever(0.30).retrieve(Query.from("리액티브 레디스 캐시 사용"));
        assertThat(ids(result)).contains("redis");
        assertThat(ids(result).get(0)).isEqualTo("redis"); // 최상위로 회수
    }

    @Test
    @DisplayName("대칭 similarity(%)는 같은 긴 청크에서 붕괴한다 - 연산자 교체의 근거")
    void symmetricSimilarityCollapsesOnLongChunk() {
        // 정답 청크의 대칭 similarity는 pg_trgm 기본 임계값 0.3은커녕 0.1에도 못 미친다.
        Double sim = jdbc.queryForObject(
                "SELECT similarity(text, ?) FROM " + TABLE + " WHERE metadata->>'id' = 'redis'",
                Double.class, "리액티브 레디스 캐시 사용");
        assertThat(sim).isLessThan(0.1);
        // 반면 word_similarity(query, text) 는 실용 범위(>= 0.3)에 든다.
        Double wsim = jdbc.queryForObject(
                "SELECT word_similarity(?, text) FROM " + TABLE + " WHERE metadata->>'id' = 'redis'",
                Double.class, "리액티브 레디스 캐시 사용");
        assertThat(wsim).isGreaterThanOrEqualTo(0.3);
    }

    @Test
    @DisplayName("%> 인자 방향이 뒤바뀌지 않았다 - word_similarity(query,text) >> word_similarity(text,query)")
    void argumentDirectionIsNotReversed() {
        Double qInText = jdbc.queryForObject(
                "SELECT word_similarity(?, text) FROM " + TABLE + " WHERE metadata->>'id'='redis'",
                Double.class, "리액티브 레디스 캐시 사용");
        Double textInQ = jdbc.queryForObject(
                "SELECT word_similarity(text, ?) FROM " + TABLE + " WHERE metadata->>'id'='redis'",
                Double.class, "리액티브 레디스 캐시 사용");
        // 프로덕션이 쓰는 방향(query-in-text)이 반대 방향보다 월등히 높아야 한다.
        assertThat(qInText).isGreaterThan(textInQ * 3);
    }

    @Test
    @DisplayName("임계값이 실제 게이트로 작동한다 - 0.30에선 회수, 0.90에선 차단")
    void thresholdActuallyGates() {
        Query q = Query.from("리액티브 레디스 캐시 사용");
        assertThat(ids(retriever(0.30).retrieve(q))).contains("redis"); // 완화 → 회수
        assertThat(ids(retriever(0.90).retrieve(q))).doesNotContain("redis"); // 강화 → 차단
    }

    @Test
    @DisplayName("set_config(is_local=true)는 트랜잭션 스코프 - 커밋 후 기본값 복귀(풀 누수 없음)")
    void thresholdIsTransactionScoped() throws Exception {
        try (java.sql.Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (java.sql.Statement st = c.createStatement()) {
                st.execute("SELECT word_similarity('a','b')"); // pg_trgm GUC를 백엔드에 로드
                st.execute("SELECT set_config('pg_trgm.word_similarity_threshold','0.15', true)");
                double inTx = readThreshold(st);
                c.commit();
                double afterCommit = readThreshold(st); // 커밋 후 같은 커넥션에서 기본값 복귀
                assertThat(inTx).isEqualTo(0.15);
                assertThat(afterCommit).isEqualTo(0.6);
            }
        }
    }

    private static double readThreshold(java.sql.Statement st) throws Exception {
        try (java.sql.ResultSet rs = st.executeQuery(
                "SELECT current_setting('pg_trgm.word_similarity_threshold')::float8")) {
            rs.next();
            return rs.getDouble(1);
        }
    }
}
