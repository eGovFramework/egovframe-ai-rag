package com.example.chat.config.etl.readers;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EgovPdfReader implements DocumentReader {

    @Value("${spring.ai.document.pdf-path}")
    private String pdfDocumentPath;

    @Value("${spring.ai.document.pdf.page-top-margin:0}")
    private int pageTopMargin;

    @Value("${spring.ai.document.pdf.pages-per-document:1}")
    private int pagesPerDocument;

    @Override
    public List<Document> get() {
        log.info("PDF 문서 읽기 시작 - 경로: {}", pdfDocumentPath);
        log.info("PDF 설정 - 페이지 상단 여백: {}, 페이지당 문서: {}", 
            pageTopMargin, pagesPerDocument);
        
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(pdfDocumentPath);
            
            if (resources.length == 0) {
                log.warn("PDF 파일을 찾을 수 없습니다: {}", pdfDocumentPath);
                return List.of();
            }
            
            log.info("{}개의 PDF 파일을 찾았습니다.", resources.length);
            
            List<Document> allDocuments = new ArrayList<>();
            
            for (Resource resource : resources) {
                log.info("PDF 파일 처리 중: {}", resource.getFilename());
                
                try {
                    // Spring AI의 PagePdfDocumentReader 사용
                    PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(
                        resource,
                        PdfDocumentReaderConfig.builder()
                            .withPageTopMargin(pageTopMargin)
                            .withPagesPerDocument(pagesPerDocument)
                            .build()
                    );
                    
                    List<Document> documents = pdfReader.read();
                    log.info("PDF 파일 '{}'에서 {}개의 문서를 읽었습니다.", 
                        resource.getFilename(), documents.size());
                    
                    // 각 문서의 내용 길이와 메타데이터 로깅
                    for (int i = 0; i < documents.size(); i++) {
                        Document doc = documents.get(i);
                        log.debug("PDF 문서 {}: ID={}, 길이={}, 메타데이터={}", 
                            i + 1, doc.getId(), doc.getText().length(), doc.getMetadata());
                    }
                    
                    // Document ID를 파일명과 페이지 기반으로 재생성
                    List<Document> documentsWithCustomIds = createDocumentsWithCustomIds(
                        documents, resource.getFilename());
                    
                    log.info("PDF 파일 '{}'에서 {}개의 문서를 커스텀 ID로 변환했습니다.", 
                        resource.getFilename(), documentsWithCustomIds.size());
                    
                    allDocuments.addAll(documentsWithCustomIds);
                    
                } catch (Exception e) {
                    log.error("PDF 파일 '{}' 처리 중 오류 발생: {}", resource.getFilename(), e.getMessage());
                    // 개별 파일 오류는 무시하고 계속 진행
                }
            }
            
            log.info("총 {}개의 PDF 문서를 읽었습니다.", allDocuments.size());
            return allDocuments;
            
        } catch (Exception e) {
            log.error("PDF 문서 읽기 중 오류 발생", e);
            return List.of();
        }
    }
    
    /**
     * Document ID를 파일명과 페이지 기반으로 재생성
     */
    private List<Document> createDocumentsWithCustomIds(List<Document> documents, String filename) {
        List<Document> documentsWithCustomIds = new ArrayList<>();
        
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            String content = document.getText();
            
            // 빈 페이지 체크 (로깅만)
            if (content == null || content.trim().isEmpty()) {
                log.debug("PDF 페이지 {}: 빈 내용 (길이: 0)", i + 1);
            } else if (content.trim().length() < 10) {
                log.debug("PDF 페이지 {}: 매우 짧은 내용 (길이: {})", i + 1, content.trim().length());
            }
            
            // 파일명에서 확장자 제거
            String baseFilename = filename.replaceAll("\\.pdf$", "");
            
            // 안전한 파일명 생성 (특수문자 제거)
            String safeFilename = baseFilename.replaceAll("[\\/:*?\"<>|]", "").replaceAll("\\s+", "-");
            
            // 새로운 Document ID 생성: pdf-파일명_페이지번호
            String customId = String.format("pdf-%s_%d", safeFilename, i + 1);
            
            // 메타데이터에 원본 ID와 페이지 정보 추가
            document.getMetadata().put("original_id", document.getId());
            document.getMetadata().put("page_number", i + 1);
            document.getMetadata().put("file_name", filename);
            document.getMetadata().put("source", filename);
            document.getMetadata().put("type", "pdf");
            document.getMetadata().put("content_length", content.length());
            
            log.debug("PDF Document ID 변경: {} -> {} (길이: {})", document.getId(), customId, content.length());
            
            documentsWithCustomIds.add(new Document(customId, content, document.getMetadata()));
        }
        
        log.info("PDF 파일 '{}': 원본 {}개 문서 중 {}개 문서로 변환됨", 
            filename, documents.size(), documentsWithCustomIds.size());
        
        return documentsWithCustomIds;
    }
} 