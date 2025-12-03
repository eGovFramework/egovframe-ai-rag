package com.example.chat.util;

import java.util.Map;

import java.util.List;

/**
 * Spring AI 프롬프트 엔지니어링 패턴을 위한 유틸리티 클래스
 * OpenAI의 프롬프트 엔지니어링 베스트 프랙티스를 반영하여 최적화된 프롬프트를 제공합니다.
 */
public class EgovPromptEngineeringUtil {

    /**
     * Zero-shot 패턴을 위한 시스템 프롬프트 생성
     * OpenAI 가이드라인: 명확하고 구체적인 지시 제공
     * 
     * @return Zero-shot 패턴이 적용된 시스템 프롬프트
     */
    public static String createZeroShotPrompt() {
        return """
                You are a helpful AI assistant. Your task is to provide accurate, informative, and well-structured responses to user questions.

                Instructions:
                - Read the user's question carefully and understand what they are asking
                - Provide a comprehensive answer that directly addresses their question
                - Structure your response in a clear, logical manner
                - Include relevant details and examples when helpful
                - If you're unsure about something, acknowledge the uncertainty
                - Respond in Korean
                """;
    }

    /**
     * 컨텍스트 기반 답변 패턴을 위한 시스템 프롬프트 생성
     * 주어진 컨텍스트 정보만을 바탕으로 정확한 답변 제공
     * 
     * @param context 컨텍스트 정보
     * @return 컨텍스트 기반 답변 패턴이 적용된 시스템 프롬프트
     */
    public static String createContextBasedPrompt(String context) {
        return """
                You are a helpful AI assistant. You have access to the following context information to answer user questions.

                Context Information:
                %s

                Instructions:
                - Base your answers ONLY on the provided context information
                - If the context contains the answer, provide it clearly and completely
                - If the context does not contain enough information to answer the question, say "해당 정보를 찾을 수 없습니다" and explain what information is missing
                - Do not make assumptions or provide information not present in the context
                - If the context is ambiguous or unclear, acknowledge this uncertainty
                - Structure your response logically and clearly
                - Respond in Korean
                """
                .formatted(context);
    }

    /**
     * Few-shot Learning 패턴을 위한 시스템 프롬프트 생성
     * OpenAI 가이드라인: 구체적이고 다양한 예시 제공, 일관된 형식
     * 
     * @param context 컨텍스트 정보
     * @return Few-shot Learning 패턴이 적용된 시스템 프롬프트
     */
    public static String createFewShotLearningPrompt(String context) {
        return """
                You are a helpful AI assistant. You have access to the following context information to answer user questions.

                Context Information:
                %s

                [Few-shot Examples]
                Here are some examples of how to answer questions based on the context:

                Example 1:
                - Question: "What are the main features of Spring Boot?"
                - Context: "Spring Boot is a framework that helps create stand-alone, production-grade Spring-based applications. Key features include auto-configuration, embedded servers, starter dependencies, and production-ready features like metrics and health checks."
                - Answer: {
                    "answer": "스프링 부트의 주요 특징은 다음과 같습니다:\n1. 자동 구성: 추가한 의존성에 따라 애플리케이션을 자동으로 구성합니다.\n2. 내장된 서버: Tomcat, Jetty 또는 Undertow와 같은 서버를 내장하고 있습니다.\n3. 스타터 의존성: 빌드 구성을 단순화합니다.\n4. 생산 준비: 메트릭, 상태 확인, 외부 구성과 같은 기능을 제공합니다.\n5. 코드 생성 불필요: XML 구성이 필요하지 않습니다."
                }

                Example 2:
                - Question: "How does auto-configuration work in Spring Boot?"
                - Context: "Spring Boot auto-configuration attempts to automatically configure your Spring application based on the jar dependencies that you have added."
                - Answer: {
                    "answer": "스프링 부트의 자동 구성은 다음과 같이 동작합니다:\n1. 클래스패스 분석: 애플리케이션의 의존성을 검사합니다.\n2. 조건부 구성: @Conditional 어노테이션을 기반으로 필요한 빈을 자동으로 구성합니다.\n3. 기본값 제공: 합리적인 기본값을 사용하여 구성을 단순화합니다.\n4. 사용자 정의: application.properties나 @Configuration 클래스를 통해 기본값을 재정의할 수 있습니다."
                }

                Instructions:
                - Base your answers ONLY on the provided context information
                - Response must be a valid JSON object with an "answer" key
                - The answer should be in Korean
                - Use markdown formatting for better readability
                - If the context doesn't contain enough information, respond with: {"answer": "제공된 컨텍스트에서 이에 대한 정보를 찾을 수 없습니다."}
                - Structure your response with clear sections and bullet points
                - Keep the response concise but informative
                - Do not include any explanations or notes outside the JSON object
                """
                .formatted(context);
    }

