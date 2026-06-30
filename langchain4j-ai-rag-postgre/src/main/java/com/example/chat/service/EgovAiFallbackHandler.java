package com.example.chat.service;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.RetriableException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;

/**
 * LLM 예외/폴백 표준 핸들러
 * <p>
 * 발생 예외를 유형별로 분류하여 표준화된 폴백 메시지를 반환한다.
 * LangChain4j 예외 계층(RetriableException/NonRetriableException)과
 * 표준 네트워크/타임아웃 예외를 cause chain 전체 순회를 통해 분류한다.
 * </p>
 */
@Slf4j
@Component
public class EgovAiFallbackHandler {

    /** cause chain 순회 최대 깊이 (무한루프 방지) */
    private static final int MAX_CAUSE_DEPTH = 10;

    /**
     * 예외 유형
     */
    public enum ErrorType {
        /** 요청/응답 타임아웃 */
        TIMEOUT,
        /** 토큰 한도 초과 */
        TOKEN_LIMIT,
        /** 서버 연결 실패 */
        CONNECTION,
        /** 일시적 오류 (재시도 가능) */
        TRANSIENT,
        /** 기타 오류 */
        GENERAL
    }

    /**
     * 예외를 유형별로 분류한다. cause chain 전체를 순회하며 분류한다.
     *
     * @param t 발생한 예외 또는 에러
     * @return 분류된 {@link ErrorType}
     */
    public ErrorType classify(Throwable t) {
        Throwable current = t;
        int depth = 0;

        while (current != null && depth < MAX_CAUSE_DEPTH) {
            ErrorType type = classifySingle(current);
            if (type != ErrorType.GENERAL) {
                return type;
            }
            Throwable cause = current.getCause();
            // 자기참조 가드
            if (cause == current) {
                break;
            }
            current = cause;
            depth++;
        }
        return ErrorType.GENERAL;
    }

    /**
     * 단일 예외 레벨을 분류한다 (cause chain 미순회).
     */
    private ErrorType classifySingle(Throwable t) {
        // LangChain4j TimeoutException — FQN으로 java.util.concurrent.TimeoutException과 구분
        if (t instanceof dev.langchain4j.exception.TimeoutException) {
            return ErrorType.TIMEOUT;
        }

        // 표준 네트워크 타임아웃
        if (t instanceof SocketTimeoutException || t instanceof java.util.concurrent.TimeoutException) {
            return ErrorType.TIMEOUT;
        }

        // LangChain4j RetriableException 계층 — RateLimitException, InternalServerException,
        // UnresolvedModelServerException 포함 (TimeoutException은 위에서 처리)
        if (t instanceof RateLimitException
                || t instanceof UnresolvedModelServerException
                || t instanceof RetriableException) {
            return ErrorType.TRANSIENT;
        }

        // LangChain4j NonRetriableException 계층 — 영구 오류
        if (t instanceof AuthenticationException
                || t instanceof InvalidRequestException
                || t instanceof ContentFilteredException
                || t instanceof ModelNotFoundException
                || t instanceof NonRetriableException) {
            return ErrorType.GENERAL;
        }

        // 메시지 키워드 기반 분류
        String msg = t.getMessage();
        if (msg == null) {
            return ErrorType.GENERAL;
        }

        String lower = msg.toLowerCase();
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return ErrorType.TIMEOUT;
        }
        if (lower.contains("context length") || lower.contains("token")
                || lower.contains("maximum context") || lower.contains("too long")) {
            return ErrorType.TOKEN_LIMIT;
        }
        if (lower.contains("connection") || lower.contains("connect refused")
                || lower.contains("connection refused") || lower.contains("unreachable")) {
            return ErrorType.CONNECTION;
        }
        return ErrorType.GENERAL;
    }

    /**
     * 예외 유형에 따른 표준 폴백 메시지를 반환한다.
     *
     * @param t 발생한 예외 또는 에러
     * @return 사용자에게 반환할 한국어 폴백 메시지
     */
    public String getFallbackMessage(Throwable t) {
        ErrorType type = classify(t);
        log.warn("LLM 예외 분류: {} - {}", type, t.getClass().getSimpleName());

        switch (type) {
            case TIMEOUT:
                return "죄송합니다. 서버 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.";
            case TOKEN_LIMIT:
                return "죄송합니다. 입력 또는 응답이 허용 길이를 초과하였습니다. 질문을 짧게 나눠서 다시 시도해주세요.";
            case CONNECTION:
                return "죄송합니다. AI 서버에 연결할 수 없습니다. 서비스 상태를 확인한 후 다시 시도해주세요.";
            case TRANSIENT:
                return "죄송합니다. 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
            default:
                return "죄송합니다. 응답을 생성하는 중에 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
        }
    }
}
