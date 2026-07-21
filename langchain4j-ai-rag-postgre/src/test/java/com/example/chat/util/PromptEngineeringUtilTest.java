package com.example.chat.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PromptEngineeringUtil} 단위테스트.
 * 각 프롬프트 생성 메서드가 null이 아닌 결과와 전달된 파라미터를 포함하는 문자열을 반환하는지 검증한다.
 */
class PromptEngineeringUtilTest {

    @Test
    @DisplayName("Zero-shot 프롬프트는 지시사항을 포함한다")
    void createZeroShotPromptContainsInstructions() {
        String prompt = PromptEngineeringUtil.createZeroShotPrompt();

        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains("Instructions"));
    }

    @Test
    @DisplayName("컨텍스트 기반 프롬프트는 전달한 컨텍스트를 포함한다")
    void createContextBasedPromptContainsContext() {
        String context = "전자정부 표준프레임워크 관련 문서 내용";

        String prompt = PromptEngineeringUtil.createContextBasedPrompt(context);

        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains(context));
        assertTrue(prompt.contains("Context Information"));
    }

    @Test
    @DisplayName("Few-shot 프롬프트는 컨텍스트와 예시 구조를 포함한다")
    void createFewShotLearningPromptContainsContextAndExamples() {
        String context = "스프링 부트 관련 컨텍스트";

        String prompt = PromptEngineeringUtil.createFewShotLearningPrompt(context);

        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains(context));
        assertTrue(prompt.contains("Few-shot Examples"));
        assertTrue(prompt.contains("answer"));
    }

    @Test
    @DisplayName("Chain-of-Thought 프롬프트는 단계별 사고 과정을 포함한다")
    void createChainOfThoughtPromptContainsThinkingProcess() {
        String prompt = PromptEngineeringUtil.createChainOfThoughtPrompt();

        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains("Chain of Thought Process"));
        assertTrue(prompt.contains("Final Answer"));
    }

    @Test
    @DisplayName("코드 생성 프롬프트는 언어와 요구사항을 포함한다")
    void createCodeGenerationPromptContainsLanguageAndRequirement() {
        String language = "Java";
        String requirement = "REST API 컨트롤러 구현";

        String prompt = PromptEngineeringUtil.createCodeGenerationPrompt(language, requirement);

        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains(language));
        assertTrue(prompt.contains(requirement));
    }

    @Test
    @DisplayName("Zero-shot 코드 생성 프롬프트는 언어와 요구사항을 포함한다")
    void createZeroShotCodeGenerationPromptContainsLanguageAndRequirement() {
        String language = "Python";
        String requirement = "배치 스케줄러 구현";

        String prompt = PromptEngineeringUtil.createZeroShotCodeGenerationPrompt(language, requirement);

        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains(language));
        assertTrue(prompt.contains(requirement));
    }

    @Test
    @DisplayName("구조화된 출력 프롬프트는 전달한 출력 구조를 포함한다")
    void createStructuredOutputPromptContainsStructure() {
        String structure = "요약, 상세설명, 예시, 참고사항";

        String prompt = PromptEngineeringUtil.createStructuredOutputPrompt(structure);

        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains(structure));
        assertTrue(prompt.contains("Output Format"));
    }

    @Test
    @DisplayName("기본 구조화된 출력 형식은 표준 섹션들을 포함한다")
    void getDefaultStructuredFormatContainsStandardSections() {
        String format = PromptEngineeringUtil.getDefaultStructuredFormat();

        assertNotNull(format);
        assertFalse(format.isBlank());
        assertTrue(format.contains("Summary"));
        assertTrue(format.contains("Details"));
        assertTrue(format.contains("Example"));
        assertTrue(format.contains("Notes"));
    }

    @Test
    @DisplayName("역할 기반 프롬프트는 역할과 작업을 포함한다")
    void createRoleBasedPromptContainsRoleAndTask() {
        String role = "보안 전문가";
        String task = "취약점 점검 결과 요약";

        String prompt = PromptEngineeringUtil.createRoleBasedPrompt(role, task);

        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains(role));
        assertTrue(prompt.contains(task));
    }

    @Test
    @DisplayName("Zero-shot 역할 기반 프롬프트는 역할과 작업을 포함한다")
    void createZeroShotRoleBasedPromptContainsRoleAndTask() {
        String role = "데이터 분석가";
        String task = "매출 데이터 분석";

        String prompt = PromptEngineeringUtil.createZeroShotRoleBasedPrompt(role, task);

        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains(role));
        assertTrue(prompt.contains(task));
    }

    @Test
    @DisplayName("동적 Few-shot 프롬프트는 컨텍스트와 예시 목록을 포함한다")
    void createDynamicFewShotPromptContainsContextAndExamples() {
        String context = "게시판 컨텍스트 정보";
        List<Map.Entry<String, String>> examples = List.of(
                Map.entry("게시글 등록 방법은?", "게시글 등록 화면에서 제목과 내용을 입력한 뒤 등록 버튼을 누릅니다."),
                Map.entry("첨부파일 제한 용량은?", "첨부파일은 최대 10MB까지 등록할 수 있습니다."));

        String prompt = PromptEngineeringUtil.createDynamicFewShotPrompt(context, examples);

        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains(context));
        for (Map.Entry<String, String> example : examples) {
            assertTrue(prompt.contains(example.getKey()));
            assertTrue(prompt.contains(example.getValue()));
        }
    }

    @Test
    @DisplayName("단계별 작업 분해 프롬프트는 전달한 작업을 포함한다")
    void createStepByStepPromptContainsTask() {
        String task = "대용량 파일 업로드 기능 구현";

        String prompt = PromptEngineeringUtil.createStepByStepPrompt(task);

        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains(task));
    }

    @Test
    @DisplayName("품질 검증 프롬프트는 기준과 검증 대상 내용을 포함한다")
    void createQualityCheckPromptContainsCriteriaAndContent() {
        String criteria = "가독성, 일관성, 정확성";
        String content = "검증 대상이 되는 응답 내용 예시";

        String prompt = PromptEngineeringUtil.createQualityCheckPrompt(criteria, content);

        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains(criteria));
        assertTrue(prompt.contains(content));
    }
}
