package com.example.chat.config;

import lombok.Getter;
import lombok.Setter;

/**
 * ONNX 임베딩 모델 설정
 * 외부 JSON 파일에서 로드되는 설정 정보
 */
@Getter
@Setter
public class EgovEmbeddingConfig {
    /**
     * ONNX 모델 파일 경로
     */
    private String modelPath;

    /**
     * 토크나이저 파일 경로
     */
    private String tokenizerPath;
}
