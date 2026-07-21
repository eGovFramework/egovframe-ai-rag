package com.example.chat.guard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.example.chat.util.EgovInjectionGuardSupport;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * 사용자 질의의 프롬프트 인젝션 여부를 판정합니다.
 */
@Slf4j
@Component
public class EgovInjectionGuard {

    @Value("${rag.guard.enabled:false}")
    private boolean enabled;

    @Value("${rag.guard.policy:log}")
    private String policy;

    @Value("${rag.guard.lexicon-path:}")
    private String lexiconPath;

    private final List<String> lexicon = new ArrayList<>();

    /**
     * 기본 렉시콘과 운영자 확장 렉시콘을 순서대로 불러옵니다.
     */
    @PostConstruct
    public void init() {
        lexicon.clear();
        ClassPathResource seedResource = new ClassPathResource("guard/injection-lexicon.txt");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(seedResource.getInputStream(), StandardCharsets.UTF_8))) {
            addLines(reader);
        } catch (IOException e) {
            log.warn("프롬프트 인젝션 기본 렉시콘을 불러오지 못했습니다.", e);
        }

        if (lexiconPath != null && !lexiconPath.trim().isEmpty()) {
            Path extensionPath = Path.of(lexiconPath.trim());
            if (!Files.exists(extensionPath)) {
                log.warn("프롬프트 인젝션 확장 렉시콘 파일이 존재하지 않습니다: {}", extensionPath);
            } else {
                try (BufferedReader reader = Files.newBufferedReader(extensionPath, StandardCharsets.UTF_8)) {
                    addLines(reader);
                } catch (IOException e) {
                    log.warn("프롬프트 인젝션 확장 렉시콘을 불러오지 못했습니다: {}", extensionPath, e);
                }
            }
        }

        if (!isSupportedPolicy(policy)) {
            log.warn("인식할 수 없는 프롬프트 인젝션 정책값입니다. log 정책으로 폴백합니다: {}", policy);
            policy = "log";
        }
    }

    /**
     * 사용자 질의에 적용할 가드 결정을 반환합니다.
     *
     * @param rawQuery 원본 질의
     * @return 가드 결정
     */
    public GuardDecision inspect(String rawQuery) {
        if (!enabled) {
            return new GuardDecision(true, false, policy, null);
        }

        boolean matched = EgovInjectionGuardSupport.isInjectionAttempt(rawQuery, lexicon);
        if (!matched) {
            return new GuardDecision(true, false, policy, null);
        }

        String normalizedQuery = EgovInjectionGuardSupport.normalizeForDetection(rawQuery);
        String matchedPattern = lexicon.stream()
                .filter(pattern -> normalizedQuery.contains(
                        EgovInjectionGuardSupport.normalizeForDetection(pattern)))
                .findFirst()
                .orElse(null);
        boolean allowed = !"block".equalsIgnoreCase(policy);
        return new GuardDecision(allowed, true, policy, matchedPattern);
    }

    private static boolean isSupportedPolicy(String value) {
        return "log".equalsIgnoreCase(value) || "block".equalsIgnoreCase(value);
    }

    private void addLines(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                lexicon.add(trimmed);
            }
        }
    }

    public record GuardDecision(boolean allowed, boolean matched, String policy, String matchedPattern) {
    }
}
