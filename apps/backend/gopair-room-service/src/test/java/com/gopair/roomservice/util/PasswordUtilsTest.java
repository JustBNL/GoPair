package com.gopair.roomservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PasswordUtils")
class PasswordUtilsTest {

    private static final String MASTER_KEY = "gopair-master-key-for-test";

    @Nested
    @DisplayName("AES-256/GCM 固定密码模式")
    class AesPasswordTests {

        @Test
        @DisplayName("encrypt 后 decrypt 应还原原文")
        void encryptDecryptRoundTrip() {
            String raw = "mySecret123";
            Long roomId = 1001L;

            String cipher = PasswordUtils.encryptPassword(raw, roomId, MASTER_KEY);
            String decrypted = PasswordUtils.decryptPassword(cipher, roomId, MASTER_KEY);

            assertEquals(raw, decrypted);
        }

        @Test
        @DisplayName("相同密码 + 不同 roomId 应产生不同密文")
        void differentRoomIdProducesDifferentCiphertext() {
            String raw = "samePassword";
            Long roomId1 = 1001L;
            Long roomId2 = 1002L;

            String cipher1 = PasswordUtils.encryptPassword(raw, roomId1, MASTER_KEY);
            String cipher2 = PasswordUtils.encryptPassword(raw, roomId2, MASTER_KEY);

            assertNotEquals(cipher1, cipher2);
        }

        @Test
        @DisplayName("verifyPassword 正确密码应返回 true")
        void verifyPasswordCorrect() {
            String raw = "correctPass";
            Long roomId = 2001L;
            String cipher = PasswordUtils.encryptPassword(raw, roomId, MASTER_KEY);

            assertTrue(PasswordUtils.verifyPassword(raw, cipher, roomId, MASTER_KEY));
        }

        @Test
        @DisplayName("verifyPassword 错误密码应返回 false")
        void verifyPasswordWrong() {
            String raw = "correctPass";
            String wrong = "wrongPass";
            Long roomId = 2002L;
            String cipher = PasswordUtils.encryptPassword(raw, roomId, MASTER_KEY);

            assertFalse(PasswordUtils.verifyPassword(wrong, cipher, roomId, MASTER_KEY));
        }

        @Test
        @DisplayName("verifyPassword null 密码应返回 false")
        void verifyPasswordNull() {
            Long roomId = 2003L;
            String cipher = PasswordUtils.encryptPassword("anything", roomId, MASTER_KEY);

            assertFalse(PasswordUtils.verifyPassword(null, cipher, roomId, MASTER_KEY));
        }

        @Test
        @DisplayName("空字符串密码应正常加密解密")
        void emptyPasswordRoundTrip() {
            String raw = "";
            Long roomId = 3001L;

            String cipher = PasswordUtils.encryptPassword(raw, roomId, MASTER_KEY);
            String decrypted = PasswordUtils.decryptPassword(cipher, roomId, MASTER_KEY);

            assertEquals(raw, decrypted);
        }

        @Test
        @DisplayName("加密后密文应为有效 Base64 字符串")
        void ciphertextIsBase64() {
            String cipher = PasswordUtils.encryptPassword("test", 4001L, MASTER_KEY);
            assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(cipher));
        }
    }

    @Nested
    @DisplayName("TOTP 动态令牌模式")
    class TotpTests {

        @Test
        @DisplayName("generateTotpSecret 应返回 Base64 字符串，长度约27")
        void generateTotpSecret() {
            String secret = PasswordUtils.generateTotpSecret();
            assertNotNull(secret);
            assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(secret));
            assertTrue(secret.length() >= 26 && secret.length() <= 28,
                    "20字节随机数经Base64编码应为约27字符，实际：" + secret.length());
        }

        @RepeatedTest(5)
        @DisplayName("getCurrentTotp 应返回6位数字字符串")
        void getCurrentTotpReturnsSixDigits() {
            String secret = PasswordUtils.generateTotpSecret();
            String totp = PasswordUtils.getCurrentTotp(secret);

            assertEquals(6, totp.length());
            assertTrue(Pattern.compile("\\d{6}").matcher(totp).matches(),
                    "TOTP 应为6位数字，实际：" + totp);
        }

        @Test
        @DisplayName("同一 secret 同一窗口内 getCurrentTotp 结果一致")
        void totpDeterministicWithinWindow() {
            String secret = PasswordUtils.generateTotpSecret();
            String totp1 = PasswordUtils.getCurrentTotp(secret);
            String totp2 = PasswordUtils.getCurrentTotp(secret);
            assertEquals(totp1, totp2);
        }

        @Test
        @DisplayName("verifyTotp 正确令牌应返回 true")
        void verifyTotpCorrectToken() {
            String secret = PasswordUtils.generateTotpSecret();
            String currentTotp = PasswordUtils.getCurrentTotp(secret);

            assertTrue(PasswordUtils.verifyTotp(currentTotp, secret));
        }

        @Test
        @DisplayName("verifyTotp 错误令牌应返回 false")
        void verifyTotpWrongToken() {
            String secret = PasswordUtils.generateTotpSecret();

            assertFalse(PasswordUtils.verifyTotp("000000", secret));
        }

        @Test
        @DisplayName("verifyTotp null 令牌应返回 false")
        void verifyTotpNullToken() {
            String secret = PasswordUtils.generateTotpSecret();
            assertFalse(PasswordUtils.verifyTotp(null, secret));
        }

        @Test
        @DisplayName("verifyTotp 空令牌应返回 false")
        void verifyTotpBlankToken() {
            String secret = PasswordUtils.generateTotpSecret();
            assertFalse(PasswordUtils.verifyTotp("   ", secret));
        }

        @Test
        @DisplayName("verifyTotp 应允许前后1个窗口的容错")
        void verifyTotpWindowSkewTolerance() {
            String secret = PasswordUtils.generateTotpSecret();

            long currentWindow = System.currentTimeMillis() / 1000L / PasswordUtils.TOTP_WINDOW_SECONDS;
            String prevWindowTotp = PasswordUtils.computeTotp(secret, currentWindow - 1);
            String nextWindowTotp = PasswordUtils.computeTotp(secret, currentWindow + 1);

            assertTrue(PasswordUtils.verifyTotp(prevWindowTotp, secret),
                    "前1个窗口的TOTP应在容错范围内");
            assertTrue(PasswordUtils.verifyTotp(nextWindowTotp, secret),
                    "后1个窗口的TOTP应在容错范围内");
        }

        @Test
        @DisplayName("verifyTotp 前2个窗口令牌应不通过（超过容错范围）")
        void verifyTotpBeyondTolerance() {
            String secret = PasswordUtils.generateTotpSecret();

            long currentWindow = System.currentTimeMillis() / 1000L / PasswordUtils.TOTP_WINDOW_SECONDS;
            String twoWindowsAgo = PasswordUtils.computeTotp(secret, currentWindow - 2);

            assertFalse(PasswordUtils.verifyTotp(twoWindowsAgo, secret),
                    "前2个窗口的TOTP超出容错范围，应返回false");
        }

        @Test
        @DisplayName("getRemainingSeconds 应返回 0 到 TOTP_WINDOW_SECONDS 之间的整数")
        void getRemainingSecondsRange() {
            int remaining = PasswordUtils.getRemainingSeconds();
            assertTrue(remaining >= 0 && remaining <= PasswordUtils.TOTP_WINDOW_SECONDS,
                    "剩余秒数应在 [0, " + PasswordUtils.TOTP_WINDOW_SECONDS + "] 之间，实际：" + remaining);
        }
    }
}
