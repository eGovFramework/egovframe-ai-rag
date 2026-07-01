package com.example.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * LLM 호출 예외를 분류하고 표준 폴백 메시지를 반환하는 핸들러
 *
 * <p>예외 유형을 타임아웃 / 토큰 초과 / 연결 오류 / 일시적 오류 / 일반 오류로 분류하여
 * 서비스 계층에서 일관된 폴백 메시지를 제공한다.</p>
 *
 * <p>Spring AI {@link TransientAiException} / {@link NonTransientAiException} 및
 * Reactor Netty 계층 예외(FQN 문자열 매칭)를 포함하며, cause chain 전체를 순회한다.</p>
 */
@Slf4j
@Component
public class EgovAiFallbackHandler {

    /** cause chain 순회 최대 깊이 (무한 루프 방지) */
    private static final int MAX_CAUSE_DEPTH = 20;

    /** Reactor Netty PrematureCloseException FQN */
    private static final String PREMATURE_CLOSE_EXCEPTION_FQN =
            "reactor.netty.http.client.PrematureCloseException";

    /** Netty ConnectTimeoutException FQN */
    private static final String NETTY_CONNECT_TIMEOUT_EXCEPTION_FQN =
            "io.netty.channel.ConnectTimeoutException";

    /** 타임아웃 관련 예외 메시지 키워드 */
    private static final String[] TIMEOUT_KEYWORDS = {
            "timeout", "timed out", "read timed out", "connection timed out"
    };

    /** 토큰 초과 관련 예외 메시지 키워드 */
    private static final String[] TOKEN_KEYWORDS = {
            "token", "context length", "maximum context", "too many tokens",
            "prompt is too long", "input too long", "exceeds the limit"
    };

    /** 연결 오류 관련 예외 메시지 키워드 */
    private static final String[] CONNECTION_KEYWORDS = {
            "connection refused", "connection reset", "no route to host",
            "connect exception", "network is unreachable", "failed to connect",
            "premature close"
    };

    /** 일시적 오류 관련 예외 메시지 키워드 */
    private static final String[] TRANSIENT_KEYWORDS = {
            "rate limit", "too many requests", "service unavailable",
            "internal server error", "bad gateway", "temporarily unavailable"
    };

    /**
     * 예외 유형을 나타내는 열거형
     */
    public enum ErrorType {
        /** LLM 응답 대기 중 시간 초과 */
        TIMEOUT,
        /** 입력 토큰이 모델 허용 한도를 초과 */
        TOKEN_EXCEEDED,
        /** LLM 서버와의 연결 실패 */
        CONNECTION_ERROR,
        /** rate limit·일시 서버 오류 등 재시도 가능한 일시적 오류 */
        TRANSIENT,
        /** 위 유형에 해당하지 않는 일반 오류 */
        GENERAL_ERROR
    }

    /**
     * 예외 유형을 분류한다.
     *
     * <p>cause chain 전체를 순회하여 각 레벨에서 타입 instanceof와 메시지 키워드를 모두 검사한다.
     * 자기참조 cause 및 깊이 초과 시 순회를 중단한다.</p>
     *
     * @param throwable 발생한 예외
     * @return 분류된 {@link ErrorType}
     */
    public ErrorType classify(Throwable throwable) {
        if (throwable == null) {
            return ErrorType.GENERAL_ERROR;
        }

        Throwable current = throwable;
        int depth = 0;
        Set<Throwable> visited = new HashSet<>();

        while (current != null && depth < MAX_CAUSE_DEPTH && visited.add(current)) {
            ErrorType type = classifyAtLevel(current);
            if (type != ErrorType.GENERAL_ERROR) {
                log.debug("예외 분류 (depth={}): {} -> {}", depth, current.getClass().getName(), type);
                return type;
            }
            current = current.getCause();
            depth++;
        }

        return ErrorType.GENERAL_ERROR;
    }

