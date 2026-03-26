package AiCompanion.aicompanion2_0.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ProviderSetupScreen extends Screen {

    private boolean configStep = false;
    private boolean advancedExpanded = false;
    private String provider = "ollama";

    // Field values preserved across reinit() calls
    private String savedUrl = "";
    private String savedModel = "";
    private String savedKey = "";
    private String savedPath = "";

    // Model fetch state
    private enum FetchState { IDLE, LOADING, SUCCESS, ERROR }
    private FetchState fetchState = FetchState.IDLE;
    private List<String> fetchedModels = new ArrayList<>();
    private String fetchError = null;
    private int modelScrollOffset = 0;

    private TextFieldWidget urlField;
    private TextFieldWidget modelField;
    private TextFieldWidget keyField;
    private TextFieldWidget pathField;

    private final Runnable onSuccess;

    // Layout constants (relative to baseY)
    private static final int URL_FIELD_Y     = 10;
    private static final int KEY_LABEL_Y     = 37;
    private static final int KEY_FIELD_Y     = 47;
    private static final int MODEL_LABEL_Y   = 74;
    private static final int MODEL_CONTENT_Y = 86;
    private static final int ITEM_HEIGHT     = 14;
    private static final int MAX_VISIBLE     = 4;

    public ProviderSetupScreen(Runnable onSuccess) {
        super(Text.literal("AI Companion Setup"));
        this.onSuccess = onSuccess;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int baseY = height / 2 - 75;

        if (!configStep) {
            int cy = height / 2;
            addDrawableChild(ButtonWidget.builder(Text.literal("Ollama (Local)"), btn -> goToConfig("ollama"))
                .dimensions(cx - 155, cy, 100, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Open-WebUI"), btn -> goToConfig("openwebui"))
                .dimensions(cx - 50, cy, 100, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Custom"), btn -> goToConfig("custom"))
                .dimensions(cx + 55, cy, 100, 20).build());
            return;
        }

        // --- Config step ---

        // URL field
        urlField = new TextFieldWidget(textRenderer, cx - 150, baseY + URL_FIELD_Y, 300, 20, Text.literal("http://..."));
        urlField.setMaxLength(200);
        urlField.setText(savedUrl.isEmpty() ? defaultUrl() : savedUrl);
        addDrawableChild(urlField);

        // Key field
        keyField = new TextFieldWidget(textRenderer, cx - 150, baseY + KEY_FIELD_Y, 300, 20, Text.literal("API Key (optional)"));
        keyField.setMaxLength(200);
        keyField.setText(savedKey);
        addDrawableChild(keyField);

        // Fetch Models button (right side of model row header)
        String fetchLabel = switch (fetchState) {
            case LOADING -> "§7Fetching...";
            case SUCCESS -> "↻ Refetch";
            case ERROR   -> "↻ Retry";
            default      -> "Fetch Models";
        };
        addDrawableChild(ButtonWidget.builder(Text.literal(fetchLabel), btn -> {
            if (fetchState != FetchState.LOADING) {
                saveCurrentValues();
                fetchModels();
            }
        }).dimensions(cx + 5, baseY + MODEL_LABEL_Y, 145, 12).build());

        // Model content area: clickable list OR text field
        boolean hasModelList = fetchState == FetchState.SUCCESS && !fetchedModels.isEmpty();
        int listHeight;

        if (hasModelList) {
            int visibleCount = Math.min(fetchedModels.size(), MAX_VISIBLE);
            int endIdx = Math.min(fetchedModels.size(), modelScrollOffset + visibleCount);
            listHeight = visibleCount * ITEM_HEIGHT;

            for (int i = modelScrollOffset; i < endIdx; i++) {
                String m = fetchedModels.get(i);
                boolean selected = m.equals(savedModel);
                final String model = m;
                addDrawableChild(ButtonWidget.builder(
                    Text.literal((selected ? "§a▶ " : "  ") + m),
                    btn -> { savedModel = model; reinit(); }
                ).dimensions(cx - 150, baseY + MODEL_CONTENT_Y + (i - modelScrollOffset) * ITEM_HEIGHT, 290, ITEM_HEIGHT - 2).build());
            }

            // Up/down scroll when list is longer than visible
            if (fetchedModels.size() > MAX_VISIBLE) {
                addDrawableChild(ButtonWidget.builder(Text.literal("▲"),
                    btn -> { modelScrollOffset = Math.max(0, modelScrollOffset - 1); reinit(); }
                ).dimensions(cx + 143, baseY + MODEL_CONTENT_Y, 7, ITEM_HEIGHT - 2).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("▼"),
                    btn -> { modelScrollOffset = Math.min(fetchedModels.size() - MAX_VISIBLE, modelScrollOffset + 1); reinit(); }
                ).dimensions(cx + 143, baseY + MODEL_CONTENT_Y + ITEM_HEIGHT, 7, ITEM_HEIGHT - 2).build());
            }
        } else {
            // Manual text input fallback
            modelField = new TextFieldWidget(textRenderer, cx - 150, baseY + MODEL_CONTENT_Y, 300, 20, Text.literal("Model name..."));
            modelField.setMaxLength(100);
            modelField.setText(savedModel.isEmpty() ? defaultModel() : savedModel);
            addDrawableChild(modelField);
            listHeight = 22;
        }

        // Positions below model area
        int afterModelY = baseY + MODEL_CONTENT_Y + listHeight + 5;

        // Advanced toggle
        String advLabel = advancedExpanded ? "Advanced ▲" : "Advanced ▼";
        addDrawableChild(ButtonWidget.builder(Text.literal("§8" + advLabel), btn -> {
            saveCurrentValues();
            advancedExpanded = !advancedExpanded;
            reinit();
        }).dimensions(cx + 55, afterModelY, 95, 12).build());

        int buttonsY;
        if (advancedExpanded) {
            pathField = new TextFieldWidget(textRenderer, cx - 150, afterModelY + 18, 300, 20, Text.literal("/api/chat/completions"));
            pathField.setMaxLength(200);
            pathField.setText(savedPath.isEmpty() ? defaultPath() : savedPath);
            addDrawableChild(pathField);
            buttonsY = afterModelY + 45;
        } else {
            buttonsY = afterModelY + 18;
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("< Back"), btn -> {
            configStep = false;
            advancedExpanded = false;
            fetchState = FetchState.IDLE;
            fetchedModels = new ArrayList<>();
            savedUrl = savedModel = savedKey = savedPath = "";
            reinit();
        }).dimensions(cx - 155, buttonsY, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Confirm"), btn -> confirm())
            .dimensions(cx - 75, buttonsY, 150, 20).build());

        urlField.setFocused(true);
    }

    private void saveCurrentValues() {
        if (urlField   != null) savedUrl   = urlField.getText();
        if (modelField != null) savedModel = modelField.getText();
        if (keyField   != null) savedKey   = keyField.getText();
        if (pathField  != null) savedPath  = pathField.getText();
    }

    private void goToConfig(String p) {
        this.provider = p;
        this.configStep = true;
        reinit();
    }

    private void reinit() {
        MinecraftClient client = MinecraftClient.getInstance();
        this.init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
    }

    private String defaultUrl() {
        return switch (provider) {
            case "ollama"    -> "http://localhost:11434";
            case "openwebui" -> "https://ai.cametendo.org";
            default          -> "";
        };
    }

    private String defaultModel() {
        return switch (provider) {
            case "openwebui" -> "minecraft-helper";
            default          -> "";
        };
    }

    private String defaultPath() {
        return switch (provider) {
            case "ollama"    -> "/v1/chat/completions";
            case "openwebui" -> "/api/chat/completions";
            default          -> "";
        };
    }

    // --- Model fetching ---

    private void fetchModels() {
        fetchState = FetchState.LOADING;
        fetchError = null;
        String url = savedUrl.isEmpty() ? defaultUrl() : savedUrl;
        String key = savedKey;
        reinit();

        new Thread(() -> {
            try {
                List<String> models = doFetchModels(url, key);
                if (models.isEmpty()) {
                    fetchState = FetchState.ERROR;
                    fetchError = "No models found at " + url;
                } else {
                    fetchedModels = models;
                    fetchState = FetchState.SUCCESS;
                    modelScrollOffset = 0;
                    if (savedModel.isEmpty()) savedModel = models.get(0);
                }
            } catch (Exception e) {
                fetchState = FetchState.ERROR;
                fetchError = e.getMessage() != null ? e.getMessage() : "Connection failed";
            }
            MinecraftClient.getInstance().execute(this::reinit);
        }).start();
    }

    private List<String> doFetchModels(String baseUrl, String apiKey) throws Exception {
        // Try Ollama native: GET /api/tags → {"models": [{"name": "..."}]}
        try {
            List<String> models = fetchFromPath(baseUrl, "/api/tags", apiKey, "name");
            if (!models.isEmpty()) return models;
        } catch (Exception ignored) {}

        // Try OpenAI-compat: GET /v1/models → {"data": [{"id": "..."}]}
        return fetchFromPath(baseUrl, "/v1/models", apiKey, "id");
    }

    private List<String> fetchFromPath(String baseUrl, String path, String apiKey, String nameField) throws Exception {
        URL url = URI.create(baseUrl + path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (apiKey != null && !apiKey.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }

        int status = conn.getResponseCode();
        if (status != 200) throw new Exception("HTTP " + status + " from " + path);

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        conn.disconnect();

        // Simple field extraction: find all occurrences of "nameField":"value"
        List<String> result = new ArrayList<>();
        String search = "\"" + nameField + "\":\"";
        String body = sb.toString();
        int idx = 0;
        while ((idx = body.indexOf(search, idx)) >= 0) {
            int start = idx + search.length();
            int end = body.indexOf("\"", start);
            if (end > start) result.add(body.substring(start, end));
            idx = end + 1;
        }
        return result;
    }

    // --- Confirm ---

    private void confirm() {
        saveCurrentValues();
        String url   = savedUrl.trim();
        String model = savedModel.trim();
        String key   = savedKey.trim();
        String path  = advancedExpanded ? savedPath.trim() : "";
        if (url.isEmpty() || model.isEmpty()) return;
        ClientConfig.setProviderConfig(url, model, key.isEmpty() ? null : key, path.isEmpty() ? null : path);
        MinecraftClient.getInstance().execute(() -> {
            close();
            onSuccess.run();
        });
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (configStep && (keyCode == 257 || keyCode == 335)) { // Enter
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int cx = width / 2;
        int baseY = height / 2 - 75;

        if (!configStep) {
            int cy = height / 2;
            context.drawCenteredTextWithShadow(textRenderer, "§6AI Companion Setup", cx, cy - 30, 0xFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, "§fChoose your AI provider:", cx, cy - 15, 0xAAAAAA);
        } else {
            String title = switch (provider) {
                case "ollama"    -> "§6Configure Ollama";
                case "openwebui" -> "§6Configure Open-WebUI";
                default          -> "§6Custom Configuration";
            };
            context.drawCenteredTextWithShadow(textRenderer, title, cx, baseY - 5, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, "§7Server URL:", cx - 150, baseY, 0xAAAAAA);
            context.drawTextWithShadow(textRenderer, "§7API Key §8(optional):", cx - 150, baseY + KEY_LABEL_Y, 0xAAAAAA);
            context.drawTextWithShadow(textRenderer, "§7Model:", cx - 150, baseY + MODEL_LABEL_Y, 0xAAAAAA);

            // Fetch status / error below model label
            if (fetchState == FetchState.LOADING) {
                context.drawTextWithShadow(textRenderer, "§7Loading...", cx - 150, baseY + MODEL_CONTENT_Y + 4, 0xAAAAAA);
            } else if (fetchState == FetchState.ERROR && fetchError != null) {
                context.drawTextWithShadow(textRenderer, "§c" + fetchError, cx - 150, baseY + MODEL_CONTENT_Y + 4, 0xFF5555);
            }

            // Advanced path label
            if (advancedExpanded) {
                boolean hasModelList = fetchState == FetchState.SUCCESS && !fetchedModels.isEmpty();
                int listHeight = hasModelList ? Math.min(fetchedModels.size(), MAX_VISIBLE) * ITEM_HEIGHT : 22;
                int afterModelY = baseY + MODEL_CONTENT_Y + listHeight + 5;
                context.drawTextWithShadow(textRenderer, "§7API Path:", cx - 150, afterModelY + 8, 0xAAAAAA);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
