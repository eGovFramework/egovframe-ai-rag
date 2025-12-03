package com.example.chat.service;

import java.util.List;

/**
 * Ollama 모델 관리 서비스
 */
public interface EgovOllamaModelService {
    
    /**
     * 설치된 Ollama 모델 목록을 가져옴
     * 
     * @return 모델 이름 목록
     */
    List<String> getInstalledModels();
    
    /**
     * Ollama 서비스가 사용 가능한지 확인
     * 
     * @return 사용 가능 여부
     */
    boolean isOllamaAvailable();
}
