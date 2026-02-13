package com.example.chat.config.etl.readers;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 문서 로더
 * LangChain4j의 ApachePdfBoxDocumentParser 사용
 */
@Slf4j
@Component
public class PdfDocumentReader {

    @Value("${document.pdf-path}")
    private String pdfDocumentPath;

    private final DocumentParser pdfParser = new ApachePdfBoxDocumentParser();

    /**
     * PDF 문서 로드
     */
    public List<Document> read() {
        log.info("PDF 문서 읽기 시작 - 경로: {}", pdfDocumentPath);

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
                    List<Document> documents = parsePdfDocument(resource);
                    log.info("PDF 파일 '{}'에서 {}개의 문서를 읽었습니다.",
                        resource.getFilename(), documents.size());

                    allDocuments.addAll(documents);

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
     * PDF 파일을 파싱하고 페이지별로 문서 생성
     */
    private List<Document> parsePdfDocument(Resource resource) throws IOException {
        String filename = resource.getFilename();
        if (filename == null) {
            filename = "unknown.pdf";
        }

        List<Document> documents = new ArrayList<>();

        try (InputStream inputStream = resource.getInputStream()) {
            // LangChain4j의 PDFBox 파서로 전체 PDF 파싱
            Document fullDocument = pdfParser.parse(inputStream);

            String content = fullDocument.text();

            // 페이지별 분할은 생략하고 전체 문서를 하나로 처리
            // Spring AI의 pagesPerDocument 속성 사용 불가
            // document 객체의 분할은 EnhancedDocumentTransformer 클래스에서 처리
            String baseFilename = filename.replaceAll("\\.pdf$", "");
            String safeFilename = baseFilename.replaceAll("[\\/:*?\"<>|]", "")
                    .replaceAll("\\s+", "-");
            String customId = String.format("pdf-%s_1", safeFilename);

            Metadata metadata = Metadata.from("id", customId);
            metadata.put("file_name", filename);
            metadata.put("source", filename);
            metadata.put("type", "pdf");
            metadata.put("content_length", String.valueOf(content.length()));
            metadata.put("page_number", "1");

            log.debug("PDF Document ID: {} (길이: {})", customId, content.length());

            documents.add(Document.from(content, metadata));
        }

        return documents;
    }
}
