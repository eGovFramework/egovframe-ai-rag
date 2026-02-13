package com.example.chat.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * 일반 챗봇 인터페이스 (RAG 없음)
 * LangChain4j AiServices를 통해 동적 프록시로 구현됨
 * - RAG 검색 없이 LLM의 일반 지식으로 응답
 * - ChatMemory가 자동으로 대화 히스토리 관리
 * - langchain4j-reactor를 통해 Flux<String> 네이티브 지원
 */
public interface SimpleChatbot {

    String SIMPLE_SYSTEM_PROMPT = """
            당신은 도움이 되는 AI 어시스턴트입니다.
            사용자의 질문에 대해 친절하고 정확한 답변을 제공하세요.
            답변은 한국어로 제공하세요.
            """;

    /**
     * 일반 스트리밍 채팅 응답 생성
     * ChatMemory가 자동으로 대화 히스토리를 관리
     * langchain4j-reactor가 Flux 변환을 자동 처리
     *
     * @param query 사용자 질문
     * @return Flux<String> (리액티브 스트리밍 응답)
     */
    @SystemMessage(SIMPLE_SYSTEM_PROMPT)
    Flux<String> streamChat(@UserMessage String query);

    /**
     * 일반 채팅 응답 생성 (비스트리밍)
     *
     * @param query 사용자 질문
     * @return AI 응답
     */
    @SystemMessage(SIMPLE_SYSTEM_PROMPT)
    String chat(@UserMessage String query);
}
