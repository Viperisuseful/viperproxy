package com.viperproxy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viperproxy.proxy.ProxyType;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class ProxyConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int AES_KEY_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final Path configFile;
    private final Path keyFile;

    public ProxyConfigStore(Path configFile) {
        this.configFile = configFile;
        this.keyFile = configFile.resolveSibling("viperproxy.key");
    }

    public ProxyConfig load() {
        LoadedProfiles loadedProfiles = loadProfiles();
        if (loadedProfiles.profiles().isEmpty()) {
            return new ProxyConfig();
        }

        return loadedProfiles.profiles().get(loadedProfiles.activeProfileIndex()).getConfigCopy().normalized();
    }

    public void save(ProxyConfig config) {
        List<ProxyProfile> profiles = new ArrayList<>();
        profiles.add(new ProxyProfile("Default", config == null ? new ProxyConfig() : config.normalized()));
        saveProfiles(profiles, 0);
    }

    public LoadedProfiles loadProfiles() {
        if (!Files.exists(this.configFile)) {
            return LoadedProfiles.defaultProfile();
        }

        try {
            String json = Files.readString(this.configFile, StandardCharsets.UTF_8);
            JsonObject rootJson = JsonParser.parseString(json).getAsJsonObject();

            if (rootJson.has("profiles")) {
                StoredRoot root = GSON.fromJson(rootJson, StoredRoot.class);
                return decodeStoredRoot(root);
            }

            ProxyConfig legacyConfig = GSON.fromJson(rootJson, ProxyConfig.class);
            ProxyProfile migrated = new ProxyProfile("Default", legacyConfig == null ? new ProxyConfig() : legacyConfig);
            return new LoadedProfiles(List.of(migrated), 0);
        } catch (Exception ignored) {
            return LoadedProfiles.defaultProfile();
        }
    }

    public void saveProfiles(List<ProxyProfile> profiles, int activeProfileIndex) {
        try {
            Path parent = this.configFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            StoredRoot root = encodeStoredRoot(profiles, activeProfileIndex);
            String json = GSON.toJson(root);
            Files.writeString(
                this.configFile,
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        } catch (Exception ignored) {
            // Fail silently to avoid breaking gameplay if config file can't be written.
        }
    }

    private LoadedProfiles decodeStoredRoot(StoredRoot root) {
        if (root == null || root.profiles == null || root.profiles.isEmpty()) {
            return LoadedProfiles.defaultProfile();
        }

        List<ProxyProfile> decodedProfiles = new ArrayList<>();
        for (StoredProfile storedProfile : root.profiles) {
            if (storedProfile == null) {
                continue;
            }

            String profileName = normalizeProfileName(storedProfile.name);
            ProxyConfig config = decodeStoredConfig(storedProfile.config == null ? new StoredProxyConfig() : storedProfile.config);
            decodedProfiles.add(new ProxyProfile(profileName, config));
        }

        if (decodedProfiles.isEmpty()) {
            decodedProfiles.add(new ProxyProfile("Default", new ProxyConfig()));
        }

        int sanitizedActiveIndex = sanitizeActiveIndex(root.activeProfileIndex, decodedProfiles.size());
        return new LoadedProfiles(decodedProfiles, sanitizedActiveIndex);
    }

    private StoredRoot encodeStoredRoot(List<ProxyProfile> profiles, int activeProfileIndex) throws GeneralSecurityException, IOException {
        List<ProxyProfile> normalizedProfiles = new ArrayList<>();
        if (profiles != null) {
            for (ProxyProfile profile : profiles) {
                if (profile != null) {
                    normalizedProfiles.add(profile.copy());
                }
            }
        }

        if (normalizedProfiles.isEmpty()) {
            normalizedProfiles.add(new ProxyProfile("Default", new ProxyConfig()));
        }

        StoredRoot root = new StoredRoot();
        root.activeProfileIndex = sanitizeActiveIndex(activeProfileIndex, normalizedProfiles.size());

        for (ProxyProfile profile : normalizedProfiles) {
            StoredProfile storedProfile = new StoredProfile();
            storedProfile.name = normalizeProfileName(profile.getName());
            storedProfile.config = encodeStoredConfig(profile.getConfigCopy());
            root.profiles.add(storedProfile);
        }

        return root;
    }

    private ProxyConfig decodeStoredConfig(StoredProxyConfig stored) {
        ProxyConfig config = new ProxyConfig();
        config.enabled = stored.enabled;
        config.host = stored.host == null ? "" : stored.host;
        config.port = stored.port;
        config.type = parseType(stored.type);

        String username = stored.username == null ? "" : stored.username;
        String password = stored.password == null ? "" : stored.password;

        if (stored.credentialsIv != null && !stored.credentialsIv.isBlank()
            && stored.credentialsCiphertext != null && !stored.credentialsCiphertext.isBlank()) {
            try {
                String decrypted = decryptCredentials(stored.credentialsIv, stored.credentialsCiphertext);
                int separator = decrypted.indexOf('\n');
                if (separator >= 0) {
                    username = decrypted.substring(0, separator);
                    password = decrypted.substring(separator + 1);
                }
            } catch (Exception ignored) {
                username = "";
                password = "";
            }
        }

        config.username = username;
        config.password = password;

        return config.normalized();
    }

    private StoredProxyConfig encodeStoredConfig(ProxyConfig config) throws GeneralSecurityException, IOException {
        ProxyConfig normalized = config == null ? new ProxyConfig() : config.normalized();

        StoredProxyConfig stored = new StoredProxyConfig();
        stored.enabled = normalized.enabled;
        stored.host = normalized.host;
        stored.port = normalized.port;
        stored.type = normalized.type == null ? ProxyType.SOCKS5.name() : normalized.type.name();

        if (normalized.hasCredentials()) {
            EncryptedCredentials encrypted = encryptCredentials(normalized.username + "\n" + normalized.password);
            stored.credentialsIv = encrypted.ivBase64();
            stored.credentialsCiphertext = encrypted.ciphertextBase64();

            // Never store plaintext credentials when encryption is used.
            stored.username = "";
            stored.password = "";
        } else {
            stored.username = "";
            stored.password = "";
        }

        return stored;
    }

    private EncryptedCredentials encryptCredentials(String plaintext) throws GeneralSecurityException, IOException {
        SecretKey key = deriveSecretKey();

        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));

        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return new EncryptedCredentials(
            Base64.getEncoder().encodeToString(iv),
            Base64.getEncoder().encodeToString(encrypted)
        );
    }

    private String decryptCredentials(String ivBase64, String ciphertextBase64) throws GeneralSecurityException, IOException {
        SecretKey key = deriveSecretKey();

        byte[] iv = Base64.getDecoder().decode(ivBase64);
        byte[] encrypted = Base64.getDecoder().decode(ciphertextBase64);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));

        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private SecretKey deriveSecretKey() throws GeneralSecurityException, IOException {
        String persistedSecret = getOrCreateMachineSecret();
        String fingerprint = (
            System.getProperty("os.name", "") + "|"
                + System.getProperty("user.name", "") + "|"
                + System.getenv().getOrDefault("COMPUTERNAME", "")
        ).toLowerCase(Locale.ROOT);

        String keyMaterial = persistedSecret + "|" + fingerprint;
        byte[] salt = "viperproxy-aes-gcm-salt".getBytes(StandardCharsets.UTF_8);

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(keyMaterial.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_BITS);
        byte[] encoded = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(encoded, "AES");
    }

    private String getOrCreateMachineSecret() throws IOException {
        Path parent = this.keyFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (Files.exists(this.keyFile)) {
            try (BufferedReader reader = Files.newBufferedReader(this.keyFile, StandardCharsets.UTF_8)) {
                String existing = reader.readLine();
                if (existing != null && !existing.isBlank()) {
                    return existing.trim();
                }
            }
        }

        String generated = UUID.randomUUID().toString();
        Files.writeString(
            this.keyFile,
            generated,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
        return generated;
    }

    private static int sanitizeActiveIndex(int activeProfileIndex, int size) {
        if (size <= 0) {
            return 0;
        }

        if (activeProfileIndex < 0 || activeProfileIndex >= size) {
            return 0;
        }

        return activeProfileIndex;
    }

    private static String normalizeProfileName(String name) {
        if (name == null || name.isBlank()) {
            return "Default";
        }

        return name.trim();
    }

    private static ProxyType parseType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return ProxyType.SOCKS5;
        }

        try {
            return ProxyType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ProxyType.SOCKS5;
        }
    }

    public record LoadedProfiles(List<ProxyProfile> profiles, int activeProfileIndex) {
        public static LoadedProfiles defaultProfile() {
            return new LoadedProfiles(List.of(new ProxyProfile("Default", new ProxyConfig())), 0);
        }
    }

    private record EncryptedCredentials(String ivBase64, String ciphertextBase64) {
    }

    private static final class StoredRoot {
        int activeProfileIndex = 0;
        List<StoredProfile> profiles = new ArrayList<>();
    }

    private static final class StoredProfile {
        String name = "Default";
        StoredProxyConfig config = new StoredProxyConfig();
    }

    private static final class StoredProxyConfig {
        boolean enabled;
        String host = "";
        int port = 1080;
        String type = ProxyType.SOCKS5.name();

        String username = "";
        String password = "";
        String credentialsIv = "";
        String credentialsCiphertext = "";
    }
}
