package io.github.kanglong1023.m3u8.util;

import org.apache.commons.lang3.ObjectUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;

public final class CipherUtil {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private CipherUtil() {
    }

    public static Cipher getAndInitM3u8AESDecryptCipher(byte[] key, byte[] initVector) {
        Preconditions.checkArgument(ObjectUtils.allNotNull(key, initVector));

        Cipher cipher;
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
            AlgorithmParameterSpec paramSpec = new IvParameterSpec(initVector);

            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, paramSpec);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
        return cipher;
    }


}
