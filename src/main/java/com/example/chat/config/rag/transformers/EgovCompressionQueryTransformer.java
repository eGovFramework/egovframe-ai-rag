package com.example.chat.config.rag.transformers;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EgovCompressionQueryTransformer {

    private final ChatMemory chatMemory;
    private final ChatClient chatClient;
    
    public EgovCompressionQueryTransformer(ChatMemory chatMemory, ChatClient chatClient) {
        this.chatMemory = chatMemory;
        this.chatClient = chatClient;
    }

    
    /**
     * 세션 ID를 직접 전달받아 히스토리 압축을 수행하는 메서드
     */
    public Query transformWithSessionId(@NonNull Query query, String sessionId) {
        log.info("히스토리 압축 시작 - 세션 ID: {}, 원본 질문: {}", sessionId, query.text());
        
        // 세션 ID가 유효하지 않은 경우 히스토리 압축 건너뛰기
        if (sessionId == null || sessionId.trim().isEmpty() || "default".equals(sessionId)) {
            log.warn("유효하지 않은 세션 ID: {} - 히스토리 압축 건너뛰기", sessionId);
            return query;
        }
        
        String originalQuery = query.text();
        log.debug("원본 질문: {}", originalQuery);
        
        // 질문이 너무 짧거나 불완전한 경우 히스토리 압축을 건너뛰고 원본 반환
        if (isIncompleteQuery(originalQuery)) {
            log.info("불완전한 질문으로 판단, 히스토리 압축 건너뛰기: {}", originalQuery);
            return query;
        }
        
        // 세션 히스토리 조회
        List<Message> conversationHistory;
        try {
            conversationHistory = chatMemory.get(sessionId);
            
            if (conversationHistory.isEmpty()) {
                log.info("대화 히스토리가 없음: {}", sessionId);
                return query;
            }
            
            log.info("대화 히스토리 발견: {} - {} 개 메시지", sessionId, conversationHistory.size());
            
        } catch (Exception e) {
            log.warn("세션 {} 히스토리 조회 중 오류 발생 - 히스토리 압축 건너뛰기: {}", sessionId, e.getMessage());
            return query;
        }
        
        // 대화 히스토리 분석 및 로깅
        log.info("대화 히스토리 분석 시작 - {} 개 메시지", conversationHistory.size());
        for (int i = 0; i < conversationHistory.size(); i++) {
            Message msg = conversationHistory.get(i);
            String content = msg.getText();
            String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
            log.info("히스토리 {}: {} - {}", i + 1, msg.getClass().getSimpleName(), preview);
        }
        
        // 대화 히스토리를 Query 객체로 변환
        Query queryWithHistory = Query.builder()
            .text(query.text())
            .history(conversationHistory)
            .build();

        // Spring AI의 CompressionQueryTransformer를 커스텀 프롬프트로 사용
        // 중요: {history}와 {query} 플레이스홀더는 필수설정값
        String customPromptText = """
            You are a query rewriting assistant. Your ONLY job is to rewrite follow-up questions into standalone questions using conversation history.

            STRICT RULES:
            - Output ONLY the rewritten question
            - NO explanations, NO thinking process, NO examples
            - DO NOT use <think> tags or any XML tags
            - Keep the SAME language as the original question (Korean → Korean, English → English)
            - Replace pronouns and references with specific terms from history

            EXAMPLES:

            History:
            User: React의 주요 기능 3가지는?
            Assistant: 1. 컴포넌트 기반 2. Virtual DOM 3. 단방향 데이터 흐름

            Follow-up: 두 번째 기능을 설명해줘
            Rewritten: React의 Virtual DOM 기능을 설명해줘

            ---

            History:
            User: 전자정부 실행환경에서 aop를 처리하는 방법에 대하여 알려 줘
            Assistant: [AOP 처리 방법 설명... 1. 프레임워크 지원 2. 설정 방법 3. Pointcut 표현식]

            Follow-up: 세 번째 사항의 예시를 알려 줘
            Rewritten: 전자정부 실행환경 AOP의 Pointcut 표현식 예시를 알려 줘

            ---

            NOW REWRITE THIS:

            Conversation History:
            {history}

            Follow-up Question:
            {query}

            Rewritten Question:""";

        // String을 PromptTemplate 객체로 변환
        PromptTemplate customPromptTemplate = new PromptTemplate(customPromptText);

        CompressionQueryTransformer compressionTransformer = CompressionQueryTransformer.builder()
            .chatClientBuilder(chatClient.mutate()
                .defaultOptions(ChatOptions.builder()
                    .temperature(0.0)
                    .build()))
            .promptTemplate(customPromptTemplate)
            .build();

        Query compressedQuery = compressionTransformer.transform(queryWithHistory);
        String compressedText = compressedQuery.text();

        log.info("압축 후 생성된 질문: '{}'", compressedText);

        // <think> 블록이 포함되어 있다면 제거 (fallback)
        if (compressedText.contains("<think>")) {
            log.warn("<think> 블록이 여전히 포함되어 있음, 제거 처리");
            String[] parts = compressedText.split("</think>");
            if (parts.length > 1) {
                compressedText = parts[1].trim();
                log.info("</think> 이후 텍스트 추출: '{}'", compressedText);
            } else {
                int thinkIndex = compressedText.indexOf("<think>");
                if (thinkIndex != -1) {
                    compressedText = compressedText.substring(thinkIndex + 7).trim();
                    log.info("<think> 이후 텍스트 추출: '{}'", compressedText);
                }
            }
        }

        // 답변이 아닌 질문인지 검증
        if (isLikelyAnswer(compressedText)) {
            log.warn("생성된 텍스트가 답변처럼 보임, 원본 질문 사용: '{}'", originalQuery);
            compressedText = originalQuery;
        }

        log.info("최종 압축된 질문: '{}'", compressedText);

        // 정리된 텍스트로 Query 재생성
        // history를 유지하여 downstream RAG 컴포넌트가 맥락을 활용할 수 있도록 함
        Query finalQuery = Query.builder()
            .text(compressedText)
            .history(conversationHistory)  // 히스토리 보존
            .build();

        return finalQuery;
    }
    
    /**
     * 생성된 텍스트가 답변처럼 보이는지 확인
     * 코드 블록, 예시 패턴 등이 포함되어 있으면 답변으로 판단
     */
    private boolean isLikelyAnswer(String text) {
        if (text == null) {
            return false;
        }

        // 코드 블록이 포함되어 있으면 답변일 가능성이 높음
        if (text.contains("```") || text.contains("function") || text.contains("const ") ||
            text.contains("return ") || text.contains("class ")) {
            return true;
        }

        // "예시:", "다음과 같습니다:", "설명:" 등의 패턴이 있으면 답변일 가능성이 높음
        if (text.matches(".*예시[는은:].*") || text.contains("다음과 같습니다") ||
            text.contains("설명:") || text.contains("코드는")) {
            return true;
        }

        // 매우 긴 텍스트 (200자 이상)는 답변일 가능성이 높음
        if (text.length() > 200) {
            return true;
        }

        return false;
    }

    /**
     * 질문이 불완전한지 확인
     * 너무 짧거나, 맥락이 필요한 질문인지 판단
     */
    private boolean isIncompleteQuery(String query) {
        if (query == null || query.trim().length() < 5) {
            return true;
        }

        // 매우 짧은 질문만 불완전으로 판단
        String[] words = query.trim().split("\\s+");
        if (words.length < 2) {
            return true;
        }

        // 단일 단어나 매우 짧은 질문만 불완전으로 판단
        if (query.trim().length() < 5) {
            return true;
        }

        return false;
    }
}
