package com.example.chat.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 외부 YAML 파일 기반 프롬프트 템플릿 관리자.
 * <p>
 * classpath:prompts/prompt-templates.yml 에서 프롬프트를 로드하며,
 * {@code {변수명}} 형식의 플레이스홀더 치환을 지원합니다.
 * 기존 {@link PromptEngineeringUtil}의 동작을 보존하면서 외부화를 제공합니다.
 * </p>
 */
@Slf4j
@Component
public class EgovPromptTemplateManager {

    private static final String TEMPLATE_PATH = "classpath:prompts/prompt-templates.yml";
    private static final String ROOT_KEY = "egov";
    private static final String TEMPLATES_KEY = "prompt-templates";

    private final ResourceLoader resourceLoader;
    private Map<String, String> templates = Collections.emptyMap();

    public EgovPromptTemplateManager(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    void init() {
        templates = loadTemplates();
        log.info("프롬프트 템플릿 로드 완료 — {} 개 항목", templates.size());
    }

    /**
     * 키에 해당하는 템플릿을 반환합니다.
     *
     * @param key prompts.* 하위 키 (예: "zero-shot", "context-based")
     * @return 템플릿 문자열, 키가 없으면 빈 문자열
     */
    public String get(String key) {
        String template = templates.get(key);
        if (template == null) {
            log.warn("프롬프트 템플릿 키를 찾을 수 없음: {}", key);
            return "";
        }
        return template;
    }

    /**
     * 키에 해당하는 템플릿에서 플레이스홀더를 치환하여 반환합니다.
     * <p>
     * {@code variables} 맵의 각 엔트리를 {@code {키}} 형식으로 치환합니다.
     * 예: key="context-based", variables=Map.of("context","...")
     * </p>
     *
     * @param key       템플릿 키
     * @param variables 치환할 변수 맵 ({변수명} → 값)
     * @return 치환된 프롬프트 문자열
     */
    public String render(String key, Map<String, String> variables) {
        String template = get(key);
        if (template.isEmpty() || variables == null || variables.isEmpty()) {
            return template;
        }
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            template = template.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return template;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> loadTemplates() {
        Resource resource = resourceLoader.getResource(TEMPLATE_PATH);
        if (!resource.exists()) {
            log.warn("프롬프트 템플릿 파일을 찾을 수 없음: {}", TEMPLATE_PATH);
            return Collections.emptyMap();
        }

        try (InputStream is = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            if (root == null) {
                return Collections.emptyMap();
            }

            Object egovNode = root.get(ROOT_KEY);
            if (!(egovNode instanceof Map)) {
                log.warn("egov 노드가 맵 형식이 아닙니다");
                return Collections.emptyMap();
            }

            Object templatesNode = ((Map<?, ?>) egovNode).get(TEMPLATES_KEY);
            if (!(templatesNode instanceof Map)) {
                log.warn("egov.prompt-templates 노드가 맵 형식이 아닙니다");
                return Collections.emptyMap();
            }

            Map<String, Object> promptsMap = (Map<String, Object>) templatesNode;
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : promptsMap.entrySet()) {
                if (entry.getValue() != null) {
                    result.put(entry.getKey(), entry.getValue().toString());
                }
            }
            return Collections.unmodifiableMap(result);

        } catch (IOException e) {
            log.error("프롬프트 템플릿 파일 로드 중 오류 발생: {}", TEMPLATE_PATH, e);
            return Collections.emptyMap();
        }
    }
}