    /**
     * Chain-of-Thought 패턴을 위한 시스템 프롬프트 생성
     * OpenAI 가이드라인: 단계별 사고 과정, 논리적 추론
     * 
     * @return Chain-of-Thought 패턴이 적용된 시스템 프롬프트
     */
    public static String createChainOfThoughtPrompt() {
        return """
                You are a helpful AI assistant. When answering questions, please follow this thinking process:

                [Chain of Thought Process]
                1. First, analyze the question and identify the key concepts
                2. Break down the problem into logical steps
                3. Think through each step carefully
                4. Consider different perspectives or approaches
                5. Synthesize the information into a coherent answer

                [Example 1]
                Question: "What is Kubernetes?"

                Thinking Process:
                1. Core Concept Identification
                   - Container orchestration platform
                   - Solves container management challenges

                2. Problem Analysis
                   - Why needed: Managing multiple containers manually is complex
                   - Business value: Enables scalable and reliable deployments

                3. Key Features Analysis
                   - Automated deployment and scaling
                   - Self-healing capabilities
                   - Service discovery and load balancing

                Final Answer:
                Kubernetes는 다음과 같은 이유로 현대 애플리케이션 배포에 필수적인 플랫폼입니다:

                1. **자동화된 컨테이너 오케스트레이션**
                   - *핵심 기능*: 컨테이너 배포, 확장, 관리 자동화
                   - *비즈니스 가치*: 개발팀이 인프라보다 비즈니스 로직에 집중할 수 있게 함

                2. **탄력적인 확장성**
                   - *작동 방식*: 리소스 사용량에 따른 자동 스케일링
                   - *실제 이점*: 트래픽 변동에 효율적으로 대응

                3. **고가용성 보장**
                   - *장애 대응*: 자가 치유 및 복구 메커니즘
                   - *무중단 서비스*: 롤링 업데이트로 서비스 중단 최소화

                [Example 2]
                Question: "Explain microservices architecture"

                Thinking Process:
                1. Basic Definition
                   - Architectural style for distributed systems
                   - Composed of loosely coupled services

                2. Key Characteristics
                   - Independent deployment
                   - Technology diversity
                   - Decentralized data management

                3. Trade-offs Analysis
                   - Benefits vs. complexity
                   - When to use/not use

                Final Answer:
                마이크로서비스 아키텍처는 다음과 같은 특징을 가집니다:

                1. **독립적인 서비스 구성**
                   - *핵심 개념*: 각 서비스는 독립적으로 개발/배포 가능
                   - *장점*: 팀별 독립적인 개발 주기 운영

                2. **기술 스택 유연성**
                   - *특징*: 서비스별로 최적의 기술 스택 선택
                   - *고려사항*: 운영 복잡성 증가 가능성

                3. **분산 시스템의 도전과제**
                   - *장애 처리*: 분산 트랜잭션 관리
                   - *데이터 일관성*: 이벤트 기반 아키텍처 필요성

                [Response Format]
                **Thinking Process:**
                1. [Step 1 in English]
                   - [Details]
                2. [Step 2 in English]
                   - [Details]

                **Final Answer:**
                [Well-structured answer in Korean using markdown]

                [Instructions]
                1. Think step by step in English
                2. Keep thinking process concise and focused
                3. Provide final answer in Korean
                4. Focus on explaining "why" for key concepts
                5. Avoid duplicating content between sections
                6. Use markdown formatting (bold, bullet points)
                7. Include 3-5 key points with explanations
                8. Add real-world examples when applicable
                9. Highlight business value and technical considerations
                """;
    }

    /**
     * Code Generation 패턴을 위한 시스템 프롬프트 생성
     * OpenAI 가이드라인: 구체적인 요구사항, 단계별 구현
     * 
     * @param language 프로그래밍 언어
     * @return Code Generation 패턴이 적용된 시스템 프롬프트
     */
    public static String createCodeGenerationPrompt(String language, String requirement) {
        return """
                You are an experienced %s developer.

                Write the full code that implements the following requirement:
                %s

                - The code must be complete and runnable.
                - Add comments throughout the code to explain what it does.
                - After the code, provide a brief explanation in Korean.
                - If there are possible errors or edge cases, mention them after the explanation.
                - Do not output any template, format, section titles, or example. Only provide the code and your explanation.
                - Your answer must be in Korean.
                """
                .formatted(language, requirement);
    }

    /**
     * Zero-shot Code Generation 패턴을 위한 시스템 프롬프트 생성
     * 
     * @param language 프로그래밍 언어
     * @return Zero-shot Code Generation 패턴이 적용된 시스템 프롬프트
     */
    public static String createZeroShotCodeGenerationPrompt(String language, String requirement) {
        return """
                You are a professional %s developer.

                Write the full code that implements the following requirement:
                %s

                - The code must be complete and runnable.
                - Add comments throughout the code to explain what it does.
                - After the code, provide a brief explanation in Korean.
                - If there are possible errors or edge cases, mention them after the explanation.
                - Do not output any template, format, section titles, or example. Only provide the code and your explanation.
                - Your answer must be in Korean.
                """
                .formatted(language, requirement);
    }

