package com.example.chat.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * RAG 기반 챗봇 인터페이스
 * LangChain4j AiServices를 통해 동적 프록시로 구현됨
 * - ContentRetriever가 자동으로 관련 문서를 검색하여 컨텍스트에 추가
 * - ChatMemory가 자동으로 대화 히스토리 관리
 * - langchain4j-reactor를 통해 Flux<String> 네이티브 지원
 */
public interface RagChatbot {

    String RAG_SYSTEM_PROMPT = """
            당신은 지식 기반 질의응답 시스템입니다.
            사용자의 질문에 대해 제공된 문서 내용을 기반으로 정확하고 도움이 되는 답변을 제공하세요.
            제공된 문서에 관련 정보가 없는 경우, 솔직하게 모른다고 답변하세요.
            답변은 한국어로 제공하세요.
            """;

    /**
     * RAG 기반 스트리밍 채팅 응답 생성
     * ChatMemory가 자동으로 대화 히스토리를 관리
     * langchain4j-reactor가 Flux 변환을 자동 처리
     *
     * @param query 사용자 질문
     * @return Flux<String> (리액티브 스트리밍 응답)
     */
    @SystemMessage(RAG_SYSTEM_PROMPT)
    Flux<String> streamChat(@UserMessage String query);

    /**
     * RAG 기반 채팅 응답 생성 (비스트리밍)
     *
     * @param query 사용자 질문
     * @return AI 응답
     */
    @SystemMessage(RAG_SYSTEM_PROMPT)
    String chat(@UserMessage String query);
}
