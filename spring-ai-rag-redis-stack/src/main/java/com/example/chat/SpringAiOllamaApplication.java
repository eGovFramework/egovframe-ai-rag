package com.example.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import com.example.chat.service.EgovDocumentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class SpringAiOllamaApplication {

    private final EgovDocumentService documentService;
    
    public static void main(String[] args) {
        SpringApplication.run(SpringAiOllamaApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeDocuments() {
        log.info("문서 인덱싱을 비동기적으로 시작합니다...");
        
        documentService.loadDocumentsAsync()
                .thenAccept(count -> {
                    if (count == 0) {
                        log.info("처리할 문서가 없습니다. 웹 인터페이스에서 문서를 업로드하거나 '문서 로드' 버튼을 클릭하세요.");
                    } else {
                        log.info("문서 인덱싱 완료: {}개 청크 처리됨", count);
                    }
                })
                .exceptionally(throwable -> {
                    log.error("문서 인덱싱 중 오류 발생", throwable);
                    return null;
                });
    }
}