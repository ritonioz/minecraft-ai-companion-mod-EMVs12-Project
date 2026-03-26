package AiCompanion.aicompanion2_0.client;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AiChatSession {

    private final String apiBaseUrl;
    private final String apiKey;
    private final String model;
    private final String apiPath; // null = auto-detect

    // OpenAI-format message history: alternating user/assistant
    private final List<String[]> history = new ArrayList<>(); // [role, content]
    private final List<String> displayLines = new ArrayList<>();

    public AiChatSession(String apiBaseUrl, String apiKey, String model, String apiPath) {
        this.apiBaseUrl = apiBaseUrl;
        this.apiKey = apiKey;
        this.model = model;
        displayLines.add("§7Start a conversation with your AI companion!");
    }

    public List<String> getDisplayLines() {
        return displayLines;
    }

    public void sendMessage(String userMessage, Consumer<String> onResponse) {
        history.add(new String[]{"user", userMessage});

        if (AiCompanionClient.tryTriggerArchEasterEgg(userMessage, response -> {
            history.add(new String[]{"assistant", response});
            onResponse.accept(response);
        })) {
            return;
        }

        new Thread(() -> {
            try {
                String response = callOpenWebUI();
                history.add(new String[]{"assistant", response});
                onResponse.accept(response);
            } catch (Exception e) {
                onResponse.accept("§cError: " + e.getMessage());
            }
        }).start();
    }

    private String callOpenWebUI() throws Exception {
        // Build messages array
        StringBuilder messages = new StringBuilder("[");
        for (int i = 0; i < history.size(); i++) {
            String[] msg = history.get(i);
            messages.append("{\"role\":\"").append(msg[0])
                    .append("\",\"content\":\"").append(jsonEscape(msg[1]))
                    .append("\"}");
            if (i < history.size() - 1) messages.append(",");
        }
        messages.append("]");

        String json = "{\"model\":\"" + jsonEscape(model) + "\",\"messages\":" + messages + ",\"stream\":false}";

        if (apiPath != null) {
            // User configured a specific path — use it directly, no fallback
            HttpResult result = postChatCompletion(apiPath, json);
            if (result.status == 200) return extractAssistantContent(result.body);
            return formatHttpError(result.status, result.body, apiPath);
        }

        // Auto-detect: try /api/chat/completions, fall back to /v1/chat/completions
        HttpResult primary = postChatCompletion("/api/chat/completions", json);
        if (primary.status == 200) {
            return extractAssistantContent(primary.body);
        }

        if (primary.status >= 500 || primary.status == 404) {
            HttpResult fallback = postChatCompletion("/v1/chat/completions", json);
            if (fallback.status == 200) {
                return extractAssistantContent(fallback.body);
            }
            return formatHttpError(fallback.status, fallback.body, "/v1/chat/completions");
        }

        return formatHttpError(primary.status, primary.body, "/api/chat/completions");
    }

    private HttpResult postChatCompletion(String path, String json) throws Exception {
        URL url = new URL(apiBaseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (apiKey != null && !apiKey.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes("utf-8"));
        }

        int status = conn.getResponseCode();
        String body = readResponseBody(conn, status >= 400);
        conn.disconnect();
        return new HttpResult(status, body);
    }

    private String readResponseBody(HttpURLConnection conn, boolean error) throws IOException {
        var stream = error ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) return "";

        StringBuilder resp = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, "utf-8"))) {
            String line;
            while ((line = br.readLine()) != null) resp.append(line);
        }
        return resp.toString();
    }

    private String extractAssistantContent(String body) {
        int idx = body.indexOf("\"content\":\"");
        if (idx >= 0) {
            int start = idx + 11;
            int end = body.indexOf("\"", start);
            while (end > 0 && body.charAt(end - 1) == '\\') {
                end = body.indexOf("\"", end + 1);
            }
            return body.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
        }
        return body;
    }

    private String formatHttpError(int status, String body, String path) {
        if (body == null || body.isBlank()) {
            return "Error: HTTP " + status + " (" + path + ")";
        }
        return "Error: HTTP " + status + " (" + path + ") - " + body;
    }

    private String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static class HttpResult {
        private final int status;
        private final String body;

        private HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
