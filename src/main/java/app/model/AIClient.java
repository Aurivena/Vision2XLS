package app.model;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class AIClient {
    private final String endpoint = "http://127.0.0.1:8081/v1/chat/completions";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ExecutorService executor = Executors.newFixedThreadPool(1);

    public List<String> analyze(List<File> images, String[] columns) throws IOException {
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (File image : images) {
            byte[] imagesBytes = Files.readAllBytes(image.toPath());

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String base64 = Base64.getEncoder().encodeToString(imagesBytes);
                    return send(createJsonBody(base64, columns));
                } catch (Exception e) {
                    System.err.println("Ошибка обработки файла " + image.getName() + ": " + e.getMessage());
                    return "Error" + image.getName();
                }
            }, executor);
            futures.add(future);
        }
        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
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


    private String createJsonBody(String image, String[] columns) {
        String columnsJsonStructure = Arrays.stream(columns)
                .map(col -> "\"" + col + "\": \"значение\"")
                .collect(Collectors.joining(",\n"));

        return String.format(
                "{" +
                        "\"messages\": [" +
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
                        "Ты — экспертная система OCR и анализа технической документации. \n" +
                        "Твоя задача — извлечь данные из предоставленного изображения.\n" +
                        "Текст может быть рукописным. Будь внимателен к цифрам, датам и галочкам. Если видишь КРЕСТ, то ставь null" +
                        "Проанализируй изображение и верни ТОЛЬКО валидный JSON без Markdown-разметки (```json ... ```).\n" +
                        "Структура JSON должна быть такой:\n" +
                        "{\n" +
                        columnsJsonStructure +
                        "}\n" +
                        "\n" +
                        "Если поле не найдено или неразборчиво, ставь null."),
                image
        );
    }

    private String escapeJson(String text) {
        return text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private String extractContent(String json) {
        String marker = "\"content\": \"";
        int start = json.indexOf(marker);
        if (start == -1) return json;
        int end = json.indexOf("\"", start + marker.length());
        return json.substring(start + marker.length(), end).replace("\\n", "\n");
    }

    public void shutdown() {
        executor.shutdown();
    }
}
