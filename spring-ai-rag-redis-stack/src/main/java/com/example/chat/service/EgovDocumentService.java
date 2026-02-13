package com.example.chat.service;

import java.util.concurrent.CompletableFuture;
import org.springframework.web.multipart.MultipartFile;

import com.example.chat.response.DocumentStatusResponse;

import java.util.Map;

/**
 * 문서 처리 서비스 인터페이스
 * 마크다운 문서를 로드하고 벡터 저장소에 저장하는 기능 제공
 */
public interface EgovDocumentService {
    
    /**
     * 문서를 비동기로 로드하고 벡터 저장소에 저장
     * 
     * @return 처리된 문서 수
     */
    CompletableFuture<Integer> loadDocumentsAsync();

    // 처리 상태 확인 메서드
    boolean isProcessing();
    int getProcessedCount();
    int getTotalCount();
    int getChangedCount();

    // 파일 업로드 및 검증/저장
    Map<String, Object> uploadMarkdownFiles(MultipartFile[] files);

    // 재인덱싱 요청(비동기) 및 결과 메시지 반환
    String reindexDocuments();

    // 상태 응답 객체 반환
    DocumentStatusResponse getStatusResponse();
}
