package com.example.chat.controller;

import org.springframework.web.bind.annotation.*;

import com.example.chat.response.DocumentStatusResponse;
import com.example.chat.service.EgovDocumentService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin
public class EgovDocumentController {

    private final EgovDocumentService documentService;

    @GetMapping("/status")
    public DocumentStatusResponse getStatus() {
        return documentService.getStatusResponse();
    }

    @PostMapping("/reindex")
    public String reindexDocuments() {
        return documentService.reindexDocuments();
    }

    /**
     * Markdown 파일 업로드 (최대 5개, .md만, 파일당 5MB, 총 20MB)
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadMarkdownFiles(@RequestParam("files") MultipartFile[] files) {
        Map<String, Object> result = documentService.uploadMarkdownFiles(files);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        if (success) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    @GetMapping("/testAsync")
    public Map<String, String> testAsync() {
        CompletableFuture.runAsync(() -> {
            log.info("CompletableFuture 비동기 작업 실행 - 별도 스레드");
            try {
                Thread.sleep(2000); // 2초 대기 (비동기 확인용)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("CompletableFuture 비동기 작업 완료");
        });
        return Map.of("result", "ok");
    }
}