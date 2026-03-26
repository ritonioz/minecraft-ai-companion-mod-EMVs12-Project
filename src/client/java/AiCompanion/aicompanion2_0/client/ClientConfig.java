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
    private static String mode = null;
    private static boolean loaded = false;

    public static String getApiKey() {
        load();
        return apiKey;
    }

    public static void setApiKey(String key) {
        apiKey = key;
        save();
    }

    /** Wipes all provider config (URL, model, key, path, mode) from memory and both config files. */
    public static void deleteConfig() throws IOException {
        apiKey  = null;
        baseUrl = null;
        model   = null;
        apiPath = null;
        mode    = null;
        loaded  = false;
        deleteConfigFrom(SHARED_CONFIG_PATH);
        deleteConfigFrom(CONFIG_PATH);
    }

    private static void deleteConfigFrom(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            props.load(in);
        }
        props.remove("api.key");
        props.remove("api.baseUrl");
        props.remove("api.model");
        props.remove("api.path");
        props.remove("api.mode");
        try (FileOutputStream out = new FileOutputStream(path.toFile())) {
            props.store(out, "AI Companion Config");
        }
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

    public static String getMode() {
        if (!loaded) load();
        return mode != null && !mode.isBlank() ? mode : "casual";
    }

    public static void setMode(String m) {
        mode = (m != null && !m.isBlank()) ? m : "casual";
        save();
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

    public static void setProviderConfig(String url, String mdl, String key, String path, String newMode) {
        baseUrl = url;
        model = mdl;
        apiKey = key != null ? key : "";
        apiPath = (path != null && !path.isBlank()) ? path : null;
        mode = (newMode != null && !newMode.isBlank()) ? newMode : "casual";
        loaded = true;
        save();
    }

    private static void load() {
        loaded = true;
        // Remove the old plain-text key file if it still exists from a previous version
        try { Files.deleteIfExists(Path.of("config", "aicompanion2_0.key")); } catch (Exception ignored) {}
        try {
            apiKey = decryptKey(loadProp(SHARED_CONFIG_PATH, "api.key"));
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = decryptKey(loadProp(CONFIG_PATH, "api.key"));
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
            mode = loadProp(SHARED_CONFIG_PATH, "api.mode");
            if (mode == null || mode.isBlank()) {
                mode = loadProp(CONFIG_PATH, "api.mode");
            }
        } catch (IOException e) {
            apiKey = null;
            System.out.println("[aicompanion2_0] Could not load Client Config.");
        }
    }

    /** Decrypts an ENC:-prefixed value; returns plaintext as-is for legacy configs. */
    private static String decryptKey(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.startsWith(CryptoUtil.PREFIX)) {
            return CryptoUtil.decrypt(raw.substring(CryptoUtil.PREFIX.length()));
        }
        // Legacy plaintext — will be re-saved encrypted on next write
        return raw;
    }

    private static String loadProp(Path path, String key) throws IOException {
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
        String keyToStore = "";
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                keyToStore = CryptoUtil.PREFIX + CryptoUtil.encrypt(apiKey);
            } catch (Exception e) {
                keyToStore = apiKey; // fallback: store plaintext if encryption fails
            }
        }
        props.setProperty("api.key", keyToStore);
        if (baseUrl != null) props.setProperty("api.baseUrl", baseUrl);
        if (model != null) props.setProperty("api.model", model);
        if (apiPath != null) props.setProperty("api.path", apiPath);
        if (mode != null) props.setProperty("api.mode", mode);
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
