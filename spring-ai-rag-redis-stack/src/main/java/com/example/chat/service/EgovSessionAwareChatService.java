package com.example.chat.service;

import org.springframework.ai.chat.model.ChatResponse;
import com.example.chat.response.TechnologyResponse;
import reactor.core.publisher.Flux;

/**
 * 세션별 채팅 서비스 인터페이스
 * 세션 기반 RAG 응답과 일반 응답을 생성하는 기능 제공
 */
public interface EgovSessionAwareChatService {
    
    /**
     * 세션별 RAG 기반 스트리밍 응답 생성
     * 벡터 저장소에서 관련 문서를 검색하여 LLM에 전달하고 스트리밍 응답 생성
     * 
     * @param query 사용자 질의
     * @param model 사용할 모델명 (null이면 기본 모델 사용)
     * @return 스트리밍 응답 Flux
     */
    Flux<ChatResponse> streamRagResponse(String query, String model);
    
    /**
     * 세션별 일반 스트리밍 응답 생성
     * 벡터 저장소 검색 없이 LLM에 직접 질의하여 스트리밍 응답 생성
     * 
     * @param query 사용자 질의
     * @param model 사용할 모델명 (null이면 기본 모델 사용)
     * @return 스트리밍 응답 Flux
     */
    Flux<ChatResponse> streamSimpleResponse(String query, String model);

    /**
     * JSON 구조화된 출력 - 기술 정보
     * 
     * @param query 사용자 질의
     * @return JSON 구조화된 기술 정보 응답
     */
    TechnologyResponse getTechnologyInfoAsJson(String query);
}