package com.example.chat.service.impl;

import com.example.chat.service.EgovOllamaModelService;
import lombok.extern.slf4j.Slf4j;

import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Ollama 모델 관리 서비스 구현체
 */
@Slf4j
@Service
public class EgovOllamaModelServiceImpl extends EgovAbstractServiceImpl implements EgovOllamaModelService {

    @Override
    public List<String> getInstalledModels() {
        List<String> models = new ArrayList<>();
        
        try {
            // ollama list 명령어 실행
            String[] command = new String[]{"ollama", "list"};
            
            // ollama list 명령어 실행
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            
            // 환경 변수 설정 (macOS/Linux 호환성)
            setupEnvironment(processBuilder);
            
            Process process = processBuilder.start();
            
            // 명령어 출력 읽기
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                boolean isFirstLine = true;
                
                while ((line = reader.readLine()) != null) {
                    // 첫 번째 줄은 헤더이므로 건너뛰기
                    if (isFirstLine) {
                        isFirstLine = false;
                        continue;
                    }
                    
                    // 빈 줄 건너뛰기
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    
                    // 모델 이름 추출 (첫 번째 컬럼이 모델 이름)
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 1) {
                        String modelName = parts[0].trim();
                        if (!modelName.isEmpty() && !modelName.equals("NAME")) {
                            models.add(modelName);
                            log.debug("발견된 모델: {}", modelName);
                        }
                    }
                }
            }
            
            // 프로세스 종료 대기
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                log.warn("ollama list 명령어가 비정상 종료되었습니다. 종료 코드: {}", exitCode);
            }
            
        } catch (IOException e) {
            log.error("ollama list 명령어 실행 중 IOException 발생", e);
        } catch (InterruptedException e) {
            log.error("ollama list 명령어 실행 중 InterruptedException 발생", e);
            Thread.currentThread().interrupt();
        }
        
        log.info("발견된 Ollama 모델 수: {}", models.size());
        return models;
    }

    @Override
    public boolean isOllamaAvailable() {
        try {
            // ollama --version 명령어로 Ollama 설치 여부 확인
            String[] command = new String[]{"ollama", "--version"};
            
            // ollama --version 명령어로 Ollama 설치 여부 확인
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            
            // 환경 변수 설정 (macOS/Linux 호환성)
            setupEnvironment(processBuilder);
            
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            
            return exitCode == 0;
            
        } catch (IOException | InterruptedException e) {
            log.debug("Ollama 사용 가능 여부 확인 중 오류 발생", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
    
    /**
     * 운영체제별 환경 변수를 설정
     */
    private void setupEnvironment(ProcessBuilder processBuilder) {
        String os = System.getProperty("os.name").toLowerCase();
        String pathSeparator = os.contains("win") ? ";" : ":";
        
        String currentPath = processBuilder.environment().getOrDefault("PATH", "");
        String[] commonPaths;
        
        if (os.contains("win")) {
            // Windows에서 ollama 설치 경로들
            String username = System.getProperty("user.name");
            commonPaths = new String[]{
                "C:\\Users\\" + username + "\\AppData\\Local\\Programs\\Ollama",
                "C:\\Program Files\\Ollama"
            };
        } else if (os.contains("mac")) {
            // macOS에서 ollama 설치 경로들
            commonPaths = new String[]{
                "/usr/local/bin",
                "/opt/homebrew/bin",
                "/usr/bin"
            };
        } else {
            // Linux에서 ollama 설치 경로들
            commonPaths = new String[]{
                "/usr/bin",
                "/usr/local/bin",
                "/opt/ollama"
            };
        }
        
        StringBuilder newPath = new StringBuilder(currentPath);
        for (String path : commonPaths) {
            if (!currentPath.contains(path)) {
                if (newPath.length() > 0) {
                    newPath.append(pathSeparator);
                }
                newPath.append(path);
            }
        }
        
        if (newPath.length() > 0) {
            processBuilder.environment().put("PATH", newPath.toString());
            log.debug("{} PATH 설정: {}", os, newPath.toString());
        }
    }
}
