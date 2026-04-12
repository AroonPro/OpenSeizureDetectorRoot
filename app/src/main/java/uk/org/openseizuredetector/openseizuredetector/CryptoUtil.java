package uk.org.openseizuredetector.openseizuredetector;

import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

public class CryptoUtil {
    private static final String AES_MODE = "AES/GCM/NoPadding";
    // In een echte app zou je deze sleutel veilig opslaan in de Android Keystore
    // Voor nu gebruiken we een vaste 256-bit sleutel (32 karakters)
    private static final byte[] FIXED_KEY = "JouwGeheimeVeiligeSleutel12345678".getBytes();

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
        byte[] iv = new byte[12];
        System.arraycopy(encrypted, 0, iv, 0, iv.length);
        SecretKeySpec keySpec = new SecretKeySpec(FIXED_KEY, "AES");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);

        Cipher cipher = Cipher.getInstance(AES_MODE);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
        return cipher.doFinal(encrypted, 12, encrypted.length - 12);
    }
}