package com.platform.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES 加密工具 — 用于数据源密码的加密存储与解密使用
 */
@Component
public class AesUtil {

    private static final String ALGORITHM = "AES";

    private final SecretKeySpec keySpec;

    public AesUtil(@Value("${platform.datasource.aes-key:SmartPlatformAESK}") String key) {
        // 确保密钥为 16 字节（AES-128）
        byte[] keyBytes = new byte[16];
        byte[] srcBytes = key.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(srcBytes, 0, keyBytes, 0, Math.min(srcBytes.length, 16));
        this.keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * AES 加密，返回 Base64 编码的密文
     */
    public String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    /**
     * AES 解密，返回明文
     */
    public String decrypt(String cipherText) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败", e);
        }
    }
}
