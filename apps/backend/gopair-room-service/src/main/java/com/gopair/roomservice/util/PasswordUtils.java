package com.gopair.roomservice.util;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 房间密码工具类
 *
 * <p>支持两种密码模式：
 * <ul>
 *   <li>模式1（固定密码）：AES-128/ECB 加密存储，可逆解密展示给房主</li>
 *   <li>模式2（动态令牌）：TOTP（RFC 6238 变体），secret 存 DB，令牌实时派生</li>
 * </ul>
 *
 * @author gopair
 */
public final class PasswordUtils {

    /** TOTP 窗口时长（秒），5分钟 */
    public static final long TOTP_WINDOW_SECONDS = 300L;

    /** TOTP 令牌位数 */
    private static final int TOTP_DIGITS = 6;

    /** TOTP 验证允许的窗口偏移（前后各1个窗口容错） */
    private static final int TOTP_ALLOWED_SKEW = 1;

    private PasswordUtils() {}

    // ==================== 模式1：AES 固定密码 ====================

    /**
     * 加密明文密码为 AES 密文（Base64编码）
     *
     * @param rawPassword 明文密码
     * @param roomId      房间ID（用于派生 AES 密钥）
     * @param masterKey   主密钥（来自配置）
     * @return Base64 编码的 AES 密文
     */
    public static String encryptPassword(String rawPassword, Long roomId, String masterKey) {
        try {
            byte[] keyBytes = deriveAesKey(roomId, masterKey);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(rawPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("密码加密失败", e);
        }
    }

    /**
     * 解密 AES 密文为明文密码
     *
     * @param cipherText Base64 编码的 AES 密文
     * @param roomId     房间ID
     * @param masterKey  主密钥
     * @return 明文密码
     */
    public static String decryptPassword(String cipherText, Long roomId, String masterKey) {
        try {
            byte[] keyBytes = deriveAesKey(roomId, masterKey);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("密码解密失败", e);
        }
    }

    /**
     * 验证明文密码是否与存储的 AES 密文匹配
     *
     * @param rawPassword 用户输入的明文密码
     * @param cipherText  存储的 AES 密文
     * @param roomId      房间ID
     * @param masterKey   主密钥
     * @return 是否匹配
     */
    public static boolean verifyPassword(String rawPassword, String cipherText, Long roomId, String masterKey) {
        try {
            String decrypted = decryptPassword(cipherText, roomId, masterKey);
            return decrypted.equals(rawPassword);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 派生 AES-128 密钥（16字节）
     * 算法：HMAC-SHA256(masterKey, roomId字符串) 取前16字节
     */
    private static byte[] deriveAesKey(Long roomId, String masterKey)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                masterKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] fullHash = mac.doFinal(
                String.valueOf(roomId).getBytes(StandardCharsets.UTF_8));
        byte[] key = new byte[16];
        System.arraycopy(fullHash, 0, key, 0, 16);
        return key;
    }

    // ==================== 模式2：TOTP 动态令牌 ====================

    /**
     * 生成 TOTP secret（20字节随机，Base64编码存储）
     *
     * @return Base64 编码的 secret
     */
    public static String generateTotpSecret() {
        byte[] secret = new byte[20];
        new SecureRandom().nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }

    /**
     * 计算指定窗口索引的 TOTP 令牌
     *
     * @param secret      Base64 编码的 TOTP secret
     * @param windowIndex 时间窗口索引
     * @return 6位数字字符串（不足6位前补0）
     */
    public static String computeTotp(String secret, long windowIndex) {
        try {
            byte[] secretBytes = Base64.getDecoder().decode(secret);
            byte[] windowBytes = ByteBuffer.allocate(8).putLong(windowIndex).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA1"));
            byte[] hash = mac.doFinal(windowBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, TOTP_DIGITS);
            return String.format("%0" + TOTP_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP 计算失败", e);
        }
    }

    /**
     * 验证用户输入的 TOTP 令牌（允许前后各1个窗口容错）
     *
     * @param inputToken 用户输入的令牌
     * @param secret     存储的 TOTP secret
     * @return 是否验证通过
     */
    public static boolean verifyTotp(String inputToken, String secret) {
        if (inputToken == null || inputToken.isBlank()) {
            return false;
        }
        long currentWindow = System.currentTimeMillis() / 1000L / TOTP_WINDOW_SECONDS;
        for (int skew = -TOTP_ALLOWED_SKEW; skew <= TOTP_ALLOWED_SKEW; skew++) {
            String expected = computeTotp(secret, currentWindow + skew);
            if (expected.equals(inputToken.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前有效的 TOTP 令牌（供房主展示）
     *
     * @param secret 存储的 TOTP secret
     * @return 当前6位令牌
     */
    public static String getCurrentTotp(String secret) {
        long currentWindow = System.currentTimeMillis() / 1000L / TOTP_WINDOW_SECONDS;
        return computeTotp(secret, currentWindow);
    }

    /**
     * 获取当前令牌窗口的剩余有效秒数
     *
     * @return 剩余秒数（0 ~ TOTP_WINDOW_SECONDS）
     */
    public static int getRemainingSeconds() {
        long nowSeconds = System.currentTimeMillis() / 1000L;
        long elapsed = nowSeconds % TOTP_WINDOW_SECONDS;
        return (int) (TOTP_WINDOW_SECONDS - elapsed);
    }
}
