package com.rassvet.essential.data.sync;

import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class VaultCryptography {
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int PBKDF2_ITERATIONS = 120_000;

    private VaultCryptography() {}

    public static byte[] encrypt(byte[] plain, String passphrase) throws Exception {
        byte[] salt = new byte[SALT_LEN];
        new SecureRandom().nextBytes(salt);
        SecretKey key = deriveKey(passphrase, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plain);
        byte[] out = new byte[salt.length + iv.length + ciphertext.length];
        System.arraycopy(salt, 0, out, 0, salt.length);
        System.arraycopy(iv, 0, out, salt.length, iv.length);
        System.arraycopy(ciphertext, 0, out, salt.length + iv.length, ciphertext.length);
        return out;
    }

    public static byte[] decrypt(byte[] blob, String passphrase) throws Exception {
        if (blob.length <= SALT_LEN + IV_LEN) {
            throw new IllegalArgumentException("blob too small");
        }
        byte[] salt = java.util.Arrays.copyOfRange(blob, 0, SALT_LEN);
        byte[] iv = java.util.Arrays.copyOfRange(blob, SALT_LEN, SALT_LEN + IV_LEN);
        byte[] ct = java.util.Arrays.copyOfRange(blob, SALT_LEN + IV_LEN, blob.length);
        SecretKey key = deriveKey(passphrase, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(ct);
    }

    private static SecretKey deriveKey(String passphrase, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec =
                new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        spec.clearPassword();
        return new SecretKeySpec(keyBytes, "AES");
    }
}


