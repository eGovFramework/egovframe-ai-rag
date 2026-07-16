package com.example.chat.config.etl.readers;

import dev.langchain4j.data.document.Document;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EgovPdfReader}가 페이지 단위로 문서를 생성하고 실제 페이지 번호를 부여하는지 검증한다.
 *
 * <p>PDFBox로 테스트용 다중 페이지 PDF를 즉석 생성해 외부 파일 의존 없이 결정적으로 확인한다.</p>
 */
class EgovPdfReaderPageNumberTest {

    /** page당 지정 텍스트를 담은 PDF 바이트를 생성한다. */
    private byte[] pdfWithPages(String... pageTexts) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 12);
                    cs.newLineAtOffset(72, 720);
                    cs.showText(text);
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Document> parse(Resource resource) throws Exception {
        Method m = EgovPdfReader.class.getDeclaredMethod("parsePdfDocument", Resource.class);
        m.setAccessible(true);
        return (List<Document>) m.invoke(new EgovPdfReader(), resource);
    }

    @Test
    @DisplayName("페이지마다 문서를 만들고 실제 page_number(1,2)와 내용을 부여한다")
    void assignsRealPageNumbers() throws Exception {
        byte[] pdf = pdfWithPages("PAGE ONE ALPHA", "PAGE TWO BRAVO");
        Resource resource = new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return "guide.pdf";
            }
        };

        List<Document> docs = parse(resource);

        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).metadata().getString("page_number")).isEqualTo("1");
        assertThat(docs.get(0).metadata().getString("file_name")).isEqualTo("guide.pdf");
        assertThat(docs.get(0).text()).contains("ALPHA");
        assertThat(docs.get(1).metadata().getString("page_number")).isEqualTo("2");
        assertThat(docs.get(1).text()).contains("BRAVO");
        // 페이지별 고유 id
        assertThat(docs.get(0).metadata().getString("id")).isEqualTo("pdf-guide_1");
        assertThat(docs.get(1).metadata().getString("id")).isEqualTo("pdf-guide_2");
    }

    @Test
    @DisplayName("빈(텍스트 없는) 페이지는 색인에서 건너뛴다")
    void skipsBlankPages() throws Exception {
        byte[] pdf = pdfWithPages("CONTENT PAGE", "   ", "LAST PAGE");
        Resource resource = new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return "doc.pdf";
            }
        };

        List<Document> docs = parse(resource);

        // 빈 2페이지는 제외 → 1·3페이지만, page_number는 실제 페이지 번호 유지
        assertThat(docs).hasSize(2);
        assertThat(docs).extracting(d -> d.metadata().getString("page_number"))
                .containsExactly("1", "3");
    }
}
