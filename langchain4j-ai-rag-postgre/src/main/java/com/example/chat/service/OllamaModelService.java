package com.example.chat.service;

import java.util.List;

/**
 * Ollama 모델 관리 서비스 인터페이스
 */
public interface OllamaModelService {

    /**
     * 설치된 Ollama 모델 목록 조회
     *
     * @return 모델명 리스트
     */
    List<String> getInstalledModels();

    /**
     * Ollama 서비스 사용 가능 여부 확인
     *
     * @return Ollama 서비스 사용 가능 여부
     */
    boolean isOllamaAvailable();
}
