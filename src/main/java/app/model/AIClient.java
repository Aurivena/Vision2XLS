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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ObjectMapper mapper = new ObjectMapper();

    public List<AnalysisResult> analyze(List<File> files, Map<Integer, String> columns) {
        List<CompletableFuture<AnalysisResult>> futures = new ArrayList<>();

        for (File image : files) {
            CompletableFuture<AnalysisResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    File tmpFile = ImagePreprocessor.convertPdfToImage(image);
                    byte[] imagesBytes = Files.readAllBytes(tmpFile.toPath());
                    String base64 = Base64.getEncoder().encodeToString(imagesBytes);
                    String jsonString = send(createJsonBody(base64, columns));
                    return new AnalysisResult(tmpFile.getName(), parseResponse(jsonString, columns));
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

        String cleanJson = cleanJson(jsonString);

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
            System.out.println(response.body());
            return extractContent(response.body());
        } else {
            return "Error " + response.statusCode();
        }
    }

    private String cleanJson(String response) {
        if (response == null) return "{}";

        // Убираем маркдаун обертки
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    private String createJsonBody(String imageBase64, Map<Integer, String> columns) {
        // Шаблон JSON
        String fieldsJson = columns.values().stream()
                .map(name -> "\"" + name + "\": \"\"")
                .collect(Collectors.joining(",\n    "));

        String systemInstruction =
                "Ты — строгий парсер сервисных актов. Заполни JSON по зонам видимости.\n" +
                        "ПРАВИЛА ПО ПОЛЯМ:\n\n" +

                        "1. ПОЛЕ 'Акт проведения работ' (ИЩИ НОМЕР):\n" +
                        "   - СМОТРИ ТОЛЬКО В ВЕРХНИЙ ПРАВЫЙ УГОЛ ЛИСТА.\n" +
                        "   - Тебе нужно найти номер акта (например: '13.1', 'К-13.1', '№5').\n" +
                        "   - Если номера нет, перепиши заголовок ('Акт проведения ТО-1').\n" +
                        "   - ЗАПРЕЩЕНО писать сюда про замечания или дефекты!\n\n" +

                        "2. ПОЛЕ 'Произведённые работы' (ИЩИ ИТОГ):\n" +
                        "   - СМОТРИ ТОЛЬКО В САМЫЙ НИЗ (подвал листа).\n" +
                        "   - ПРАВИЛО ПРИОРИТЕТА: Если ты видишь рукописный текст, НО он перечеркнут (линией, Z, крестом) -> ЭТО ЗНАЧИТ ПУСТО.\n" +
                        "   - ЛОГИКА:\n" +
                        "       A) Перечеркнуто-> Пиши: \"Проведено ТО-1. Замечаний нет.\"\n" +
                        "       B) Абсолютно пусто -> Пиши: \"Проведено ТО-1. Замечаний нет.\"\n" +
                        "       C) Есть текст -> Пиши: \"Проведено ТО-1. Замечания: <тут текст>\"\n\n" +

                        "3. ПОЛЕ 'Исполнитель':\n" +
                        "   - СМОТРИ ВЛЕВО ВВЕРХ. Читай фамилию буквально (Иванищев != Иванов).\n\n" +

                        "ФОРМАТ:\n" +
                        "Верни ОДИН JSON объект. Значения — только строки.";

        String userPrompt =
                "Заполни этот JSON данными из картинки:\n" +
                        "{\n    " + fieldsJson + "\n}";

        return String.format(
                "{" +
                        "\"messages\": [" +
                        "{" +
                        "\"role\": \"system\"," +
                        "\"content\": \"%s\" " +
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
                        "\"max_tokens\": 1024" +
                        "}",
                escapeJson(systemInstruction),
                escapeJson(userPrompt),
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
