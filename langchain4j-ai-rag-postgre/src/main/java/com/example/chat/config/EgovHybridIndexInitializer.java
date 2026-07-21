package com.example.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 하이브리드 lexical 검색용 pg_trgm 인덱스 초기화
 *
 * <p>{@code rag.retrieval.hybrid.enabled=true} 일 때만 활성화된다. 애플리케이션
 * 기동 완료 시점에 {@code pg_trgm} 확장과 GIN 인덱스를 멱등(IF NOT EXISTS)으로
 * 생성한다. 권한 부족 등으로 실패하더라도 경고만 남기고 기동을 막지 않는다
 * (graceful degrade). 인덱스가 없으면 lexical 검색은 느려질 뿐 동작 자체는
 * 유지된다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rag.retrieval.hybrid", name = "enabled", havingValue = "true")
public class EgovHybridIndexInitializer {

    private static final String TEXT_COLUMN = "text";

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;

    public EgovHybridIndexInitializer(JdbcTemplate jdbcTemplate,
                                      @Value("${pgvector.table-name:document_embeddings}") String tableName) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
    }

    /**
     * pg_trgm 확장 및 GIN trigram 인덱스를 멱등 생성한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeTrigramIndex() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");

            String indexName = "idx_doc_emb_trgm";
            String createIndex = "CREATE INDEX IF NOT EXISTS " + indexName
                    + " ON " + tableName + " USING gin (" + TEXT_COLUMN + " gin_trgm_ops)";
            jdbcTemplate.execute(createIndex);

            log.info("하이브리드 lexical 인덱스 준비 완료 - table: {}, index: {}", tableName, indexName);
        } catch (Exception e) {
            // 권한 부족·미지원 DB 등으로 실패해도 기동을 막지 않는다.
            // 확장 설치 실패 시에는 lexical 검색이 비활성화되고(% 연산자 미존재로 SQL 예외 →
            // dense 폴백), 인덱스만 실패한 경우에는 순차 스캔으로 느려질 수 있다.
            log.warn("pg_trgm 인덱스 초기화 실패 - lexical 검색이 비활성화되거나 느려질 수 있습니다. 원인: {}", e.getMessage());
        }
    }
}
