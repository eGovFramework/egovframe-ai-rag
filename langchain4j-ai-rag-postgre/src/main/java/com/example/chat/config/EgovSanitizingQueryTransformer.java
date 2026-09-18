package com.example.chat.config;

import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 질의 재작성 결과를 검증·정리하는 위임형 {@link QueryTransformer}.
 *
 * <p>재작성에 쓰는 모델을 고정하지 않기 때문에, 프롬프트로 금지해도 모델이 규칙을 어길 수 있다.
 * 이 클래스는 위임한 트랜스포머의 결과에 두 가지 안전장치를 적용한다.</p>
 *
 * <ol>
 *   <li><b>{@code <think>} 블록 제거</b> — reasoning 모드를 지원하는 모델이 사고 과정을 함께
 *       출력하면 태그가 포함된 문자열이 그대로 검색어가 된다. 블록을 제거한 뒤 사용한다.</li>
 *   <li><b>재작성 결과 검증</b> — 모델이 재작성 질의 대신 답변을 반환하면(코드 블록 포함,
 *       200자 초과 등) 검색 품질이 오히려 떨어진다. 이 경우 원본 질의로 복원한다.</li>
 * </ol>
 *
 * <p>판정 규칙은 spring-ai 모듈의 {@code EgovCompressionQueryTransformer}와 동일하게 맞췄다.
 * 다만 여러 줄 응답도 걸러지도록 "예시는/예시:" 패턴 매칭에 {@code (?s)} 플래그를 적용했다.</p>
 *
 * <p>어느 안전장치도 걸리지 않으면 위임 결과를 그대로 통과시키므로 기존 동작은 보존된다.</p>
 */
@Slf4j
public class EgovSanitizingQueryTransformer implements QueryTransformer {

    /** 이 길이를 넘으면 질의가 아니라 답변으로 판단한다. spring-ai 모듈과 동일한 임계값. */
    static final int MAX_REWRITTEN_LENGTH = 200;

    private static final String THINK_OPEN = "<think>";
    private static final String THINK_CLOSE = "</think>";

    private final QueryTransformer delegate;

    public EgovSanitizingQueryTransformer(QueryTransformer delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    @Override
    public Collection<Query> transform(Query query) {
        Collection<Query> transformed = delegate.transform(query);
        if (transformed == null || transformed.isEmpty()) {
            log.warn("재작성 결과가 비어 있어 원본 질의를 사용한다: '{}'", query.text());
            return List.of(query);
        }
        List<Query> sanitized = new ArrayList<>(transformed.size());
        for (Query candidate : transformed) {
            sanitized.add(sanitize(query, candidate));
        }
        return sanitized;
    }

    private Query sanitize(Query original, Query candidate) {
        if (candidate == null || candidate.text() == null) {
            log.warn("재작성 결과가 비어 있어 원본 질의를 사용한다: '{}'", original.text());
            return original;
        }
        String cleaned = stripThinkBlock(candidate.text());
        if (cleaned.isEmpty() || isLikelyAnswer(cleaned)) {
            log.warn("재작성 결과를 질의로 볼 수 없어 원본 질의를 사용한다: '{}'", candidate.text());
            return original;
        }
        if (cleaned.equals(candidate.text())) {
            return candidate;
        }
        log.info("<think> 블록 제거 후 재작성 질의: '{}'", cleaned);
        return candidate.metadata() == null
                ? Query.from(cleaned)
                : Query.from(cleaned, candidate.metadata());
    }

    /**
     * {@code <think> ... </think>} 블록을 제거한다.
     * 닫는 태그가 없으면(출력이 잘린 경우) 여는 태그 이후를 사용한다.
     */
    static String stripThinkBlock(String text) {
        if (text == null) {
            return "";
        }
        String result = text;
        int close = result.lastIndexOf(THINK_CLOSE);
        if (close >= 0) {
            result = result.substring(close + THINK_CLOSE.length());
        } else {
            int open = result.indexOf(THINK_OPEN);
            if (open >= 0) {
                result = result.substring(open + THINK_OPEN.length());
            }
        }
        return result.trim();
    }

    /** 재작성 결과가 질의가 아니라 답변처럼 보이는지 판단한다. */
    static boolean isLikelyAnswer(String text) {
        if (text == null) {
            return false;
        }
        if (text.contains("```") || text.contains("function") || text.contains("const ")
                || text.contains("return ") || text.contains("class ")) {
            return true;
        }
        if (text.matches("(?s).*예시[는은:].*") || text.contains("다음과 같습니다")
                || text.contains("설명:") || text.contains("코드는")) {
            return true;
        }
        return text.length() > MAX_REWRITTEN_LENGTH;
    }
}

