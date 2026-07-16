package com.example.chat.config.etl.readers;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * DOCX 문서 로더
 * Apache POI XWPFDocument를 사용하여 .docx 파일에서 텍스트를 추출한다.
 */
@Slf4j
@Component
public class EgovDocxReader {

    @Value("${document.docx-path:#{null}}")
    private String docxDocumentPath;

    /**
     * DOCX 문서 로드
     */
    public List<Document> read() {
        if (docxDocumentPath == null || docxDocumentPath.isBlank()) {
            log.info("DOCX 문서 경로가 설정되지 않아 건너뜁니다.");
            return List.of();
        }
        log.info("DOCX 문서 읽기 시작 - 경로: {}", docxDocumentPath);

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(docxDocumentPath);

            if (resources.length == 0) {
                log.warn("DOCX 파일을 찾을 수 없습니다: {}", docxDocumentPath);
                return List.of();
            }

            log.info("{}개의 DOCX 파일을 찾았습니다.", resources.length);

            List<Document> allDocuments = new ArrayList<>();

            for (Resource resource : resources) {
                log.info("DOCX 파일 처리 중: {}", resource.getFilename());
                try {
                    Document doc = parseDocxDocument(resource);
                    if (doc != null) {
                        allDocuments.add(doc);
                        log.info("DOCX 파일 '{}' 처리 완료 (길이: {})",
                                resource.getFilename(), doc.text().length());
                    }
                } catch (Exception e) {
                    log.error("DOCX 파일 '{}' 처리 중 오류 발생: {}", resource.getFilename(), e.getMessage());
                    // 개별 파일 오류는 무시하고 계속 진행
                }
            }

            log.info("총 {}개의 DOCX 문서를 읽었습니다.", allDocuments.size());
            return allDocuments;

        } catch (Exception e) {
            log.error("DOCX 문서 읽기 중 오류 발생", e);
            return List.of();
        }
    }

    /**
     * DOCX 파일을 파싱하여 Document 생성
     */
    private Document parseDocxDocument(Resource resource) throws IOException {
        String filename = resource.getFilename();
        if (filename == null) {
            filename = "unknown.docx";
        }

        try (InputStream inputStream = resource.getInputStream();
             XWPFDocument xwpfDocument = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(xwpfDocument)) {

            String content = extractor.getText();

            if (content == null || content.trim().isEmpty()) {
                log.warn("빈 DOCX 파일 건너뜀: {}", filename);
                return null;
            }

            String baseFilename = filename.replaceAll("\\.docx$", "");
            String safeFilename = baseFilename.replaceAll("[\\/:*?\"<>|]", "")
                    .replaceAll("\\s+", "-");
            String customId = String.format("docx-%s_1", safeFilename);

            Metadata metadata = Metadata.from("id", customId);
            metadata.put("file_name", filename);
            metadata.put("source", filename);
            metadata.put("type", "docx");
            metadata.put("content_length", String.valueOf(content.length()));

            log.debug("DOCX Document ID: {} (길이: {})", customId, content.length());

            return Document.from(content, metadata);
        }
    }
}
