package com.example.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * LLM 예외/폴백 표준 핸들러
 * <p>
 * 발생 예외를 유형별로 분류하여 표준화된 폴백 메시지를 반환한다.
 * 타임아웃·토큰 초과·연결 오류·일반 오류를 각각 구분하며
 * 외부 의존성을 추가하지 않는다.
 * </p>
 */
@Slf4j
@Component
public class EgovAiFallbackHandler {

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
        /** 기타 오류 */
        GENERAL
    }

    /**
     * 예외를 유형별로 분류한다.
     *
     * @param e 발생한 예외
     * @return 분류된 {@link ErrorType}
     */
    public ErrorType classify(Exception e) {
        if (e instanceof SocketTimeoutException || e instanceof TimeoutException) {
            return ErrorType.TIMEOUT;
        }

        String msg = e.getMessage();
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
     * @param e 발생한 예외
     * @return 사용자에게 반환할 한국어 폴백 메시지
     */
    public String getFallbackMessage(Exception e) {
        ErrorType type = classify(e);
        log.warn("LLM 예외 분류: {} - {}", type, e.getMessage());

        switch (type) {
            case TIMEOUT:
                return "죄송합니다. 서버 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.";
            case TOKEN_LIMIT:
                return "죄송합니다. 입력 또는 응답이 허용 길이를 초과하였습니다. 질문을 짧게 나눠서 다시 시도해주세요.";
            case CONNECTION:
                return "죄송합니다. AI 서버에 연결할 수 없습니다. 서비스 상태를 확인한 후 다시 시도해주세요.";
            default:
                return "죄송합니다. 응답을 생성하는 중에 오류가 발생했습니다: " + e.getMessage();
        }
    }
}
