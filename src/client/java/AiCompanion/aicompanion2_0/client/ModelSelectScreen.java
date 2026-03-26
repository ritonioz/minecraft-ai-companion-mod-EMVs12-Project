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

public class ModelSelectScreen extends Screen {

    private enum FetchState { IDLE, LOADING, SUCCESS, ERROR }
    private FetchState fetchState = FetchState.IDLE;
    private List<String> fetchedModels = new ArrayList<>();
    private String fetchError = null;
    private int scrollOffset = 0;

    private String savedModel;
    private String savedMode;
    private TextFieldWidget modelField;

    private static final int MAX_VISIBLE = 5;
    private static final int ITEM_H = 16;

    public ModelSelectScreen() {
        super(Text.literal("Change Model"));
        this.savedModel = ClientConfig.getModel() != null ? ClientConfig.getModel() : "";
        this.savedMode  = ClientConfig.getMode();
    }

    @Override
    protected void init() {
        modelField = null;

        int cx = width / 2;
        int cy = height / 2;

        // Fetch button
        String fetchLabel = switch (fetchState) {
            case LOADING -> "§7Fetching...";
            case SUCCESS -> "↻ Refetch";
            case ERROR   -> "↻ Retry";
            default      -> "Fetch Models";
        };
        addDrawableChild(ButtonWidget.builder(Text.literal(fetchLabel), btn -> {
            if (fetchState != FetchState.LOADING) startFetch();
        }).dimensions(cx - 75, cy - 55, 150, 20).build());

        // Model list or manual text field
        boolean hasList = fetchState == FetchState.SUCCESS && !fetchedModels.isEmpty();
        int listStartY = cy - 28;

        if (hasList) {
            int visible = Math.min(fetchedModels.size(), MAX_VISIBLE);
            int end = Math.min(fetchedModels.size(), scrollOffset + visible);
            for (int i = scrollOffset; i < end; i++) {
                String m = fetchedModels.get(i);
                boolean selected = m.equals(savedModel);
                final String model = m;
                addDrawableChild(ButtonWidget.builder(
                    Text.literal((selected ? "§a▶ " : "  ") + m),
                    btn -> { savedModel = model; reinit(); }
                ).dimensions(cx - 150, listStartY + (i - scrollOffset) * ITEM_H, 290, ITEM_H - 2).build());
            }
            if (fetchedModels.size() > MAX_VISIBLE) {
                addDrawableChild(ButtonWidget.builder(Text.literal("▲"),
                    btn -> { scrollOffset = Math.max(0, scrollOffset - 1); reinit(); }
                ).dimensions(cx + 143, listStartY, 7, ITEM_H - 2).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("▼"),
                    btn -> { scrollOffset = Math.min(fetchedModels.size() - MAX_VISIBLE, scrollOffset + 1); reinit(); }
                ).dimensions(cx + 143, listStartY + ITEM_H, 7, ITEM_H - 2).build());
            }
        } else {
            modelField = new TextFieldWidget(textRenderer, cx - 150, listStartY, 300, 20, Text.literal("Model name..."));
            modelField.setMaxLength(100);
            modelField.setText(savedModel);
            modelField.setFocused(true);
            addDrawableChild(modelField);
        }

        int listH = hasList ? Math.min(fetchedModels.size(), MAX_VISIBLE) * ITEM_H : 22;
        int modeY = listStartY + listH + 8;

        boolean casualSelected = !"minecraft".equals(savedMode);
        boolean mcSelected     = "minecraft".equals(savedMode);
        addDrawableChild(ButtonWidget.builder(
            Text.literal(casualSelected ? "§a▶ Casual" : "  Casual"),
            btn -> { savedMode = "casual"; reinit(); }
        ).dimensions(cx - 110, modeY, 100, 12).build());
        addDrawableChild(ButtonWidget.builder(
            Text.literal(mcSelected ? "§a▶ Minecraft" : "  Minecraft"),
            btn -> { savedMode = "minecraft"; reinit(); }
        ).dimensions(cx - 5, modeY, 110, 12).build());

        int saveY = modeY + 18;
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), btn -> confirm())
            .dimensions(cx - 55, saveY, 110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> close())
            .dimensions(cx - 55, saveY + 24, 110, 20).build());
    }

    private void reinit() {
        if (modelField != null) savedModel = modelField.getText();
        MinecraftClient mc = MinecraftClient.getInstance();
        this.init(mc, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
    }

    private void confirm() {
        if (modelField != null) savedModel = modelField.getText();
        String model = savedModel.trim();
        if (model.isEmpty()) return;
        ClientConfig.setProviderConfig(
            ClientConfig.getBaseUrl(),
            model,
            ClientConfig.getApiKey(),
            ClientConfig.getApiPath(),
            savedMode
        );
        close();
    }

    // -------------------------------------------------------------------------
    // Fetching
    // -------------------------------------------------------------------------

    private void startFetch() {
        if (modelField != null) savedModel = modelField.getText();
        fetchState = FetchState.LOADING;
        fetchError = null;
        reinit();

        String url = ClientConfig.getBaseUrl();
        String key = ClientConfig.getApiKey();

        new Thread(() -> {
            try {
                List<String> models = fetchModels(url, key);
                if (models.isEmpty()) {
                    fetchState = FetchState.ERROR;
                    fetchError = "No models found";
                } else {
                    fetchedModels = models;
                    fetchState = FetchState.SUCCESS;
                    scrollOffset = 0;
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
        try {
            List<String> m = fetchFromPath(baseUrl, "/api/tags", apiKey, "name");
            if (!m.isEmpty()) return m;
        } catch (Exception ignored) {}
        try {
            List<String> m = fetchFromPath(baseUrl, "/v1/models", apiKey, "id");
            if (!m.isEmpty()) return m;
        } catch (Exception ignored) {}
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

        List<String> result = new ArrayList<>();
        String key = "\"" + field + "\"";
        String body = sb.toString();
        int idx = 0;
        while ((idx = body.indexOf(key, idx)) >= 0) {
            idx += key.length();
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
        int cy = height / 2;

        context.drawCenteredTextWithShadow(textRenderer, "§6Change Model", cx, cy - 75, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            "§8Current: §7" + (ClientConfig.getModel() != null ? ClientConfig.getModel() : "none"),
            cx, cy - 63, 0x888888);
        context.drawTextWithShadow(textRenderer, "§7Model:", cx - 150, cy - 30, 0xAAAAAA);

        int listH = (fetchState == FetchState.SUCCESS && !fetchedModels.isEmpty())
            ? Math.min(fetchedModels.size(), MAX_VISIBLE) * ITEM_H : 22;
        int modeY = (cy - 28) + listH + 8;
        context.drawTextWithShadow(textRenderer, "§7Mode:", cx - 110, modeY + 2, 0xAAAAAA);

        if (fetchState == FetchState.LOADING) {
            context.drawTextWithShadow(textRenderer, "§7Fetching...", cx - 150, cy - 14, 0x888888);
        } else if (fetchState == FetchState.ERROR && fetchError != null) {
            context.drawTextWithShadow(textRenderer, "§c" + fetchError, cx - 150, cy - 14, 0xFF5555);
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
            scrollOffset = Math.max(0, Math.min(
                fetchedModels.size() - MAX_VISIBLE,
                scrollOffset - (int) Math.signum(amount)
            ));
            reinit();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean shouldPause() { return false; }
}
