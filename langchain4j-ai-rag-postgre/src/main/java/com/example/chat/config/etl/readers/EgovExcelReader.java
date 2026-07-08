package com.example.chat.config.etl.readers;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Excel(.xlsx) 문서 로더
 * Apache POI XSSFWorkbook 으로 시트/행/셀 텍스트를 추출하여 RAG 파이프라인에 공급한다.
 */
@Slf4j
@Component
public class EgovExcelReader {

    @Value("${document.xlsx-path:#{null}}")
    private String excelDocumentPath;

    // 시트 하나를 통째로 Document 하나로 만들면 업무일지처럼 행이 많은 시트에서 컨텍스트 초과가
    // 발생하므로(PDF 리더의 pagesPerDocument와 동일한 이유), N행 단위로 나눠 시트당 여러 Document를 생성한다.
    @Value("${document.excel.rows-per-document:200}")
    private int rowsPerDocument;

    /**
     * Excel 문서 로드
     */
    public List<Document> read() {
        if (excelDocumentPath == null || excelDocumentPath.isBlank()) {
            log.info("Excel(xlsx) 문서 경로가 설정되지 않아 건너뜁니다.");
            return List.of();
        }
        log.info("Excel 문서 읽기 시작 - 경로: {}", excelDocumentPath);

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(excelDocumentPath);

            if (resources.length == 0) {
                log.warn("Excel 파일을 찾을 수 없습니다: {}", excelDocumentPath);
                return List.of();
            }

            log.info("{}개의 Excel 파일을 찾았습니다.", resources.length);

            List<Document> allDocuments = new ArrayList<>();

            for (Resource resource : resources) {
                log.info("Excel 파일 처리 중: {}", resource.getFilename());
                try {
                    List<Document> documents = parseExcelDocument(resource);
                    log.info("Excel 파일 '{}'에서 {}개의 문서를 읽었습니다.",
                            resource.getFilename(), documents.size());
                    allDocuments.addAll(documents);
                } catch (Exception e) {
                    log.error("Excel 파일 '{}' 처리 중 오류 발생: {}", resource.getFilename(), e.getMessage());
                }
            }

            log.info("총 {}개의 Excel 문서를 읽었습니다.", allDocuments.size());
            return allDocuments;

        } catch (Exception e) {
            log.error("Excel 문서 읽기 중 오류 발생", e);
            return List.of();
        }
    }

    /**
     * .xlsx 파일을 파싱하여 시트 단위 Document 목록 생성
     */
    private List<Document> parseExcelDocument(Resource resource) throws IOException {
        String filename = resource.getFilename();
        if (filename == null) {
            filename = "unknown.xlsx";
        }

        List<Document> documents = new ArrayList<>();

        try (InputStream inputStream = resource.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {

            int sheetCount = workbook.getNumberOfSheets();
            log.debug("Excel 파일 '{}' - 시트 수: {}", filename, sheetCount);

            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                List<String> rowLines = extractSheetRowLines(sheet);

                if (rowLines.isEmpty()) {
                    log.debug("빈 시트 건너뜀: {} / {}", filename, sheetName);
                    continue;
                }

                String baseFilename = filename.replaceAll("\\.xlsx$", "");
                String safeFilename = baseFilename.replaceAll("[\\/:*?\"<>|]", "")
                        .replaceAll("\\s+", "-");

                int chunkCount = (int) Math.ceil((double) rowLines.size() / rowsPerDocument);
                for (int chunkIdx = 0; chunkIdx < chunkCount; chunkIdx++) {
                    int fromRow = chunkIdx * rowsPerDocument;
                    int toRow = Math.min(fromRow + rowsPerDocument, rowLines.size());
                    String content = String.join("\n", rowLines.subList(fromRow, toRow)) + "\n";

                    String customId = String.format("excel-%s_sheet%d_chunk%d", safeFilename, i + 1, chunkIdx + 1);

                    Metadata metadata = Metadata.from("id", customId);
                    metadata.put("file_name", filename);
                    metadata.put("source", filename);
                    metadata.put("type", "excel");
                    metadata.put("sheet_name", sheetName);
                    metadata.put("sheet_index", String.valueOf(i + 1));
                    metadata.put("chunk_index", String.valueOf(chunkIdx + 1));
                    metadata.put("chunk_count", String.valueOf(chunkCount));
                    metadata.put("content_length", String.valueOf(content.length()));

                    log.debug("Excel Document ID: {} (시트: {}, 청크: {}/{}, 길이: {})",
                            customId, sheetName, chunkIdx + 1, chunkCount, content.length());

                    documents.add(Document.from(content, metadata));
                }
            }
        }

        return documents;
    }

    /**
     * 시트의 모든 행을 탭 구분 텍스트 한 줄씩으로 추출(빈 행 제외).
     * 시트 전체를 하나의 문자열로 합치지 않고 행 단위 목록으로 반환해,
     * 호출부에서 N행 단위로 잘라 여러 Document로 나눌 수 있게 한다.
     */
    private List<String> extractSheetRowLines(Sheet sheet) {
        List<String> lines = new ArrayList<>();

        for (Row row : sheet) {
            StringJoiner rowJoiner = new StringJoiner("\t");
            boolean hasValue = false;

            for (Cell cell : row) {
                String cellValue = extractCellValue(cell);
                rowJoiner.add(cellValue);
                if (!cellValue.isEmpty()) {
                    hasValue = true;
                }
            }

            if (hasValue) {
                lines.add(rowJoiner.toString());
            }
        }

        return lines;
    }

    /**
     * 셀 값을 문자열로 변환
     */
    private String extractCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        CellType cellType = cell.getCellType();

        if (cellType == CellType.FORMULA) {
            cellType = cell.getCachedFormulaResultType();
        }

        switch (cellType) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDateTime dateTime = cell.getLocalDateTimeCellValue();
                    return dateTime.toLocalTime().equals(LocalTime.MIDNIGHT)
                            ? dateTime.toLocalDate().toString()
                            : dateTime.toString();
                }
                double numericValue = cell.getNumericCellValue();
                if (numericValue == Math.floor(numericValue) && !Double.isInfinite(numericValue)) {
                    return String.valueOf((long) numericValue);
                }
                return String.valueOf(numericValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case BLANK:
                return "";
            default:
                return "";
        }
    }
}
