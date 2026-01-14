package app.model;

import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExcelService {

    public List<String> getColumnsName(File file) throws IOException {
        List<String> result = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow != null) {
                DataFormatter formatter = new DataFormatter();

                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    String text = (cell == null) ? "" : formatter.formatCellValue(cell);
                    result.add(text);
                }
            }
        }
        return result;
    }

    public void appendRow(File file, Map<Integer, String> columnData) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);

            int lastRowIndex = sheet.getLastRowNum();

            if (sheet.getPhysicalNumberOfRows() > 0) {
                lastRowIndex++;
            }

            Row row = sheet.createRow(lastRowIndex);

            for (Map.Entry<Integer, String> entry : columnData.entrySet()) {
                Cell cell = row.createCell(entry.getKey());
                cell.setCellValue(entry.getValue());
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }
    }
}
