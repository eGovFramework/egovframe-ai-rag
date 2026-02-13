package com.example.chat.service.impl;

import com.example.chat.service.OllamaModelService;
import lombok.extern.slf4j.Slf4j;

import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Ollama 모델 관리 서비스 구현
 */
@Slf4j
@Service
public class OllamaModelServiceImpl extends EgovAbstractServiceImpl implements OllamaModelService {

    /**
     * 설치된 Ollama 모델 목록 조회
     *
     * @return 모델명 리스트
     */
    @Override
    public List<String> getInstalledModels() {
        List<String> models = new ArrayList<>();

        try {
            String[] command = new String[]{"ollama", "list"};
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            setupEnvironment(processBuilder);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                boolean isFirstLine = true;

                while ((line = reader.readLine()) != null) {
                    // 첫 번째 줄은 헤더이므로 스킵
                    if (isFirstLine) {
                        isFirstLine = false;
                        continue;
                    }

                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    // 라인을 공백으로 분리하여 첫 번째 컬럼(모델명) 추출
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 1) {
                        String modelName = parts[0].trim();
                        if (!modelName.isEmpty() && !modelName.equals("NAME")) {
                            models.add(modelName);
                            log.debug("발견된 Ollama 모델: {}", modelName);
                        }
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("Ollama 모델 목록 조회 완료: {}개 모델 발견", models.size());
            } else {
                log.warn("ollama list 명령어가 0이 아닌 종료 코드를 반환했습니다: {}", exitCode);
            }

        } catch (IOException | InterruptedException e) {
            log.error("ollama list 명령어 실행 중 오류 발생", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        return models;
    }

    /**
     * Ollama 서비스 사용 가능 여부 확인
     *
     * @return true if Ollama is available, false otherwise
     */
    @Override
    public boolean isOllamaAvailable() {
        try {
            String[] command = new String[]{"ollama", "--version"};
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            setupEnvironment(processBuilder);

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            boolean available = exitCode == 0;
            log.debug("Ollama 사용 가능 여부: {}", available);
            return available;

        } catch (IOException | InterruptedException e) {
            log.debug("Ollama 사용 가능 여부 확인 중 오류 발생 (설치되지 않았을 수 있음)", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * OS별 환경 설정
     *
     * @param processBuilder ProcessBuilder 인스턴스
     */
    private void setupEnvironment(ProcessBuilder processBuilder) {
        String os = System.getProperty("os.name").toLowerCase();
        log.debug("현재 OS: {}", os);

        if (os.contains("win")) {
            // Windows: PATH에 일반적인 Ollama 설치 경로 추가
            String path = System.getenv("PATH");
            String ollamaPath = System.getenv("LOCALAPPDATA") + "\\Programs\\Ollama";
            processBuilder.environment().put("PATH", path + ";" + ollamaPath);
        } else if (os.contains("mac")) {
            // macOS: PATH에 Homebrew 및 일반적인 설치 경로 추가
            String path = System.getenv("PATH");
            processBuilder.environment().put("PATH",
                    path + ":/usr/local/bin:/opt/homebrew/bin");
        } else {
            // Linux: PATH에 일반적인 설치 경로 추가
            String path = System.getenv("PATH");
            processBuilder.environment().put("PATH",
                    path + ":/usr/local/bin:/usr/bin");
        }
    }
}
