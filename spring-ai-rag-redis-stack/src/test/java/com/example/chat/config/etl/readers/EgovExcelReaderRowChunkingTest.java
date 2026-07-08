package com.example.chat.config.etl.readers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

/**
 * {@link EgovExcelReader} 의 시트 N행 단위 청킹을 검증한다.
 *
 * <p>이전 구현은 시트 하나를 통째로 Document 하나로 만들어, 업무일지처럼 행이 많은
 * 시트는 검색 시 컨텍스트를 초과시켰다(메인테이너 리뷰로 발견). 본 테스트는 시트를
 * {@code rowsPerDocument} 단위로 나눠 여러 Document를 생성하는지, 행 수가 적은 시트는
 * 여전히 Document 하나만 생성하는지 확인한다.</p>
 */
class EgovExcelReaderRowChunkingTest {

    private EgovExcelReader readerWithRowsPerDocument(int rowsPerDocument) throws Exception {
        EgovExcelReader reader = new EgovExcelReader();
        Field field = EgovExcelReader.class.getDeclaredField("rowsPerDocument");
        field.setAccessible(true);
        field.setInt(reader, rowsPerDocument);
        return reader;
    }

    @SuppressWarnings("unchecked")
    private List<Document> parse(EgovExcelReader reader, Resource resource) throws Exception {
        Method method = EgovExcelReader.class.getDeclaredMethod("processExcelResource", Resource.class);
        method.setAccessible(true);
        return (List<Document>) method.invoke(reader, resource);
    }

    private Resource workbookWithRows(int rowCount) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("sheet1");
            for (int i = 0; i < rowCount; i++) {
                var row = sheet.createRow(i);
                row.createCell(0).setCellValue("row-" + i);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ByteArrayResource(out.toByteArray()) {
                @Override
                public String getFilename() {
                    return "sample.xlsx";
                }
            };
        }
    }

    @Test
    @DisplayName("행 수가 rowsPerDocument를 초과하는 시트는 여러 Document로 나뉜다")
    void largeSheetSplitsIntoMultipleDocuments() throws Exception {
        EgovExcelReader reader = readerWithRowsPerDocument(3);
        Resource resource = workbookWithRows(7);

        List<Document> documents = parse(reader, resource);

        // 7행을 3행 단위로 나누면 3+3+1 = 3개 청크
        assertThat(documents).hasSize(3);
        assertThat(documents.get(0).getMetadata().get("chunk_index")).isEqualTo(1);
        assertThat(documents.get(0).getMetadata().get("chunk_count")).isEqualTo(3);
        assertThat(documents.get(0).getText()).contains("row-0").contains("row-1").contains("row-2")
                .doesNotContain("row-3");
        assertThat(documents.get(2).getText()).contains("row-6").doesNotContain("row-5");
    }

    @Test
    @DisplayName("행 수가 rowsPerDocument 이하인 시트는 Document 하나만 생성된다")
    void smallSheetProducesSingleDocument() throws Exception {
        EgovExcelReader reader = readerWithRowsPerDocument(200);
        Resource resource = workbookWithRows(5);

        List<Document> documents = parse(reader, resource);

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getMetadata().get("chunk_index")).isEqualTo(1);
        assertThat(documents.get(0).getMetadata().get("chunk_count")).isEqualTo(1);
        for (int i = 0; i < 5; i++) {
            assertThat(documents.get(0).getText()).contains("row-" + i);
        }
    }

    @Test
    @DisplayName("각 청크의 문서 ID는 시트 번호와 청크 번호를 모두 포함해 서로 구분된다")
    void chunkIdsAreUnique() throws Exception {
        EgovExcelReader reader = readerWithRowsPerDocument(2);
        Resource resource = workbookWithRows(5);

        List<Document> documents = parse(reader, resource);

        List<String> ids = documents.stream().map(Document::getId).toList();
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids).allMatch(id -> id.matches("xlsx-sample_0-sheet1_chunk\\d+"));
    }
}
