package uk.org.openseizuredetector.openseizuredetector;

import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

public class CryptoUtil {
    private static final String AES_MODE = "AES/GCM/NoPadding";
    // De sleutel moet PRECIES 16, 24 of 32 bytes zijn voor AES.
    // "OpenSeizureDetectorSafeSSHKey123" = 32 karakters = 256 bits.
    private static final byte[] FIXED_KEY = "OpenSeizureDetectorSafeSSHKey123".getBytes();

    public static byte[] encrypt(byte[] input) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_MODE);
        byte[] iv = new byte[12]; // Initialisatie Vector voor GCM
        new SecureRandom().nextBytes(iv);
        SecretKeySpec keySpec = new SecretKeySpec(FIXED_KEY, "AES");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

        byte[] cipherText = cipher.doFinal(input);
        // Plak het IV voor het resultaat zodat de ontvanger het kan gebruiken
        byte[] combined = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
        return combined;
    }

    public static byte[] decrypt(byte[] encrypted) throws Exception {
        if (encrypted == null || encrypted.length < 12) return null;
        
        byte[] iv = new byte[12];
        System.arraycopy(encrypted, 0, iv, 0, iv.length);
        SecretKeySpec keySpec = new SecretKeySpec(FIXED_KEY, "AES");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);

        Cipher cipher = Cipher.getInstance(AES_MODE);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
        return cipher.doFinal(encrypted, 12, encrypted.length - 12);
    }
}
