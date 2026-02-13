package com.example.chat;

import com.example.chat.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class Langchain4jRagApplication {

    private final DocumentService documentService;

    public static void main(String[] args) {
        SpringApplication.run(Langchain4jRagApplication.class, args);
    }

    /**
     * 애플리케이션 시작 시 문서 자동 인덱싱
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeDocuments() {
        log.info("문서 인덱싱을 비동기적으로 시작합니다...");

        documentService.loadDocumentsAsync()
                .thenAccept(count -> {
                    if (count == 0) {
                        log.info("처리할 문서가 없습니다. 웹 인터페이스에서 문서를 업로드하거나 '문서 재인덱싱' 버튼을 클릭하세요.");
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
