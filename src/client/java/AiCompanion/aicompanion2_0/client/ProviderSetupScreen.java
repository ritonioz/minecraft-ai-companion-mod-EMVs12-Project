package AiCompanion.aicompanion2_0.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ProviderSetupScreen extends Screen {

    private boolean advancedExpanded = false;

    private enum FetchState { IDLE, LOADING, SUCCESS, ERROR }
    private FetchState fetchState = FetchState.IDLE;
    private List<String> fetchedModels = new ArrayList<>();
    private String fetchError = null;
    private int modelScrollOffset = 0;

    // Values preserved across reinit() calls
    private String savedUrl   = "http://localhost:11434";
    private String savedKey   = "";
    private String savedModel = "";
    private String savedPath  = "";
    private String savedMode  = ClientConfig.getMode();

    private TextFieldWidget urlField;
    private TextFieldWidget keyField;
    private TextFieldWidget modelField;
    private TextFieldWidget pathField;

    private final Runnable onSuccess;

    private static final int MAX_VISIBLE = 3;
    private static final int ITEM_H = 14;

    // Layout Y offsets relative to baseY (= height/2 - 100)
    private static final int Y_PRESETS    = 20;  // preset buttons
    private static final int Y_URL_FIELD  = 50;  // URL text field
    private static final int Y_KEY_FIELD  = 85;  // key text field
    private static final int Y_MODEL_ROW  = 110; // "Model:" label + fetch button
    private static final int Y_MODEL_CONT = 123; // model list / text field

    public ProviderSetupScreen(Runnable onSuccess) {
        super(Text.literal("AI Companion Setup"));
        this.onSuccess = onSuccess;
    }

    @Override
    protected void init() {
        urlField = null;
        keyField = null;
        modelField = null;
        pathField = null;

        int cx = width / 2;
        int baseY = height / 2 - 100;

        // --- Preset buttons (pre-fill URL) ---
        addDrawableChild(ButtonWidget.builder(Text.literal("Ollama"), btn -> {
            saveCurrentValues();
            savedUrl = "http://localhost:11434";
            reinit();
        }).dimensions(cx - 155, baseY + Y_PRESETS, 95, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Open-WebUI"), btn -> {
            saveCurrentValues();
            savedUrl = "https://ai.example.org";
            savedPath = "/api/chat/completions";
            advancedExpanded = true;
            reinit();
        }).dimensions(cx - 50, baseY + Y_PRESETS, 95, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Custom"), btn -> {
            saveCurrentValues();
            savedUrl = "";
            reinit();
        }).dimensions(cx + 55, baseY + Y_PRESETS, 95, 14).build());

        // --- URL field ---
        urlField = new TextFieldWidget(textRenderer, cx - 150, baseY + Y_URL_FIELD, 300, 20, Text.literal("http://..."));
        urlField.setMaxLength(200);
        urlField.setText(savedUrl);
        addDrawableChild(urlField);

        // --- API key field (password-masked, optional) ---
        keyField = new TextFieldWidget(textRenderer, cx - 150, baseY + Y_KEY_FIELD, 300, 20, Text.literal("API Key (optional)"));
        keyField.setMaxLength(256);
        keyField.setText(savedKey);
        keyField.setRenderTextProvider((text, offset) ->
            OrderedText.styledForwardsVisitedString("•".repeat(text.length()), Style.EMPTY));
        addDrawableChild(keyField);

        // --- Fetch button (right side of model row) ---
        String fetchLabel = switch (fetchState) {
            case LOADING -> "§7Fetching...";
            case SUCCESS -> "↻ Refetch";
            case ERROR   -> "↻ Retry";
            default      -> "Fetch Models";
        };
        addDrawableChild(ButtonWidget.builder(Text.literal(fetchLabel), btn -> {
            if (fetchState != FetchState.LOADING) {
                saveCurrentValues();
                startFetch();
            }
        }).dimensions(cx + 5, baseY + Y_MODEL_ROW, 145, 12).build());

        // --- Model area ---
        boolean hasList = fetchState == FetchState.SUCCESS && !fetchedModels.isEmpty();
        if (hasList) {
            int visible = Math.min(fetchedModels.size(), MAX_VISIBLE);
            int end = Math.min(fetchedModels.size(), modelScrollOffset + visible);
            for (int i = modelScrollOffset; i < end; i++) {
                String m = fetchedModels.get(i);
                boolean selected = m.equals(savedModel);
                final String model = m;
                addDrawableChild(ButtonWidget.builder(
                    Text.literal((selected ? "§a▶ " : "  ") + m),
                    btn -> { savedModel = model; reinit(); }
                ).dimensions(cx - 150, baseY + Y_MODEL_CONT + (i - modelScrollOffset) * ITEM_H, 290, ITEM_H - 2).build());
            }
            if (fetchedModels.size() > MAX_VISIBLE) {
                addDrawableChild(ButtonWidget.builder(Text.literal("▲"),
                    btn -> { modelScrollOffset = Math.max(0, modelScrollOffset - 1); reinit(); }
                ).dimensions(cx + 143, baseY + Y_MODEL_CONT, 7, ITEM_H - 2).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("▼"),
                    btn -> { modelScrollOffset = Math.min(fetchedModels.size() - MAX_VISIBLE, modelScrollOffset + 1); reinit(); }
                ).dimensions(cx + 143, baseY + Y_MODEL_CONT + ITEM_H, 7, ITEM_H - 2).build());
            }
        } else {
            modelField = new TextFieldWidget(textRenderer, cx - 150, baseY + Y_MODEL_CONT, 300, 20, Text.literal("Model name..."));
            modelField.setMaxLength(100);
            modelField.setText(savedModel);
            addDrawableChild(modelField);
        }

        int listH = hasList ? Math.min(fetchedModels.size(), MAX_VISIBLE) * ITEM_H : 22;
        int afterModelY = baseY + Y_MODEL_CONT + listH + 4;

        // --- Advanced toggle ---
        String advLabel = advancedExpanded ? "Advanced ▲" : "Advanced ▼";
        addDrawableChild(ButtonWidget.builder(Text.literal("§8" + advLabel), btn -> {
            saveCurrentValues();
            advancedExpanded = !advancedExpanded;
            reinit();
        }).dimensions(cx + 55, afterModelY, 95, 12).build());

        // --- Advanced: API path ---
        int saveY;
        if (advancedExpanded) {
            pathField = new TextFieldWidget(textRenderer, cx - 150, afterModelY + 18, 300, 20, Text.literal("/api/chat/completions"));
            pathField.setMaxLength(200);
            pathField.setText(savedPath);
            addDrawableChild(pathField);
            saveY = afterModelY + 45;
        } else {
            saveY = afterModelY + 18;
        }

        // --- Mode selector ---
        boolean casualSelected   = !"minecraft".equals(savedMode);
        boolean mcSelected       = "minecraft".equals(savedMode);
        addDrawableChild(ButtonWidget.builder(
            Text.literal(casualSelected ? "§a▶ Casual" : "  Casual"),
            btn -> { saveCurrentValues(); savedMode = "casual"; reinit(); }
        ).dimensions(cx - 155, saveY, 95, 12).build());
        addDrawableChild(ButtonWidget.builder(
            Text.literal(mcSelected ? "§a▶ Minecraft" : "  Minecraft"),
            btn -> { saveCurrentValues(); savedMode = "minecraft"; reinit(); }
        ).dimensions(cx - 50, saveY, 100, 12).build());

        // --- Save button ---
        addDrawableChild(ButtonWidget.builder(Text.literal("Save & Connect"), btn -> confirm())
            .dimensions(cx - 75, saveY + 18, 150, 20).build());

        urlField.setFocused(true);
    }

    private void saveCurrentValues() {
        if (urlField   != null) savedUrl   = urlField.getText();
        if (keyField   != null) savedKey   = keyField.getText();
        if (modelField != null) savedModel = modelField.getText();
        if (pathField  != null) savedPath  = pathField.getText();
    }

    private void reinit() {
        MinecraftClient mc = MinecraftClient.getInstance();
        this.init(mc, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
    }

    private void confirm() {
        saveCurrentValues();
        String url   = savedUrl.trim();
        String model = savedModel.trim();
        String key   = savedKey.trim();
        String path  = savedPath.trim();
        if (url.isEmpty() || model.isEmpty()) return;
        ClientConfig.setProviderConfig(url, model, key.isEmpty() ? null : key, path.isEmpty() ? null : path, savedMode);
        MinecraftClient.getInstance().execute(() -> {
            close();
            onSuccess.run();
        });
    }

    // -------------------------------------------------------------------------
    // Model fetching
    // -------------------------------------------------------------------------

    private void startFetch() {
        fetchState = FetchState.LOADING;
        fetchError = null;
        String url = savedUrl.trim().isEmpty() ? "http://localhost:11434" : savedUrl.trim();
        String key = savedKey.trim();
        reinit();

        new Thread(() -> {
            try {
                List<String> models = fetchModels(url, key);
                if (models.isEmpty()) {
                    fetchState = FetchState.ERROR;
                    fetchError = "No models found";
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

    private List<String> fetchModels(String baseUrl, String apiKey) throws Exception {
        // Ollama native: GET /api/tags → {"models":[{"name":"..."}]}
        try {
            List<String> m = fetchFromPath(baseUrl, "/api/tags", apiKey, "name");
            if (!m.isEmpty()) return m;
        } catch (Exception ignored) {}
        // OpenAI-compat: GET /v1/models → {"data":[{"id":"..."}]}
        try {
            List<String> m = fetchFromPath(baseUrl, "/v1/models", apiKey, "id");
            if (!m.isEmpty()) return m;
        } catch (Exception ignored) {}
        // Open-WebUI native: GET /api/models
        return fetchFromPath(baseUrl, "/api/models", apiKey, "id");
    }

    private List<String> fetchFromPath(String baseUrl, String path, String apiKey, String field) throws Exception {
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

        // Robust parser: handles both `"field":"value"` and `"field": "value"`
        List<String> result = new ArrayList<>();
        String body = sb.toString();
        String key = "\"" + field + "\"";
        int idx = 0;
        while ((idx = body.indexOf(key, idx)) >= 0) {
            idx += key.length();
            // skip whitespace and colon
            while (idx < body.length() && (body.charAt(idx) == ' ' || body.charAt(idx) == '\t' || body.charAt(idx) == ':')) idx++;
            if (idx < body.length() && body.charAt(idx) == '"') {
                int start = idx + 1;
                int end = body.indexOf('"', start);
                if (end > start) result.add(body.substring(start, end));
                idx = end + 1;
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int cx = width / 2;
        int baseY = height / 2 - 100;

        context.drawCenteredTextWithShadow(textRenderer, "§6AI Companion Setup", cx, baseY - 5, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, "§8Quick presets:", cx, baseY + 8, 0x888888);

        context.drawTextWithShadow(textRenderer, "§7Server URL:", cx - 150, baseY + 38, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, "§7API Key §8(optional):", cx - 150, baseY + 73, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, "§7Model:", cx - 150, baseY + Y_MODEL_ROW, 0xAAAAAA);

        if (fetchState == FetchState.LOADING) {
            context.drawTextWithShadow(textRenderer, "§7Fetching...", cx - 150, baseY + Y_MODEL_CONT + 4, 0x888888);
        } else if (fetchState == FetchState.ERROR && fetchError != null) {
            context.drawTextWithShadow(textRenderer, "§c" + fetchError, cx - 150, baseY + Y_MODEL_CONT + 4, 0xFF5555);
        }

        {
            boolean hasList = fetchState == FetchState.SUCCESS && !fetchedModels.isEmpty();
            int listH = hasList ? Math.min(fetchedModels.size(), MAX_VISIBLE) * ITEM_H : 22;
            int afterModelY = baseY + Y_MODEL_CONT + listH + 4;
            if (advancedExpanded) {
                context.drawTextWithShadow(textRenderer, "§7API Path:", cx - 150, afterModelY + 8, 0xAAAAAA);
            }
            int modeY = advancedExpanded ? afterModelY + 45 : afterModelY + 18;
            context.drawTextWithShadow(textRenderer, "§7Mode:", cx - 155, modeY + 2, 0xAAAAAA);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (fetchState == FetchState.SUCCESS && fetchedModels.size() > MAX_VISIBLE) {
            modelScrollOffset = Math.max(0, Math.min(
                fetchedModels.size() - MAX_VISIBLE,
                modelScrollOffset - (int) Math.signum(amount)
            ));
            reinit();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean shouldPause() { return false; }
}
