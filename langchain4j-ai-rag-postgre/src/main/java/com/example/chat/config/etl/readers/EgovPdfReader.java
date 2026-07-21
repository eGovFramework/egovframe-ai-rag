package com.example.chat.config.etl.readers;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
 * PDFBox로 페이지 단위 파싱하여 각 페이지에 실제 페이지 번호(page_number)를 부여한다.
 */
@Slf4j
@Component
public class EgovPdfReader {

    @Value("${document.pdf-path}")
    private String pdfDocumentPath;

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
     * PDF 파일을 페이지 단위로 파싱해 페이지당 하나의 문서를 생성한다.
     *
     * <p>PDFBox {@link PDFTextStripper}로 페이지별 텍스트를 추출하고, 각 문서에 실제 페이지
     * 번호({@code page_number})를 부여한다(출처 인용에 필요). 텍스트가 비어 있는 페이지(스캔
     * 이미지 등)는 색인에서 건너뛴다. 페이지 내 청킹은 {@code EgovEnhancedDocumentTransformer}가
     * 담당하며, 페이지 단위로 문서가 나뉘므로 청크가 페이지 경계를 넘지 않는다.</p>
     */
    List<Document> parsePdfDocument(Resource resource) throws IOException {
        String filename = resource.getFilename();
        if (filename == null) {
            filename = "unknown.pdf";
        }
        String baseFilename = filename.replaceAll("\\.pdf$", "");
        String safeFilename = baseFilename.replaceAll("[\\/:*?\"<>|]", "").replaceAll("\\s+", "-");

        List<Document> documents = new ArrayList<>();

        try (InputStream inputStream = resource.getInputStream();
             PDDocument pdf = PDDocument.load(inputStream)) {

            int pageCount = pdf.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();

            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String content = stripper.getText(pdf);

                if (content == null || content.isBlank()) {
                    log.debug("PDF '{}' {}페이지: 빈 내용으로 건너뜀", filename, page);
                    continue;
                }

                String customId = String.format("pdf-%s_%d", safeFilename, page);
                Metadata metadata = Metadata.from("id", customId);
                metadata.put("file_name", filename);
                metadata.put("source", filename);
                metadata.put("type", "pdf");
                metadata.put("content_length", String.valueOf(content.length()));
                metadata.put("page_number", String.valueOf(page));

                documents.add(Document.from(content, metadata));
            }

            log.debug("PDF '{}': {}페이지 중 {}개 문서 생성", filename, pageCount, documents.size());
        }

        return documents;
    }
}
