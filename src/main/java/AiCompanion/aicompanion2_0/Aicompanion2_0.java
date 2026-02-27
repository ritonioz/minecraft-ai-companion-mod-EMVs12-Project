package AiCompanion.aicompanion2_0;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;


public class Aicompanion2_0 implements ModInitializer {

    public static final String MOD_ID = "aicompanion2_0";
    private static String API_BASE_URL = "http://ollama.cametendo.org";
    private static String API_PATH = "/api/generate";

    @Override
    public void onInitialize() {
        System.out.println("[" + MOD_ID + "] MOD STARTET!");

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            System.out.println("[" + MOD_ID + "] Registriere /ai command");

            dispatcher.register(
                    CommandManager.literal("ai")
                            .then(CommandManager.argument("frage", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        var player = ctx.getSource().getPlayer();
                                        String frage = StringArgumentType.getString(ctx, "frage");

                                        if (player != null) {
                                            player.sendMessage(
                                                    Text.literal("§6[AI] §fDenke nach über: " + frage),
                                                    false
                                            );
                                        }

                                        // KI‑Request in neuem Thread, damit der Server nicht hängt
                                        new Thread(() -> {
                                            try {
                                                String antwort = callOllama(frage);
                                                if (antwort == null || antwort.isEmpty()) {
                                                    return;
                                                }

                                                // Antwort etwas kürzen, damit der Chat nicht explodiert
                                                if (antwort.length() > 2000) {
                                                    antwort = antwort.substring(0, 2000) + "...";
                                                }

                                                if (player != null) {
                                                    player.sendMessage(
                                                            Text.literal("§6[AI] §fAntwort: " + antwort),
                                                            false
                                                    );
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }).start();

                                        return 1;
                                    })
                            )
            );
        });

        System.out.println("[" + MOD_ID + "] MOD GELADEN!");
    }

    // Innerhalb deiner Klasse Aicompanion2_0

    private String callOllama(String prompt) throws Exception {
        URL url = new URL(API_BASE_URL + API_PATH);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setDoOutput(true);

        // WICHTIG: "stream": false hinzugefügt
        String json = """
                {
                "model": "gpt-oss:20b",
                "prompt": "%s",
                "stream": false
                }
                """.formatted(prompt.replace("\"", "\\\""));

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = json.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        if (conn.getResponseCode() != 200) return "Fehler: " + conn.getResponseCode();

        StringBuilder resp = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                resp.append(line);
            }
        }
        conn.disconnect();

        // Primitives Parsing der Antwort (ohne externe Library)
        String fullResponse = resp.toString();
        if (fullResponse.contains("\"response\":\"")) {
            int start = fullResponse.indexOf("\"response\":\"") + 12;
            int end = fullResponse.indexOf("\"", start);
            return fullResponse.substring(start, end);
        }

        return fullResponse;
    }

private void loadConfig() {
    try {
        Path configPath = Path.of("config", "aicompanion2_0.properties");
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(configPath.toFile())) {
            props.load(in);
        }
        API_BASE_URL = props.getProperty("api.baseUrl", API_BASE_URL);
        API_PATH = props.getProperty("api.path", API_PATH);
    } catch (IOException e) {
        System.out.println("[" + MOD_ID + "] Keine Config gefunden, benutze Default-API.");
    }
}
}