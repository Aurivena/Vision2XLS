package app.controller;

import app.model.AIClient;
import app.model.AnalysisResult;
import app.model.ExcelService;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Handler {
    private final AIClient aiClient;
    private final ExcelService excelService;

    public Handler(AIClient aiClient, ExcelService excelService) {
        this.aiClient = aiClient;
        this.excelService = excelService;
    }

    public void start(File excelFile, List<File> images, Map<Integer, String> columns) throws IOException {
        List<AnalysisResult> analyze = aiClient.analyze(images, columns);
        for (AnalysisResult an : analyze) {
            if (an.getError() != null) {
                continue;
            }
            excelService.appendRow(excelFile, an.getData());
        }
    }
}
