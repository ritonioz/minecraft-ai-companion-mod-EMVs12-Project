package AiCompanion.aicompanion2_0.client;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.*;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * AES-256-GCM encryption with a key derived from machine-specific data via PBKDF2.
 * No key file is written to disk — the encryption key is re-derived on each session
 * from identifiers tied to the current user and machine, making the stored ciphertext
 * useless on any other machine or user account.
 */
public class CryptoUtil {

    static final String PREFIX = "ENC:";

    private static final int IV_LEN   = 12;
    private static final int TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 65536;

    // Fixed, non-secret salt — prevents pre-computation attacks.
    private static final byte[] SALT = {
        0x4d, 0x43, 0x41, 0x49, 0x5f, 0x73, 0x61, 0x6c,
        0x74, 0x5f, 0x76, 0x31, 0x5f, 0x78, 0x79, 0x7a
    };

    private static SecretKey cachedKey = null;

    // -------------------------------------------------------------------------

    private static SecretKey deriveKey() throws Exception {
        if (cachedKey != null) return cachedKey;
        String machineId = buildMachineId();
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(machineId.toCharArray(), SALT, PBKDF2_ITERATIONS, 256);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        cachedKey = new SecretKeySpec(keyBytes, "AES");
        return cachedKey;
    }

    /**
     * Builds a stable identifier from OS username, home directory path, hostname,
     * and MAC address. Changing any of these (e.g. renaming the user) will invalidate
     * previously encrypted values — intentional machine-binding behaviour.
     */
    private static String buildMachineId() {
        StringBuilder id = new StringBuilder();
        id.append(System.getProperty("user.name", "?"));
        id.append('\0');
        id.append(System.getProperty("user.home", "?"));
        id.append('\0');
        try {
            id.append(InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            id.append("localhost");
        }
        // MAC address for stronger hardware binding
        try {
            NetworkInterface ni = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            if (ni != null) {
                byte[] mac = ni.getHardwareAddress();
                if (mac != null) {
                    id.append('\0');
                    id.append(Base64.getEncoder().encodeToString(mac));
                }
            }
        } catch (Exception ignored) {}
        return id.toString();
    }

    // -------------------------------------------------------------------------

    /** Encrypts {@code plaintext} and returns a Base64 string (IV prepended to ciphertext). */
    public static String encrypt(String plaintext) throws Exception {
        SecretKey key = deriveKey();
        byte[] iv = new byte[IV_LEN];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plaintext.getBytes("UTF-8"));
        byte[] out = new byte[IV_LEN + ct.length];
        System.arraycopy(iv, 0, out, 0, IV_LEN);
        System.arraycopy(ct, 0, out, IV_LEN, ct.length);
        return Base64.getEncoder().encodeToString(out);
    }

    /**
     * Decrypts a Base64 string produced by {@link #encrypt}.
     * Returns {@code null} on failure (wrong machine, corrupt data, etc.).
     */
    public static String decrypt(String encoded) {
        try {
            SecretKey key = deriveKey();
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LEN];
            byte[] ct = new byte[combined.length - IV_LEN];
            System.arraycopy(combined, 0, iv, 0, IV_LEN);
            System.arraycopy(combined, IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
