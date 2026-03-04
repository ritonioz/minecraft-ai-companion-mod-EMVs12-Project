package AiCompanion.aicompanion2_0.client;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class ClientConfig {

    private static final Path CONFIG_PATH = Path.of("config", "aicompanion2_0_client.properties");
    private static final Path SHARED_CONFIG_PATH = Path.of("config", "aicompanion2_0.properties");
    private static String apiKey = null;

    public static String getApiKey() {
        if (apiKey == null) load();
        return apiKey;
    }

    public static void setApiKey(String key) {
        apiKey = key;
        save();
    }

    public static boolean hasApiKey() {
        if (apiKey == null) load();
        return apiKey != null && !apiKey.isBlank();
    }

    private static void load() {
        try {
            // Prefer shared config so GUI and /ai frage use the same key source.
            apiKey = loadApiKeyFrom(SHARED_CONFIG_PATH);
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = loadApiKeyFrom(CONFIG_PATH);
            }
        } catch (IOException e) {
            System.out.println("[aicompanion2_0] Konnte Client-Config nicht laden.");
        }
    }

    private static void save() {
        try {
            saveApiKeyTo(CONFIG_PATH, "AI Companion Client Config");
            saveApiKeyTo(SHARED_CONFIG_PATH, "AI Companion Shared Config");
        } catch (IOException e) {
            System.out.println("[aicompanion2_0] Konnte Client-Config nicht speichern.");
        }
    }

    private static String loadApiKeyFrom(Path path) throws IOException {
        if (!Files.exists(path)) return null;
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            props.load(in);
        }
        return props.getProperty("api.key", null);
    }

    private static void saveApiKeyTo(Path path, String comment) throws IOException {
        Files.createDirectories(path.getParent());
        Properties props = new Properties();
        if (Files.exists(path)) {
            try (FileInputStream in = new FileInputStream(path.toFile())) {
                props.load(in);
            }
        }
        props.setProperty("api.key", apiKey != null ? apiKey : "");
        try (FileOutputStream out = new FileOutputStream(path.toFile())) {
            props.store(out, comment);
        }
    }
}
