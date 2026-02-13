package com.example.chat.util;

import com.google.gson.Gson;
import com.example.chat.config.EmbeddingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 외부 설정 파일 로드 유틸리티
 * ONNX 모델 및 토크나이저 파일 경로를 외부 JSON 파일에서 관리
 */
@Slf4j
@Component
public class ConfigUtils {

    @Value("${app.embedding-config-path}")
    private String configPath;

    /**
     * 설정 파일을 로드하고 환경 변수를 처리하여 EmbeddingConfig 객체를 반환
     *
     * @return 처리된 EmbeddingConfig 객체
     */
    public EmbeddingConfig loadConfig() {
        try {
            // 설정 파일 존재 여부 확인
            java.nio.file.Path path = Paths.get(configPath);
            if (!Files.exists(path)) {
                log.error("설정 파일이 존재하지 않음: {}", configPath);
                return null;
            }

            String jsonStr = new String(Files.readAllBytes(path));
            EmbeddingConfig config = new Gson().fromJson(jsonStr, EmbeddingConfig.class);

            // 경로 처리
            if (config.getModelPath() != null) {
                String resolvedModelPath = resolvePath(config.getModelPath());
                config.setModelPath(resolvedModelPath);
            }

            if (config.getTokenizerPath() != null) {
                String resolvedTokenizerPath = resolvePath(config.getTokenizerPath());
                config.setTokenizerPath(resolvedTokenizerPath);
            }

            log.debug("최종 modelPath: {}", config.getModelPath());
            log.debug("최종 tokenizerPath: {}", config.getTokenizerPath());

            return config;
        } catch (Exception e) {
            log.error("Failed to load search config: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * ${HOME} 환경 변수를 운영체제에 맞게 해석하여 절대 경로로 변환
     *
     * @param path ${HOME}을 포함한 경로
     * @return 절대 경로
     */
    public String resolvePath(String path) {
        log.debug("경로 해석 전: {}", path);

        if (path == null || path.isEmpty()) {
            log.debug("경로가 null 또는 빈 문자열");
            return path;
        }

        // ${HOME} 환경 변수 처리
        if (path.contains("${HOME}")) {
            String homePath;

            // 먼저 HOME 환경 변수 확인 (Linux, macOS)
            homePath = System.getenv("HOME");
            log.debug("HOME 환경 변수: {}", homePath);

            // HOME이 없으면 USERPROFILE 확인 (Windows)
            if (homePath == null || homePath.isEmpty()) {
                homePath = System.getenv("USERPROFILE");
                log.debug("USERPROFILE 환경 변수: {}", homePath);
            }

            // 그래도 없으면 Java의 user.home 속성 사용 (모든 OS)
            if (homePath == null || homePath.isEmpty()) {
                homePath = System.getProperty("user.home");
                log.debug("user.home 속성: {}", homePath);
            }

            String resolvedPath = path.replace("${HOME}", homePath);

            // 경로 구분자 통일
            resolvedPath = normalizePath(resolvedPath);

            return resolvedPath;
        }

        return normalizePath(path);
    }

    /**
     * 경로 구분자를 운영체제에 맞게 정규화
     * @param path 정규화할 경로
     * @return 정규화된 경로
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        // 모든 구분자를 일단 / 로 통일
        String normalized = path.replace('\\', '/');

        // 그 다음 시스템 구분자로 변환
        if (!"/".equals(File.separator)) {
            normalized = normalized.replace('/', File.separatorChar);
        }

        // 경로 정규화
        try {
            normalized = Paths.get(normalized).normalize().toString();
        } catch (Exception e) {
            log.warn("경로 정규화 중 오류 발생: {}", e.getMessage());
        }

        return normalized;
    }
}
