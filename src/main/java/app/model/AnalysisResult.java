package app.model;

import java.util.HashMap;
import java.util.Map;

public class AnalysisResult {
    private final String fileName;
    private final Map<Integer, String> data;
    private final String error;

    public AnalysisResult(String fileName, Map<Integer, String> data) {
        this.fileName = fileName;
        this.data = data;
        this.error = null;
    }

    public AnalysisResult(String fileName, String error) {
        this.fileName = fileName;
        this.data = new HashMap<>();
        this.error = error;
    }

    public Map<Integer, String> getData() {
        return data;
    }

    public String getError() {
        return error;
    }
}
