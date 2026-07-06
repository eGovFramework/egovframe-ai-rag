package com.example.chat.config.etl.readers;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDate;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link EgovExcelReader} 의 날짜 셀 처리를 검증한다.
 *
 * <p>이전 구현은 NUMERIC 셀 타입을 전부 숫자 문자열로 변환해, Excel이 내부적으로
 * 시리얼 숫자로 저장하는 날짜 셀도 예컨대 "45000" 같은 숫자로 색인됐다. 본 테스트는
 * 날짜 서식이 적용된 셀이 실제 날짜 문자열로, 일반 숫자 셀은 그대로 숫자로 추출되는지
 * 확인한다.</p>
 */
class EgovExcelReaderDateCellTest {

    private String cellValueAsString(Cell cell) throws Exception {
        Method method = EgovExcelReader.class.getDeclaredMethod("getCellValueAsString", Cell.class);
        method.setAccessible(true);
        return (String) method.invoke(new EgovExcelReader(), cell);
    }

    @Test
    @DisplayName("날짜 서식이 적용된 숫자 셀은 시리얼 숫자가 아니라 날짜 문자열로 추출된다")
    void dateFormattedCellReturnsDateString() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(LocalDate.of(2026, 3, 15));

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            cell.setCellStyle(dateStyle);

            String value = cellValueAsString(cell);

            assertThat(value).isEqualTo("2026-03-15");
            assertThat(value).doesNotContain(".");
        }
    }

    @Test
    @DisplayName("날짜 서식이 없는 일반 숫자 셀은 그대로 숫자로 추출된다")
    void plainNumericCellReturnsNumberString() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(45000);
            cell.setCellType(CellType.NUMERIC);

            String value = cellValueAsString(cell);

            assertThat(value).isEqualTo("45000");
        }
    }
}
