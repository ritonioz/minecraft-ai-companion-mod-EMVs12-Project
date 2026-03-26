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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class Aicompanion2_0 implements ModInitializer {

    public static final String MOD_ID = "aicompanion2_0";
    private static final String DEFAULT_API_BASE_URL = "https://ai.example.org";
    private static final String DEFAULT_MODEL = "minecraft-helper";
    private static final String DEFAULT_API_PATH = "/api/chat/completions";
    private static String API_BASE_URL = DEFAULT_API_BASE_URL;
    private static String MODEL = DEFAULT_MODEL;
    private static String API_KEY = "";
    private static String API_PATH = DEFAULT_API_PATH;
    private static final Path SHARED_CONFIG_PATH = Path.of("config", "aicompanion2_0.properties");
    private static final Path CLIENT_CONFIG_PATH = Path.of("config", "aicompanion2_0_client.properties");
    private static final long ARCH_EASTER_EGG_COOLDOWN_MS = 15L * 60L * 1000L;
    private static final int ARCH_EASTER_EGG_BUFF_DURATION_TICKS = 20 * 60;
    private static final int ARCH_EASTER_EGG_TUX_DURATION_TICKS = 20 * 10;
    private static final Map<UUID, Long> ARCH_EASTER_EGG_LAST_USED = new HashMap<>();
    public static final Identifier QUESTION_PACKET_ID = new Identifier(MOD_ID, "question");
    public static final Identifier DELETE_KEY_PACKET_ID = new Identifier(MOD_ID, "delete_key");
    public static final Identifier DELETE_CONFIG_PACKET_ID = new Identifier(MOD_ID, "delete_config");
    public static final Identifier MODEL_SELECT_PACKET_ID = new Identifier(MOD_ID, "model_select");
    public static final Identifier CHANGE_MODE_PACKET_ID = new Identifier(MOD_ID, "change_mode");
    public static final Identifier ARCH_EASTER_EGG_PACKET_ID = new Identifier(MOD_ID, "arch_easter_egg");
    public static final Identifier ARCH_EASTER_EGG_RESPONSE_PACKET_ID = new Identifier(MOD_ID, "arch_easter_egg_response");

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
        ServerPlayNetworking.registerGlobalReceiver(ARCH_EASTER_EGG_PACKET_ID, (server, player, handler, buf, responseSender) ->
            server.execute(() -> sendArchEasterEggResponse(player))
        );

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
                                player.sendMessage(Text.literal("§6[AI] §fThe companion has been called!"), false);
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
                                () -> Text.literal("§6[AI] §fCompanion removed: " + removedCount), false);
                            return 1;
                        })
                    )
                    // /ai delete-key
                    .then(CommandManager.literal("delete-key")
                        .executes(ctx -> {
                            var player = ctx.getSource().getPlayer();
                            if (player != null) {
                                if (!ServerPlayNetworking.canSend(player, DELETE_KEY_PACKET_ID)) {
                                    ctx.getSource().sendFeedback(
                                        () -> Text.literal("§c[AI] delete-key requires the AI Companion client mod."), false);
                                    return 0;
                                }

                                ServerPlayNetworking.send(player, DELETE_KEY_PACKET_ID, PacketByteBufs.create());
                                return 1;
                            }

                            try {
                                boolean deleted = deleteStoredApiKey();
                                if (deleted) {
                                    ctx.getSource().sendFeedback(
                                        () -> Text.literal("§6[AI] §fAPI key deleted."), false);
                                } else {
                                    ctx.getSource().sendFeedback(
                                        () -> Text.literal("§6[AI] §fno api key deleted: none found"), false);
                                }
                                return 1;
                            } catch (IOException e) {
                                String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                                ctx.getSource().sendFeedback(
                                    () -> Text.literal("§c[AI] Could not delete API key: " + error), false);
                                return 0;
                            }
                        })
                    )
                    // /ai delete-config
                    .then(CommandManager.literal("delete-config")
                        .executes(ctx -> {
                            var player = ctx.getSource().getPlayer();
                            if (player != null) {
                                if (!ServerPlayNetworking.canSend(player, DELETE_CONFIG_PACKET_ID)) {
                                    ctx.getSource().sendFeedback(
                                        () -> Text.literal("§c[AI] delete-config requires the AI Companion client mod."), false);
                                    return 0;
                                }
                                ServerPlayNetworking.send(player, DELETE_CONFIG_PACKET_ID, PacketByteBufs.create());
                                return 1;
                            }
                            return 0;
                        })
                    )
                    // /ai model
                    .then(CommandManager.literal("model")
                        .executes(ctx -> {
                            var player = ctx.getSource().getPlayer();
                            if (player != null) {
                                if (!ServerPlayNetworking.canSend(player, MODEL_SELECT_PACKET_ID)) {
                                    ctx.getSource().sendFeedback(
                                        () -> Text.literal("§c[AI] model command requires the AI Companion client mod."), false);
                                    return 0;
                                }
                                ServerPlayNetworking.send(player, MODEL_SELECT_PACKET_ID, PacketByteBufs.create());
                                return 1;
                            }
                            return 0;
                        })
                    )
                    // /ai change-mode
                    .then(CommandManager.literal("change-mode")
                        .executes(ctx -> {
                            var player = ctx.getSource().getPlayer();
                            if (player != null) {
                                if (!ServerPlayNetworking.canSend(player, CHANGE_MODE_PACKET_ID)) {
                                    ctx.getSource().sendFeedback(
                                        () -> Text.literal("§c[AI] change-mode requires the AI Companion client mod."), false);
                                    return 0;
                                }
                                ServerPlayNetworking.send(player, CHANGE_MODE_PACKET_ID, PacketByteBufs.create());
                                return 1;
                            }
                            return 0;
                        })
                    )
                    // /ai question <question>
                    .then(CommandManager.literal("question")
                        .then(CommandManager.argument("question", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                var player = ctx.getSource().getPlayer();
                                var server = ctx.getSource().getServer();
                                String frage = StringArgumentType.getString(ctx, "question");

                                if (player != null) {
                                    if (!ServerPlayNetworking.canSend(player, QUESTION_PACKET_ID)) {
                                        ctx.getSource().sendFeedback(
                                            () -> Text.literal("§c[AI] question requires the AI Companion client mod."), false);
                                        return 0;
                                    }

                                    var buf = PacketByteBufs.create();
                                    buf.writeString(frage);
                                    ServerPlayNetworking.send(player, QUESTION_PACKET_ID, buf);
                                    return 1;
                                }

                                new Thread(() -> {
                                    try {
                                        String antwort = callOllama(frage);
                                        if (antwort == null || antwort.isEmpty()) return;
                                        if (antwort.length() > 2000) antwort = antwort.substring(0, 2000) + "...";
                                        if (player != null) {
                                            String finalAntwort = antwort;
                                            server.execute(() -> player.sendMessage(
                                                Text.literal("§6[AI] §fAnswer: " + finalAntwort), false));
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        if (player != null) {
                                            String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                                            server.execute(() -> player.sendMessage(
                                                Text.literal("§c[AI] Error with /ai question: " + error), false));
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
        loadConfig();

        String apiBaseUrl = API_BASE_URL;
        String apiPath = API_PATH;
        String model = MODEL;
        String apiKey = API_KEY;

        String json = "{\"model\":\"" + jsonEscape(model) + "\",\"messages\":[{\"role\":\"user\",\"content\":\""
            + jsonEscape(prompt) + "\"}],\"stream\":false}";

        HttpResult primary = postChatCompletion(apiBaseUrl, apiPath, apiKey, json);
        if (primary.status == 200) {
            return extractAssistantContent(primary.body);
        }

        // Some deployments expose OpenAI-compatible chat under /v1/chat/completions.
        if ((primary.status >= 500 || primary.status == 404) && !"/v1/chat/completions".equals(apiPath)) {
            HttpResult fallback = postChatCompletion(apiBaseUrl, "/v1/chat/completions", apiKey, json);
            if (fallback.status == 200) {
                return extractAssistantContent(fallback.body);
            }
            return formatHttpError(fallback.status, fallback.body, "/v1/chat/completions");
        }

        return formatHttpError(primary.status, primary.body, apiPath);
    }

    private HttpResult postChatCompletion(String apiBaseUrl, String path, String apiKey, String json) throws Exception {
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

    private synchronized void loadConfig() {
        API_BASE_URL = DEFAULT_API_BASE_URL;
        API_PATH = DEFAULT_API_PATH;
        MODEL = DEFAULT_MODEL;
        API_KEY = "";

        try {
            if (Files.exists(SHARED_CONFIG_PATH)) {
                Properties props = new Properties();
                try (FileInputStream in = new FileInputStream(SHARED_CONFIG_PATH.toFile())) {
                    props.load(in);
                }
                API_BASE_URL = props.getProperty("api.baseUrl", DEFAULT_API_BASE_URL);
                API_PATH     = props.getProperty("api.path", DEFAULT_API_PATH);
                MODEL        = props.getProperty("api.model", DEFAULT_MODEL);
                API_KEY      = props.getProperty("api.key", "").trim();
            }

            if (API_KEY == null || API_KEY.isBlank()) {
                String clientFallbackKey = readApiKeyFrom(CLIENT_CONFIG_PATH);
                if (clientFallbackKey != null && !clientFallbackKey.isBlank()) {
                    API_KEY = clientFallbackKey;
                }
            }
        } catch (IOException e) {
            System.out.println("[" + MOD_ID + "] Could not reload config, using defaults.");
        }
    }

    private boolean deleteStoredApiKey() throws IOException {
        boolean deleted = deleteApiKeyFrom(SHARED_CONFIG_PATH, "AI Companion Shared Config");
        deleted = deleteApiKeyFrom(CLIENT_CONFIG_PATH, "AI Companion Client Config") || deleted;
        API_KEY = "";
        return deleted;
    }

    private String readApiKeyFrom(Path configPath) throws IOException {
        if (!Files.exists(configPath)) return null;
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(configPath.toFile())) {
            props.load(in);
        }
        return props.getProperty("api.key", null);
    }

    private boolean deleteApiKeyFrom(Path configPath, String comment) throws IOException {
        if (!Files.exists(configPath)) return false;

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(configPath.toFile())) {
            props.load(in);
        }

        String existingKey = props.getProperty("api.key", null);
        if (existingKey == null || existingKey.isBlank()) return false;

        props.remove("api.key");
        Files.createDirectories(configPath.getParent());
        try (OutputStream out = Files.newOutputStream(configPath)) {
            props.store(out, comment);
        }
        return true;
    }

    private void sendArchEasterEggResponse(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, ARCH_EASTER_EGG_RESPONSE_PACKET_ID)) {
            return;
        }

        String response = triggerArchEasterEgg(player);
        var buf = PacketByteBufs.create();
        buf.writeString(response);
        ServerPlayNetworking.send(player, ARCH_EASTER_EGG_RESPONSE_PACKET_ID, buf);
    }

    private String triggerArchEasterEgg(ServerPlayerEntity player) {
        long now = System.currentTimeMillis();
        Long lastUsed = ARCH_EASTER_EGG_LAST_USED.get(player.getUuid());

        if (lastUsed != null) {
            long remainingMs = ARCH_EASTER_EGG_COOLDOWN_MS - (now - lastUsed);
            if (remainingMs > 0) {
                return "Optimization already complete. Cooldown remaining: " + formatCooldown(remainingMs) + ".";
            }
        }

        ARCH_EASTER_EGG_LAST_USED.put(player.getUuid(), now);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ARCH_EASTER_EGG_BUFF_DURATION_TICKS, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, ARCH_EASTER_EGG_BUFF_DURATION_TICKS, 1));
        activateTuxModeForOwnedCompanions(player);
        return "Optimization complete. Bloatware removed.";
    }

    private void activateTuxModeForOwnedCompanions(ServerPlayerEntity player) {
        for (ServerWorld world : player.getServer().getWorlds()) {
            for (AIEntity companion : world.getEntitiesByType(AI_COMPANION, entity ->
                player.getUuid().equals(entity.getOwnerUuid()))) {
                companion.activateTuxMode(ARCH_EASTER_EGG_TUX_DURATION_TICKS);
            }
        }
    }

    private String formatCooldown(long remainingMs) {
        long totalSeconds = Math.max(1, (remainingMs + 999) / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + "m " + seconds + "s";
    }
}
