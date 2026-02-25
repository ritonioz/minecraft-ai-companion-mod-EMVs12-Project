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


public class Aicompanion2_0 implements ModInitializer {

    public static final String MOD_ID = "aicompanion2_0";
    private static String API_BASE_URL = "http://localhost:11434";
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

    private String callOllama(String prompt) throws Exception {
        // URL deines Ollama‑Servers, ggf. anpassen
        URL url = new URL(API_BASE_URL + API_PATH);


        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setDoOutput(true);

        String json = """
                {
                  "model": "llama3",
                  "prompt": "%s"
                }
                """.formatted(prompt.replace("\"", "\\\""));

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = json.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            System.out.println("[" + MOD_ID + "] Ollama HTTP Status: " + status);
            return null;
        }

        StringBuilder resp = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "utf-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                resp.append(line);
            }
        }

        conn.disconnect();

        // Sehr einfach: komplette JSON‑Antwort zurückgeben
        // Später können wir das noch sauber parsen
        return resp.toString();
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
        API_PATH = props.getProperty("api.path", API_PATH);
    } catch (IOException e) {
        System.out.println("[" + MOD_ID + "] Keine Config gefunden, benutze Default-API.");
    }
}