    /**
     * 예외 유형에 맞는 표준 폴백 메시지를 반환한다.
     *
     * @param throwable 발생한 예외
     * @return 사용자에게 전달할 폴백 메시지
     */
    public String getFallbackMessage(Throwable throwable) {
        ErrorType type = classify(throwable);
        String message = buildFallbackMessage(type);
        log.warn("LLM 폴백 처리 - 오류 유형: {}, 원인: {}", type, summarize(throwable));
        return message;
    }

    /**
     * 예외 유형에 맞는 표준 폴백 메시지를 반환한다 (ErrorType 직접 지정).
     *
     * @param type 오류 유형
     * @return 사용자에게 전달할 폴백 메시지
     */
    public String getFallbackMessage(ErrorType type) {
        return buildFallbackMessage(type);
    }

    // ---- private helpers ----

    /**
     * 단일 예외 레벨에서 타입 체크와 메시지 키워드 검사를 수행한다.
     */
    private ErrorType classifyAtLevel(Throwable t) {
        // 1. Spring AI 영구 오류 (인증 실패·잘못된 모델명 등) → GENERAL_ERROR 선 분류
        if (t instanceof NonTransientAiException) {
            return ErrorType.GENERAL_ERROR;
        }

        // 2. Spring AI 일시적 오류 (rate limit·서버 5xx 등) → TRANSIENT
        if (t instanceof TransientAiException) {
            return ErrorType.TRANSIENT;
        }

        // 3. Java 표준 타임아웃
        if (t instanceof TimeoutException || t instanceof SocketTimeoutException) {
            return ErrorType.TIMEOUT;
        }

        // 4. Netty ConnectTimeoutException (FQN 비교 — 전이 의존성 미보장)
        if (NETTY_CONNECT_TIMEOUT_EXCEPTION_FQN.equals(t.getClass().getName())) {
            return ErrorType.TIMEOUT;
        }

        // 5. Java 표준 연결 오류
        if (t instanceof ConnectException) {
            return ErrorType.CONNECTION_ERROR;
        }

        // 6. Reactor Netty PrematureCloseException (FQN 비교)
        if (PREMATURE_CLOSE_EXCEPTION_FQN.equals(t.getClass().getName())) {
            return ErrorType.CONNECTION_ERROR;
        }

        // 7. 메시지 키워드 기반 분류
        return classifyByMessage(t.getMessage());
    }

    private ErrorType classifyByMessage(String message) {
        if (message == null) {
            return ErrorType.GENERAL_ERROR;
        }
        String lower = message.toLowerCase();

        for (String keyword : TIMEOUT_KEYWORDS) {
            if (lower.contains(keyword)) {
                return ErrorType.TIMEOUT;
            }
        }
        for (String keyword : TOKEN_KEYWORDS) {
            if (lower.contains(keyword)) {
                return ErrorType.TOKEN_EXCEEDED;
            }
        }
        for (String keyword : CONNECTION_KEYWORDS) {
            if (lower.contains(keyword)) {
                return ErrorType.CONNECTION_ERROR;
            }
        }
        for (String keyword : TRANSIENT_KEYWORDS) {
            if (lower.contains(keyword)) {
                return ErrorType.TRANSIENT;
            }
        }
        return ErrorType.GENERAL_ERROR;
    }

    private String buildFallbackMessage(ErrorType type) {
        return switch (type) {
            case TIMEOUT -> "AI 모델 응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.";
            case TOKEN_EXCEEDED -> "입력 내용이 너무 길어 처리할 수 없습니다. 질문을 줄여서 다시 시도해 주세요.";
            case CONNECTION_ERROR -> "AI 모델 서버에 연결할 수 없습니다. 서버 상태를 확인한 후 다시 시도해 주세요.";
            case TRANSIENT -> "죄송합니다. 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
            case GENERAL_ERROR -> "AI 응답 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
        };
    }

    private String summarize(Throwable throwable) {
        if (throwable == null) {
            return "null";
        }
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName()
                + (message != null ? ": " + (message.length() > 100 ? message.substring(0, 100) + "..." : message) : "");
    }
}
