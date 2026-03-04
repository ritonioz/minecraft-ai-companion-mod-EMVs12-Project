package AiCompanion.aicompanion2_0;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Properties;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class Aicompanion2_0 implements ModInitializer {

    public static final String MOD_ID = "aicompanion2_0";
    private static String API_BASE_URL = "https://ai.cametendo.org";
    private static String MODEL = "minecraft-helper";
    private static String API_KEY = "";
    private static String API_PATH = "/api/chat/completions";

    public static final EntityType<AIEntity> AI_COMPANION = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MOD_ID, "ai_companion"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, AIEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                    .build()
    );

    // Static getters used by the client side
    public static String getApiBaseUrl() { return API_BASE_URL; }
    public static String getModel() { return MODEL; }

    @Override
    public void onInitialize() {
        loadConfig();
        System.out.println("[" + MOD_ID + "] MOD STARTET!");

        FabricDefaultAttributeRegistry.register(AI_COMPANION, AIEntity.createMobAttributes());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            System.out.println("[" + MOD_ID + "] Registriere /ai command");

            dispatcher.register(
                CommandManager.literal("ai")
                    // /ai spawn
                    .then(CommandManager.literal("spawn")
                        .executes(ctx -> {
                            var player = ctx.getSource().getPlayer();
                            if (player != null) {
                                AIEntity companion = new AIEntity(AI_COMPANION, player.getWorld());
                                companion.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), 0, 0);
                                companion.setOwner(player);
                                companion.setTamed(true);
                                player.getWorld().spawnEntity(companion);
                                player.sendMessage(Text.literal("§6[AI] §fBegleiter wurde gerufen!"), false);
                            }
                            return 1;
                        })
                    )
                    // /ai kill
                    .then(CommandManager.literal("kill")
                        .executes(ctx -> {
                            var server = ctx.getSource().getServer();
                            int removed = 0;

                            for (var world : server.getWorlds()) {
                                var companions = world.getEntitiesByType(AI_COMPANION, entity -> true);
                                for (var companion : companions) {
                                    // Force-remove companion even when invulnerable.
                                    companion.discard();
                                    removed++;
                                }
                            }

                            final int removedCount = removed;
                            ctx.getSource().sendFeedback(
                                () -> Text.literal("§6[AI] §fBegleiter entfernt: " + removedCount), false);
                            return 1;
                        })
                    )
                    // /ai frage <frage>
                    .then(CommandManager.literal("frage")
                        .then(CommandManager.argument("frage", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                var player = ctx.getSource().getPlayer();
                                var server = ctx.getSource().getServer();
                                String frage = StringArgumentType.getString(ctx, "frage");

                                if (player != null) {
                                    player.sendMessage(
                                        Text.literal("§6[AI] §fDenke nach über: " + frage), false);
                                }

                                new Thread(() -> {
                                    try {
                                        String antwort = callOllama(frage);
                                        if (antwort == null || antwort.isEmpty()) return;
                                        if (antwort.length() > 2000) antwort = antwort.substring(0, 2000) + "...";
                                        if (player != null) {
                                            String finalAntwort = antwort;
                                            server.execute(() -> player.sendMessage(
                                                Text.literal("§6[AI] §fAntwort: " + finalAntwort), false));
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        if (player != null) {
                                            String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                                            server.execute(() -> player.sendMessage(
                                                Text.literal("§c[AI] Fehler bei /ai frage: " + error), false));
                                        }
                                    }
                                }).start();

                                return 1;
                            })
                        )
                    )
            );
        });

        System.out.println("[" + MOD_ID + "] MOD GELADEN!");
    }

    // Innerhalb deiner Klasse Aicompanion2_0

    private String callOllama(String prompt) throws Exception {
        String json = "{\"model\":\"" + jsonEscape(MODEL) + "\",\"messages\":[{\"role\":\"user\",\"content\":\""
            + jsonEscape(prompt) + "\"}],\"stream\":false}";

        HttpResult primary = postChatCompletion(API_PATH, json);
        if (primary.status == 200) {
            return extractAssistantContent(primary.body);
        }

        // Some deployments expose OpenAI-compatible chat under /v1/chat/completions.
        if ((primary.status >= 500 || primary.status == 404) && !"/v1/chat/completions".equals(API_PATH)) {
            HttpResult fallback = postChatCompletion("/v1/chat/completions", json);
            if (fallback.status == 200) {
                return extractAssistantContent(fallback.body);
            }
            return formatHttpError(fallback.status, fallback.body, "/v1/chat/completions");
        }

        return formatHttpError(primary.status, primary.body, API_PATH);
    }

    private HttpResult postChatCompletion(String path, String json) throws Exception {
        URL url = new URL(API_BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (API_KEY != null && !API_KEY.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
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

    private String extractAssistantContent(String body) {
        int idx = body.indexOf("\"content\":\"");
        if (idx >= 0) {
            int start = idx + 11;
            int end = body.indexOf("\"", start);
            while (end > 0 && body.charAt(end - 1) == '\\') end = body.indexOf("\"", end + 1);
            return body.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
        }
        return body;
    }

    private String formatHttpError(int status, String body, String path) {
        if (body == null || body.isBlank()) {
            return "Fehler: HTTP " + status + " (" + path + ")";
        }
        return "Fehler: HTTP " + status + " (" + path + ") - " + body;
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

    private String jsonEscape(String value) {
        if (value == null) return "";
        String escaped = value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
        return escaped;
    }

    private static class HttpResult {
        private final int status;
        private final String body;

        private HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private void loadConfig() {
        try {
            Path configPath = Path.of("config", "aicompanion2_0.properties");
            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(configPath.toFile())) {
                props.load(in);
            }
            API_BASE_URL = props.getProperty("api.baseUrl", API_BASE_URL);
            API_PATH     = props.getProperty("api.path", API_PATH);
            MODEL        = props.getProperty("api.model", MODEL);
            API_KEY      = props.getProperty("api.key", API_KEY);

            if (API_KEY == null || API_KEY.isBlank()) {
                String clientFallbackKey = readApiKeyFrom(Path.of("config", "aicompanion2_0_client.properties"));
                if (clientFallbackKey != null && !clientFallbackKey.isBlank()) {
                    API_KEY = clientFallbackKey;
                }
            }
        } catch (IOException e) {
            System.out.println("[" + MOD_ID + "] Keine Config gefunden, benutze Default-API.");

            try {
                String clientFallbackKey = readApiKeyFrom(Path.of("config", "aicompanion2_0_client.properties"));
                if (clientFallbackKey != null && !clientFallbackKey.isBlank()) {
                    API_KEY = clientFallbackKey;
                }
            } catch (IOException ignored) {
                // Keep defaults when no config files are available.
            }
        }
    }

    private String readApiKeyFrom(Path configPath) throws IOException {
        if (!Files.exists(configPath)) return null;
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(configPath.toFile())) {
            props.load(in);
        }
        return props.getProperty("api.key", null);
    }
}