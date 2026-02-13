package com.example.chat.dto;

/**
 * SSE 스트리밍 토큰 전송용 DTO
 * Spring의 SSE 인코더가 공백을 트리밍하는 문제를 해결하기 위해
 * JSON 객체로 래핑하여 전송
 */
public record StreamTokenDto(String token) {
}
