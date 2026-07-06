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
 * Issue #55 후속 lexical(pg_trgm) recall 하네스 실증 최소 PoC.
 *
 * <p>메인테이너 제약에 맞춰 (1) 테스트 내부의 고정 corpus와 QA 시드셋을 사용하고,
 * (2) 검색 청크의 {@code id} 메타데이터로 원문을 역추적하며, (3) 평균 recall@3 하한을
 * 회귀 게이트로 검증한다. Docker 미가용 환경에서는 자동 skip된다.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class EgovRetrievalRecallPoCTest {

    private static final String TABLE = "document_embeddings";
    private static final int TOP_K = 3;

    private static final String REDIS_DOCUMENT = (
            "# 리액티브 레디스 접근 모듈\n"
            + "ReactiveRedisTemplate은 논블로킹 방식으로 캐시 항목을 조회하고 저장하며 키 만료 시간을 관리합니다. "
            + "리액티브 세션 저장소는 요청 스레드를 점유하지 않고 비동기 결과를 발행합니다. "
            + "직렬화 컨텍스트는 문자열 키와 업무 객체의 변환 규칙을 분리하고 운영 환경에서 호환성을 유지합니다. "
            + "연결 팩토리는 Redis 서버 접속 정보와 커넥션 공유 정책을 적용하며 장애 시 재연결 동작을 제어합니다. "
            + "캐시 무효화는 데이터 변경 시점과 만료 정책을 함께 고려하고 모니터링 지표로 적중률을 확인합니다. "
            + "표준프레임워크 서비스 계층에서는 Mono와 Flux 반환형으로 원격 호출의 완료 신호를 전달합니다. "
            + "클러스터 환경에서는 슬롯 이동과 읽기 복제본 선택이 업무 요청에 미치는 영향을 점검합니다. "
            + "트랜잭션 명령은 동일 연결에서 실행하고 여러 키를 변경할 때 원자성 범위를 명확히 정의합니다. "
            + "상태 점검은 연결 가능 여부뿐 아니라 실제 읽기와 쓰기 명령의 지연 시간까지 측정합니다. ").repeat(8)
            + "\n\n"
            + ("## Lettuce 리액티브 스트림 운영\n"
            + "Lettuce 클라이언트는 리액티브 스트림의 백프레셔 신호에 따라 대량 데이터 처리 부하를 조절합니다. "
            + "구독자가 요청한 수량만 발행하도록 흐름을 구성하고 느린 소비자로 인한 메모리 증가를 방지합니다. "
            + "연결 풀을 사용할 때에는 최대 연결 수와 대기 시간을 지정하고 명령 지연 시간을 관찰해야 합니다. "
            + "파이프라이닝은 왕복 시간을 줄이지만 단일 요청에 과도한 명령을 누적하지 않도록 배치 크기를 제한합니다. "
            + "타임아웃과 재시도는 멱등성이 보장되는 읽기 명령을 중심으로 적용하고 오류 신호를 상위 계층에 전달합니다. "
            + "운영자는 처리량, 지연 시간, 실패율을 함께 확인하여 스트림 처리 용량과 서버 자원을 조정합니다. "
            + "명령 실행 순서가 중요한 업무는 병렬 연산을 제한하고 구독 체인에서 순차 결합 연산자를 사용합니다. "
            + "구독 취소 신호가 전달되면 남은 네트워크 작업과 버퍼를 해제하여 불필요한 자원 사용을 막습니다. "
            + "부하 시험은 생산 환경과 비슷한 키 분포와 값 크기를 사용하여 병목 지점을 사전에 확인합니다. ").repeat(8);
    private static final String PGVECTOR_DOCUMENT = (
            "# PostgreSQL 임베딩 검색 모듈\n"
            + "pgvector 확장은 문서 임베딩을 벡터 열에 저장하고 코사인 거리 기준으로 유사한 청크를 검색합니다. "
            + "질의 임베딩과 저장 벡터의 차원은 같아야 하며 모델 교체 시 전체 색인의 호환성을 점검합니다. "
            + "HNSW 인덱스는 근사 최근접 탐색의 후보 수를 조절하여 응답 시간과 재현율의 균형을 맞춥니다. "
            + "검색 결과에는 원문 식별자와 파일 출처를 메타데이터로 보존하여 답변 근거를 추적할 수 있게 합니다. "
            + "대량 적재 작업은 트랜잭션 크기를 제한하고 인덱스 생성 시점과 통계 정보 갱신 시점을 구분합니다. "
            + "운영 환경에서는 실행 계획과 거리 분포를 관찰하여 필터 조건이 벡터 검색보다 먼저 적용되는지 확인합니다. "
            + "업무 분류와 접근 권한 조건은 메타데이터 열에 저장하고 벡터 후보 집합과 함께 안전하게 제한합니다. "
            + "삭제와 갱신이 반복되는 테이블은 진공 작업과 재색인 주기를 정해 인덱스 팽창을 관리합니다. "
            + "검색 품질 시험은 고정 질의 집합으로 정확도와 지연 시간을 함께 기록하여 설정 변경을 비교합니다. ").repeat(8);
    private static final String CRYPTO_DOCUMENT = (
            "# 표준프레임워크 데이터 암호화\n"
            + "ARIA 대칭키 암복호화 서비스는 환경 설정의 민감한 값을 보호하고 허가된 애플리케이션만 복호화합니다. "
            + "PBE 방식은 비밀번호와 솔트를 이용해 키를 유도하며 반복 횟수와 알고리즘 식별자를 설정으로 관리합니다. "
            + "사용자 비밀번호는 복호화 가능한 형태가 아니라 단방향 해시 다이제스트와 개별 솔트로 저장합니다. "
            + "암호 키는 소스 저장소에 기록하지 않고 별도 비밀 관리 체계에서 주입하며 정기 교체 절차를 운영합니다. "
            + "암호문에는 버전 정보를 함께 남겨 키 변경 기간에도 이전 데이터의 복호화 경로를 선택할 수 있게 합니다. "
            + "감사 로그에는 평문과 키 값을 제외하고 수행 주체, 알고리즘, 성공 여부만 기록하여 노출을 방지합니다. "
            + "초기화 벡터와 난수 값은 안전한 생성기를 사용하고 동일 키에서 값을 재사용하지 않도록 검사합니다. "
            + "암복호화 API는 입력 길이와 문자 인코딩을 검증하고 오류 메시지에 민감 정보가 포함되지 않게 합니다. "
            + "보안 점검에서는 허용 알고리즘 목록과 키 접근 권한을 확인하고 폐기된 키의 사용을 차단합니다. ").repeat(8);
    private static final String BATCH_DOCUMENT = (
            "# 대용량 배치 실행 모듈\n"
            + "배치 작업은 청크 단위 커밋으로 읽기, 처리, 쓰기 구간을 나누고 성공한 실행 위치를 체크포인트에 보존합니다. "
            + "작업 실패 후 재시작하면 완료된 구간을 건너뛰고 마지막 저장 지점부터 남은 데이터를 다시 수행합니다. "
            + "재시도 정책은 일시적인 네트워크 오류에 제한하여 적용하고 데이터 오류는 별도 건너뛰기 정책으로 분류합니다. "
            + "JobRepository는 실행 식별자와 단계 상태를 관리하며 동일 파라미터의 중복 실행 여부를 판단합니다. "
            + "파티셔닝을 사용할 때에는 입력 범위가 겹치지 않게 분배하고 각 워커의 처리 결과를 중앙에서 집계합니다. "
            + "운영자는 처리 건수, 커밋 횟수, 롤백 원인을 확인하여 청크 크기와 동시 실행 수를 조정합니다. "
            + "단계 리스너는 실행 전후 통계를 남기되 업무 데이터 변경은 처리기와 기록기에서 일관되게 수행합니다. "
            + "재실행 가능한 작업은 외부 시스템 호출에도 업무 키를 전달하여 중복 반영을 방지합니다. "
            + "스케줄러는 이전 실행의 종료 상태를 확인하고 장시간 작업이 겹치지 않도록 동시 실행을 제한합니다. ").repeat(8);
    private static final String CACHE_DISTRACTOR_DOCUMENT = (
            "# 로컬 파일 캐싱과 객체 저장소\n"
            + "애플리케이션의 캐시 저장소는 원격 객체 파일을 로컬 디스크에 보관하여 반복 다운로드 비용을 줄입니다. "
            + "비동기 조회 작업은 파일 경로와 수정 시각을 검사한 뒤 작업 큐에서 원본 스토리지 접근을 수행합니다. "
            + "용량 제한을 넘으면 최근 사용 빈도가 낮은 항목을 제거하고 메타데이터 인덱스와 실제 파일을 함께 정리합니다. "
            + "공유 디렉터리는 프로세스 간 잠금을 적용하여 동시에 같은 객체를 내려받는 중복 작업을 방지합니다. "
            + "무결성 검사는 체크섬과 콘텐츠 길이를 비교하며 손상된 항목은 폐기한 후 원격 저장소에서 다시 가져옵니다. "
            + "이 모듈은 데이터베이스나 메시지 브로커가 아니라 대용량 정적 파일의 임시 보관 계층을 제공합니다. "
            + "제거 정책은 파일 크기와 마지막 접근 시각을 함께 계산하여 일부 대형 객체가 공간을 독점하지 않게 합니다. "
            + "파일 권한은 애플리케이션 계정으로 제한하고 심볼릭 링크를 통한 허용 경로 이탈을 검사합니다. "
            + "임시 파일은 다운로드 완료 후 원자적으로 이름을 변경하고 비정상 종료 시 남은 항목을 주기적으로 정리합니다. ").repeat(8);

    private static final List<SeedQuestion> QA_SEEDS = List.of(
            new SeedQuestion("논블로킹 캐시 저장소에서 비동기 조회를 구성하는 방법", "doc-reactive-redis.md"),
            new SeedQuestion("Lettuce 연결의 대량 스트림 부하 제어 방식", "doc-reactive-redis.md"),
            new SeedQuestion("임베딩 문서를 코사인 기준으로 유사 검색하는 인덱스 구성", "doc-pgvector.md"),
            new SeedQuestion("환경의 비밀 설정값을 ARIA 대칭키로 보호하는 절차", "doc-crypto.md"),
            new SeedQuestion("실패 작업을 체크포인트부터 다시 수행하는 청크 커밋 전략", "doc-batch.md"));

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
        insert("doc-cache-storage.md", "cache-storage.md", CACHE_DISTRACTOR_DOCUMENT);
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
            assertThat(retrievedDocIds).hasSizeLessThanOrEqualTo(TOP_K);
            double recall = recallAtK(retrievedDocIds, List.of(seed.goldDocId()), TOP_K);
            recallSum += recall;
            System.out.println("[recall@3] 질의='" + seed.question() + "' gold=" + seed.goldDocId()
                    + " retrieved=" + retrievedDocIds + " recall=" + recall);
        }

        double averageRecall = recallSum / QA_SEEDS.size();
        System.out.println("[평균 recall@3] " + averageRecall);
        assertThat(averageRecall).isGreaterThanOrEqualTo(0.6);
    }

    @Test
    @DisplayName("recall@3은 4순위의 정답을 hit로 계산하지 않는다")
    void excludesRelevantDocumentAfterCutoff() {
        assertThat(recallAtK(List.of("N1", "N2", "N3", "GOLD"), List.of("GOLD"), 3)).isZero();
    }

    private static double recallAtK(List<String> retrieved, List<String> relevant, int k) {
        List<String> topK = retrieved.stream().limit(k).toList();
        long hit = relevant.stream().filter(topK::contains).count();
        return (double) hit / relevant.size();
    }

    private record SeedQuestion(String question, String goldDocId) {
    }
}
