package app.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class AIClient {
    private final String endpoint = "http://127.0.0.1:8081/v1/chat/completions";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final ObjectMapper mapper = new ObjectMapper();

    public List<AnalysisResult> analyze(List<File> images, Map<Integer, String> columns) {
        List<CompletableFuture<AnalysisResult>> futures = new ArrayList<>();

        for (File image : images) {
            CompletableFuture<AnalysisResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    byte[] imagesBytes = Files.readAllBytes(image.toPath());
                    String base64 = Base64.getEncoder().encodeToString(imagesBytes);
                    String jsonString = send(createJsonBody(base64, columns));
                    return new AnalysisResult(image.getName(), parseResponse(jsonString, columns));
                } catch (Exception e) {
                    System.err.println("Ошибка обработки файла " + image.getName() + ": " + e.getMessage());
                    return new AnalysisResult(image.getName(), "Error: " + e.getMessage());
                }
            }, executor);
            futures.add(future);
        }
        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    private Map<Integer, String> parseResponse(String jsonString, Map<Integer, String> columns) {
        Map<Integer, String> result = new HashMap<>();
        if (jsonString == null || jsonString.isEmpty()) return result;

        String cleanJson = jsonString.trim();
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.replace("```json", "").replace("```", "").trim();
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.replace("```", "").trim();
        }

        if (!cleanJson.startsWith("{")) {
            System.err.println("AI отказался работать. Ответ: " + cleanJson);
            return new HashMap<>();
        }

        try {
            Map<String, String> rawMap = mapper.readValue(jsonString, new TypeReference<>() {
            });

            for (Map.Entry<Integer, String> entry : columns.entrySet()) {
                Integer realIndex = entry.getKey();
                String colName = entry.getValue();

                if (rawMap.containsKey(colName)) {
                    String val = rawMap.get(colName);
                    if (val != null) {
                        result.put(realIndex, val);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка парсинга JSON от AI: " + e.getMessage() + "\nОтвет был: " + jsonString);
        }
        return result;
    }

    private String send(String jsonBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return extractContent(response.body());
        } else {
            return "Error " + response.statusCode();
        }
    }


    private String createJsonBody(String imageBase64, Map<Integer, String> columns) {
        String structurePrompt = columns.values().stream()
                .map(name -> String.format("\"%s\": \"<значение>\"", name))
                .collect(Collectors.joining(",\n"));

        return String.format(
                "{" +
                        "\"messages\": [" +
                        "{" +
                        "\"role\": \"system\"," +
                        "\"content\": \"Ты — строгий JSON-генератор. Твоя единственная задача — извлечь данные из картинки.\" " +
                        "}," +
                        "{" +
                        "\"role\": \"user\"," +
                        "\"content\": [" +
                        "{\"type\": \"text\", \"text\": \"%s\"}," +
                        "{\"type\": \"image_url\", \"image_url\": {\"url\": \"data:image/jpeg;base64,%s\"}}" +
                        "]" +
                        "}" +
                        "]," +
                        "\"temperature\": 0.1," +
                        "\"max_tokens\": 500" +
                        "}",
                escapeJson("" +
                        "Проанализируй изображение. Верни ТОЛЬКО валидный JSON.\n" +
                        "ЗАПРЕЩЕНО: писать вступления, извиняться, использовать Markdown (```json).\n" +
                        "Только сырой JSON. Если данные не найдены — ставь пустую строку.\n" +
                        "Структура ответа ОБЯЗАНА быть такой:\n" +
                        "{\n" + structurePrompt + "\n}\n"),
                imageBase64
        );
    }

    private String escapeJson(String text) {
        return text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private String extractContent(String fullJsonResponse) {
        try {
            JsonNode root = mapper.readTree(fullJsonResponse);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            System.err.println("CRITICAL: Не удалось найти content в ответе: " + fullJsonResponse);
            return "";
        }
    }
}
