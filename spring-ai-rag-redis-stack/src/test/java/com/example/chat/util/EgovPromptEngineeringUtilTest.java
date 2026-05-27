package com.example.chat.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EgovPromptEngineeringUtil 단위 테스트")
class EgovPromptEngineeringUtilTest {

    @Test
    @DisplayName("createZeroShotPrompt: 응답 언어 지시 포함 여부 확인")
    void createZeroShotPrompt_containsKoreanInstruction() {
        String prompt = EgovPromptEngineeringUtil.createZeroShotPrompt();

        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("Respond in Korean");
    }

    @Test
    @DisplayName("createContextBasedPrompt: 컨텍스트 문자열이 프롬프트에 포함되는지 확인")
    void createContextBasedPrompt_embedsContext() {
        String context = "eGovFrame 5.0 신규 기능 안내";
        String prompt = EgovPromptEngineeringUtil.createContextBasedPrompt(context);

        assertThat(prompt).contains(context);
        assertThat(prompt).contains("ONLY on the provided context information");
    }

    @Test
    @DisplayName("createFewShotLearningPrompt: 컨텍스트 및 Few-shot 예시 포함 여부 확인")
    void createFewShotLearningPrompt_containsExamplesAndContext() {
        String context = "Spring Boot 자동 구성 원리";
        String prompt = EgovPromptEngineeringUtil.createFewShotLearningPrompt(context);

        assertThat(prompt).contains(context);
        assertThat(prompt).contains("Few-shot Examples");
        assertThat(prompt).contains("Spring Boot");
    }

    @Test
    @DisplayName("createChainOfThoughtPrompt: 단계별 사고 지시 포함 여부 확인")
    void createChainOfThoughtPrompt_containsChainOfThoughtInstructions() {
        String prompt = EgovPromptEngineeringUtil.createChainOfThoughtPrompt();

        assertThat(prompt).contains("Chain of Thought Process");
        assertThat(prompt).contains("Thinking Process");
    }

    @Test
    @DisplayName("createCodeGenerationPrompt: 언어와 요구사항이 프롬프트에 포함되는지 확인")
    void createCodeGenerationPrompt_embedsLanguageAndRequirement() {
        String language = "Java";
        String requirement = "파일 업로드 기능 구현";
        String prompt = EgovPromptEngineeringUtil.createCodeGenerationPrompt(language, requirement);

        assertThat(prompt).contains(language);
        assertThat(prompt).contains(requirement);
        assertThat(prompt).contains("Korean");
    }

    @Test
    @DisplayName("createZeroShotCodeGenerationPrompt: 언어와 요구사항 포함 여부 확인")
    void createZeroShotCodeGenerationPrompt_embedsLanguageAndRequirement() {
        String language = "Python";
        String requirement = "CSV 파일 파싱";
        String prompt = EgovPromptEngineeringUtil.createZeroShotCodeGenerationPrompt(language, requirement);

        assertThat(prompt).contains(language);
        assertThat(prompt).contains(requirement);
    }

    @Test
    @DisplayName("createStructuredOutputPrompt: 구조 문자열이 프롬프트에 포함되는지 확인")
    void createStructuredOutputPrompt_embedsStructure() {
        String structure = "요약, 상세설명, 예시, 참고사항";
        String prompt = EgovPromptEngineeringUtil.createStructuredOutputPrompt(structure);

        assertThat(prompt).contains(structure);
        assertThat(prompt).contains("Korean");
    }

    @Test
    @DisplayName("getDefaultStructuredFormat: 기본 섹션 헤더 포함 여부 확인")
    void getDefaultStructuredFormat_containsDefaultSections() {
        String format = EgovPromptEngineeringUtil.getDefaultStructuredFormat();

        assertThat(format).contains("## Summary");
        assertThat(format).contains("## Details");
        assertThat(format).contains("## Example");
        assertThat(format).contains("## Notes");
    }

    @Test
    @DisplayName("createRoleBasedPrompt: 역할과 작업이 프롬프트에 포함되는지 확인")
    void createRoleBasedPrompt_embedsRoleAndTask() {
        String role = "보안 전문가";
        String task = "SQL 인젝션 취약점 분석";
        String prompt = EgovPromptEngineeringUtil.createRoleBasedPrompt(role, task);

        assertThat(prompt).contains(role);
        assertThat(prompt).contains(task);
        assertThat(prompt).contains("Korean");
    }

    @Test
    @DisplayName("createZeroShotRoleBasedPrompt: 역할과 작업 포함 여부 확인")
    void createZeroShotRoleBasedPrompt_embedsRoleAndTask() {
        String role = "데이터 분석가";
        String task = "월별 트래픽 분석";
        String prompt = EgovPromptEngineeringUtil.createZeroShotRoleBasedPrompt(role, task);

        assertThat(prompt).contains(role);
        assertThat(prompt).contains(task);
    }

    @Test
    @DisplayName("createDynamicFewShotPrompt: 컨텍스트와 예시 Q&A가 프롬프트에 포함되는지 확인")
    void createDynamicFewShotPrompt_embedsContextAndExamples() {
        String context = "eGovFrame 공통컴포넌트 개요";
        List<Map.Entry<String, String>> examples = List.of(
                Map.entry("eGovFrame이란?", "전자정부 표준 프레임워크입니다."),
                Map.entry("공통컴포넌트는?", "재사용 가능한 253개 기능 모음입니다.")
        );
        String prompt = EgovPromptEngineeringUtil.createDynamicFewShotPrompt(context, examples);

        assertThat(prompt).contains(context);
        assertThat(prompt).contains("eGovFrame이란?");
        assertThat(prompt).contains("전자정부 표준 프레임워크입니다.");
        assertThat(prompt).contains("공통컴포넌트는?");
    }

    @Test
    @DisplayName("createDynamicFewShotPrompt: 빈 예시 리스트도 정상 처리")
    void createDynamicFewShotPrompt_withEmptyExamples() {
        String prompt = EgovPromptEngineeringUtil.createDynamicFewShotPrompt("context", List.of());

        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("context");
    }

    @Test
    @DisplayName("createStepByStepPrompt: 작업 문자열이 프롬프트에 포함되는지 확인")
    void createStepByStepPrompt_embedsTask() {
        String task = "REST API 설계 및 구현";
        String prompt = EgovPromptEngineeringUtil.createStepByStepPrompt(task);

        assertThat(prompt).contains(task);
        assertThat(prompt).contains("step by step");
        assertThat(prompt).contains("Korean");
    }

    @Test
    @DisplayName("createQualityCheckPrompt: 기준과 콘텐츠가 프롬프트에 포함되는지 확인")
    void createQualityCheckPrompt_embedsCriteriaAndContent() {
        String criteria = "가독성, 정확성, 완결성";
        String content = "검토 대상 문서 내용";
        String prompt = EgovPromptEngineeringUtil.createQualityCheckPrompt(criteria, content);

        assertThat(prompt).contains(criteria);
        assertThat(prompt).contains(content);
        assertThat(prompt).contains("Korean");
    }
}
