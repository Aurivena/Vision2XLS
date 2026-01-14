package app.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class AIPreprocessor {
    private Process serverProcess;
    private final String host = "127.0.0.1";
    private final String port = "8081";

    public void init() throws IOException {
        int totalCores = Runtime.getRuntime().availableProcessors();
        int safeThreads = Math.max(1, totalCores - 1);
        safeThreads = Math.min(safeThreads, 8);

        ProcessBuilder pb = getProcessBuilder(safeThreads);

        serverProcess = pb.start();

        CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serverProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[llama-server] " + line);
                    if (line.contains("HTTP server started")) {
                        System.out.println("LLM server is ready!");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        if (!waitForServerReady()) {
            throw new RuntimeException("LLM server failed to start in time");
        }
    }

    private ProcessBuilder getProcessBuilder(int threads) throws IOException {
        File binDir = new File("bin");
        File binaryFile = new File(binDir, getServerBinary());

        String modelName = "Qwen2-VL-2B-Instruct-Q4_K_M.gguf";
        String projectorName = "mmproj-Qwen2-VL-2B-Instruct-f16.gguf";

        if (!binaryFile.exists()) {
            throw new IOException("Binary not found at: " + binaryFile.getAbsolutePath());
        }

        ProcessBuilder pb = new ProcessBuilder(
                binaryFile.getAbsolutePath(),
                "--model", modelName,
                "--mmproj", projectorName,
                "--host", host,
                "--port", port,
                "--threads", String.valueOf(threads),
                "--n-gpu-layers", "0"
        );

        pb.directory(binDir);
        pb.redirectErrorStream(true);
        return pb;
    }

    private String getServerBinary() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "llama-server.exe";
        } else {
            return "llama-server";
        }
    }

    private boolean waitForServerReady() {
        int maxSecond = 120;
        for (int i = 0; i < maxSecond; i++) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(host + ":" + port + "/health").openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);

                int code = conn.getResponseCode();
                if (code == 200) return true;
            } catch (Exception ignored) {
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }

        }

        return false;
    }


    public void shutdown() {
        if (serverProcess != null) {
            serverProcess.destroyForcibly();
        }
    }
}

