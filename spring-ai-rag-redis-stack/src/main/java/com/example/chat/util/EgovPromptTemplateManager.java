package com.example.chat.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;

/**
 * classpath 기반 프롬프트 템플릿 매니저
 *
 * <p>{@code prompts/prompt-templates.yml}에 정의된 프롬프트를 로드하고,
 * {@code {변수명}} 형식의 플레이스홀더를 치환하여 최종 프롬프트를 반환합니다.
 *
 * <p>사용 예:
 * <pre>{@code
 * // 단순 조회
 * String prompt = manager.get("zero-shot");
 *
 * // 플레이스홀더 치환
 * String prompt = manager.format("context-based", Map.of("context", contextText));
 * }</pre>
 */
@Component
public class EgovPromptTemplateManager {

    private static final Logger log = LoggerFactory.getLogger(EgovPromptTemplateManager.class);

    private static final String TEMPLATES_PATH = "prompts/prompt-templates.yml";

    /** egov.prompt-templates 아래 로드된 키-값 맵 */
    private Map<String, String> templates = Collections.emptyMap();

    /**
     * 애플리케이션 기동 시 YAML 파일을 한 번 로드합니다.
     * 파일이 없거나 파싱 오류가 발생하면 빈 맵으로 동작(기존 유틸 폴백 가능).
     */
    @PostConstruct
    @SuppressWarnings("unchecked")
    void load() {
        ClassPathResource resource = new ClassPathResource(TEMPLATES_PATH);
        if (!resource.exists()) {
            log.warn("프롬프트 템플릿 파일을 찾을 수 없습니다: {}", TEMPLATES_PATH);
            return;
        }
        try (InputStream is = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            if (root == null) {
                log.warn("프롬프트 템플릿 YAML이 비어 있습니다: {}", TEMPLATES_PATH);
                return;
            }
            Object egov = root.get("egov");
            if (egov instanceof Map<?, ?> egovMap) {
                Object promptTemplates = egovMap.get("prompt-templates");
                if (promptTemplates instanceof Map<?, ?> rawMap) {
                    Map<String, String> loaded = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                        if (entry.getKey() instanceof String k && entry.getValue() instanceof String v) {
                            loaded.put(k, v);
                        }
                    }
                    templates = Collections.unmodifiableMap(loaded);
                    log.info("프롬프트 템플릿 {}개 로드 완료 ({})", templates.size(), TEMPLATES_PATH);
                }
            }
        } catch (IOException e) {
            log.error("프롬프트 템플릿 파일 로드 실패: {}", TEMPLATES_PATH, e);
        }
    }

    /**
     * 키에 해당하는 프롬프트 템플릿 원문을 반환합니다.
     *
     * @param key 템플릿 키 (예: "zero-shot", "context-based")
     * @return 템플릿 문자열, 키가 없으면 빈 문자열
     */
    public String get(String key) {
        return templates.getOrDefault(key, "");
    }

    /**
     * 키에 해당하는 템플릿에서 {@code {변수명}} 플레이스홀더를 치환한 결과를 반환합니다.
     *
     * @param key       템플릿 키
     * @param variables 치환할 변수 맵 (키: 변수명, 값: 치환 문자열)
     * @return 플레이스홀더가 치환된 프롬프트 문자열
     */
    public String format(String key, Map<String, String> variables) {
        String template = get(key);
        if (template.isEmpty() || variables == null || variables.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    /**
     * 로드된 템플릿 키 목록을 반환합니다.
     *
     * @return 읽기 전용 키 집합
     */
    public java.util.Set<String> keys() {
        return templates.keySet();
    }

    /**
     * 로드된 템플릿 수를 반환합니다.
     *
     * @return 템플릿 수
     */
    public int size() {
        return templates.size();
    }
}