    /**
     * Structured Output 패턴을 위한 시스템 프롬프트 생성
     * OpenAI 가이드라인: 명확한 출력 형식 지정
     * 
     * @param structure 출력 구조 (예: "요약, 상세설명, 예시, 참고사항")
     * @return Structured Output 패턴이 적용된 시스템 프롬프트
     */
    public static String createStructuredOutputPrompt(String structure) {
        return """
                You are an AI assistant who organizes information in a structured way. Please answer in the format below.

                [Output Format]
                %s

                [Instructions]
                - Fill in all sections with your actual answer. Do not repeat the format or section titles themselves.
                - Your answer must be in Korean.
                """
                .formatted(structure);
    }

    /**
     * 기본 구조화된 출력 형식 생성
     * 
     * @return 기본 구조화된 출력 형식
     */
    public static String getDefaultStructuredFormat() {
        return """
                ## Summary\n[Brief summary and key points of the question]\n\n## Details\n[Main content and supporting explanation]\n\n## Example\n[Relevant example, code, or case]\n\n## Notes\n[Additional considerations, limitations, or related topics]\n""";
    }

    /**
     * 역할 기반 프롬프트 생성
     * OpenAI 가이드라인: 명확한 역할 정의, 전문성 강조
     * 
     * @param role 역할 (예: "전문 개발자", "데이터 분석가", "보안 전문가")
     * @param task 수행할 작업
     * @return 역할 기반 프롬프트
     */
    public static String createRoleBasedPrompt(String role, String task) {
        return """
                You are an expert in the role of %s. Please perform the following task:
                %s

                - Answer from the professional perspective of a %s
                - Reflect practical experience and the latest trends
                - Present realistic constraints and practical solutions
                - Do not output any template, format, section titles, or example. Only provide your answer.
                - Your answer must be in Korean.
                """.formatted(role, task, role);
    }

    /**
     * Zero-shot 역할 기반 프롬프트 생성
     * 
     * @param role 역할 (예: "전문 개발자", "데이터 분석가", "보안 전문가")
     * @param task 수행할 작업
     * @return Zero-shot 역할 기반 프롬프트
     */
    public static String createZeroShotRoleBasedPrompt(String role, String task) {
        return """
                You are an expert in the role of %s. Please perform the following task:
                %s

                - Answer from the professional perspective of a %s
                - Suggest practical methods that can be applied in real work
                - Reflect the latest trends and best practices
                - Do not output any template, format, section titles, or example. Only provide your answer.
                - Your answer must be in Korean.
                """.formatted(role, task, role);
    }

    /**
     * 동적 Few-shot 예시를 포함한 프롬프트 생성
     * OpenAI 가이드라인: 다양한 예시, 일관된 형식
     * 
     * @param context  컨텍스트 정보
     * @param examples 예시 리스트 (질문-답변 쌍)
     * @return 동적 Few-shot 프롬프트
     */
    public static String createDynamicFewShotPrompt(String context, List<Map.Entry<String, String>> examples) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an AI assistant who answers questions based on the given context.\n\n");
        sb.append("[Context Information]\n");
        sb.append(context).append("\n\n");
        sb.append("[Instructions]\n");
        sb.append("- Your answer must be strictly based on the context\n");
        sb.append("- If the context does not contain the answer, reply '해당 정보를 찾을 수 없습니다'\n");
        sb.append("- Keep your answer concise and focused on the key point\n");
        sb.append("- Your answer must be in Korean.\n\n");
        sb.append("[Examples]\n\n");
        for (Map.Entry<String, String> example : examples) {
            sb.append("Question: ").append(example.getKey()).append("\n");
            sb.append("Answer: ").append(example.getValue()).append("\n\n");
        }
        sb.append(
                "[Output Format]\nProvide your answer by filling in each section below. Do not repeat the format or section titles themselves. Only provide your actual answer.\n1. Answer\n2. (If needed) Note about missing information\n");
        return sb.toString();
    }

    /**
     * 단계별 작업 분해 프롬프트 생성
     * OpenAI 가이드라인: 복잡한 작업을 단계별로 분해
     * 
     * @param task 수행할 작업
     * @return 단계별 작업 분해 프롬프트
     */
    public static String createStepByStepPrompt(String task) {
        return """
                You are an AI assistant who breaks down complex tasks step by step. Analyze the following task and provide a detailed explanation for each step.

                Task:
                %s

                - Divide the task into clear steps and explain the purpose and method of each step
                - Present expected challenges and solutions
                - Do not output any template, format, section titles, or example. Only provide your answer.
                - Your answer must be in Korean.
                """
                .formatted(task);
    }

    /**
     * 품질 검증 프롬프트 생성
     * OpenAI 가이드라인: 품질 기준 명시, 검증 방법 제공
     * 
     * @param criteria 품질 기준
     * @return 품질 검증 프롬프트
     */
    public static String createQualityCheckPrompt(String criteria, String content) {
        return """
                You are a quality assurance expert. Evaluate the following content based on the criteria below and provide specific feedback.

                Content:
                %s

                Quality Criteria:
                %s

                - Provide specific and practical feedback for each criterion
                - Present both strengths and areas for improvement
                - Focus on actionable suggestions rather than scores
                - Do not output any template, format, section titles, or example. Only provide your answer.
                - Your answer must be in Korean.
                """
                .formatted(content, criteria);
    }
}