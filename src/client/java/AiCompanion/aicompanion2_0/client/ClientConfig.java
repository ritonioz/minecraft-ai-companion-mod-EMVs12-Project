package AiCompanion.aicompanion2_0.client;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class ClientConfig {

    private static final Path CONFIG_PATH = Path.of("config", "aicompanion2_0_client.properties");
    private static final Path SHARED_CONFIG_PATH = Path.of("config", "aicompanion2_0.properties");

    private static String apiKey = null;
    private static String baseUrl = null;
    private static String model = null;
    private static String apiPath = null;
    private static boolean loaded = false;

    public static String getApiKey() {
        load();
        return apiKey;
    }

    public static void setApiKey(String key) {
        apiKey = key;
        save();
    }

    public static boolean deleteApiKey() throws IOException {
        boolean deleted = deleteApiKeyFrom(SHARED_CONFIG_PATH, "AI Companion Shared Config");
        deleted = deleteApiKeyFrom(CONFIG_PATH, "AI Companion Client Config") || deleted;
        apiKey = null;
        return deleted;
    }

    public static boolean hasApiKey() {
        load();
        return apiKey != null && !apiKey.isBlank();
    }

    public static String getBaseUrl() {
        if (!loaded) load();
        return baseUrl;
    }

    public static String getModel() {
        if (!loaded) load();
        return model;
    }

    public static String getApiPath() {
        if (!loaded) load();
        return apiPath;
    }

    public static boolean isSetupDone() {
        if (!loaded) load();
        return baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank();
    }

    public static void setProviderConfig(String url, String mdl, String key, String path) {
        baseUrl = url;
        model = mdl;
        apiKey = key != null ? key : "";
        apiPath = (path != null && !path.isBlank()) ? path : null;
        loaded = true;
        save();
    }

    private static void load() {
        loaded = true;
        try {
            apiKey = loadProp(SHARED_CONFIG_PATH, "api.key");
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = loadProp(CONFIG_PATH, "api.key");
            }
            baseUrl = loadProp(SHARED_CONFIG_PATH, "api.baseUrl");
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = loadProp(CONFIG_PATH, "api.baseUrl");
            }
            model = loadProp(SHARED_CONFIG_PATH, "api.model");
            if (model == null || model.isBlank()) {
                model = loadProp(CONFIG_PATH, "api.model");
            }
            apiPath = loadProp(SHARED_CONFIG_PATH, "api.path");
            if (apiPath == null || apiPath.isBlank()) {
                apiPath = loadProp(CONFIG_PATH, "api.path");
            }
        } catch (IOException e) {
            apiKey = null;
            System.out.println("[aicompanion2_0] Could not load Client Config.");
        }
    }

    private static void save() {
        try {
            saveApiKeyTo(CONFIG_PATH, "AI Companion Client Config");
            saveApiKeyTo(SHARED_CONFIG_PATH, "AI Companion Shared Config");
        } catch (IOException e) {
            System.out.println("[aicompanion2_0] Could not save Client Config.");
        }
    }

    private static String loadApiKeyFrom(Path path) throws IOException {
        if (!Files.exists(path)) return null;
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            props.load(in);
        }
        return props.getProperty(key, null);
    }

    private static void save() {
        try {
            saveTo(CONFIG_PATH, "AI Companion Client Config");
            saveTo(SHARED_CONFIG_PATH, "AI Companion Shared Config");
        } catch (IOException e) {
            System.out.println("[aicompanion2_0] Konnte Client-Config nicht speichern.");
        }
    }

    private static void saveTo(Path path, String comment) throws IOException {
        Files.createDirectories(path.getParent());
        Properties props = new Properties();
        if (Files.exists(path)) {
            try (FileInputStream in = new FileInputStream(path.toFile())) {
                props.load(in);
            }
        }
        props.setProperty("api.key", apiKey != null ? apiKey : "");
        if (baseUrl != null) props.setProperty("api.baseUrl", baseUrl);
        if (model != null) props.setProperty("api.model", model);
        if (apiPath != null) props.setProperty("api.path", apiPath);
        try (FileOutputStream out = new FileOutputStream(path.toFile())) {
            props.store(out, comment);
        }
    }

    private static boolean deleteApiKeyFrom(Path path, String comment) throws IOException {
        if (!Files.exists(path)) return false;

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            props.load(in);
        }

        String existingKey = props.getProperty("api.key", null);
        if (existingKey == null || existingKey.isBlank()) return false;

        props.remove("api.key");
        Files.createDirectories(path.getParent());
        try (FileOutputStream out = new FileOutputStream(path.toFile())) {
            props.store(out, comment);
        }
        return true;
    }
}
