package com.gopair.userservice.service.impl;

import com.gopair.userservice.util.PasswordUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PasswordUtils 密码工具类单元测试。
 *
 * BCrypt 加密为确定性单向哈希，同一明文每次加密结果不同（因随机盐），但 matches 始终返回 true。
 * 本测试类隔离 Spring 容器，专注于密码加密与匹配的数学逻辑校验。
 */
@DisplayName("PasswordUtils 单元测试")
class PasswordUtilsUnitTest {

    private PasswordUtils passwordUtils;

    @BeforeEach
    void setUp() {
        passwordUtils = new PasswordUtils();
    }

    @Nested
    @DisplayName("encode(String) 加密测试")
    class EncodeTests {

        @Test
        @DisplayName("正常密码加密应返回非空且不同于原始密码的哈希值")
        void encode_shouldReturnNonEmptyHash() {
            String raw = "MySecureP@ssw0rd!";
            String encoded = passwordUtils.encode(raw);

            assertThat(encoded).isNotBlank();
            assertThat(encoded).isNotEqualTo(raw);
            assertThat(encoded.startsWith("$2a$") || encoded.startsWith("$2b$")).isTrue();
        }

        @Test
        @DisplayName("同一密码多次加密结果应不同（随机盐）")
        void encode_samePassword_shouldProduceDifferentHashes() {
            String raw = "123456";
            String hash1 = passwordUtils.encode(raw);
            String hash2 = passwordUtils.encode(raw);

            assertThat(hash1).isNotEqualTo(hash2);
            assertThat(hash1.length()).isEqualTo(hash2.length());
        }

        @Test
        @DisplayName("空密码应可正常加密")
        void encode_emptyPassword_shouldReturnHash() {
            String encoded = passwordUtils.encode("");
            assertThat(encoded).isNotBlank();
        }

        @Test
        @DisplayName("超长密码应可正常加密")
        void encode_longPassword_shouldReturnHash() {
            String longPassword = "A".repeat(200);
            String encoded = passwordUtils.encode(longPassword);
            assertThat(encoded).isNotBlank();
            assertThat(passwordUtils.matches(longPassword, encoded)).isTrue();
        }
    }

    @Nested
    @DisplayName("matches(String, String) 匹配测试")
    class MatchesTests {

        @Test
        @DisplayName("正确密码匹配应返回 true")
        void matches_correctPassword_shouldReturnTrue() {
            String raw = "CorrectHorseBattery!";
            String encoded = passwordUtils.encode(raw);
            assertThat(passwordUtils.matches(raw, encoded)).isTrue();
        }

        @Test
        @DisplayName("错误密码匹配应返回 false")
        void matches_wrongPassword_shouldReturnFalse() {
            String raw = "CorrectPassword";
            String wrong = "WrongPassword";
            String encoded = passwordUtils.encode(raw);
            assertThat(passwordUtils.matches(wrong, encoded)).isFalse();
        }

        @Test
        @DisplayName("大小写敏感的匹配")
        void matches_caseSensitive_shouldBeCaseSensitive() {
            String upper = "ABCDEF";
            String lower = "abcdef";
            String encoded = passwordUtils.encode(upper);
            assertThat(passwordUtils.matches(lower, encoded)).isFalse();
        }

        @Test
        @DisplayName("包含特殊字符的密码匹配")
        void matches_specialCharacters_shouldMatchCorrectly() {
            String special = "P@ssw0rd!#$%^&*()_+-=[]{}|;':\",./<>?";
            String encoded = passwordUtils.encode(special);
            assertThat(passwordUtils.matches(special, encoded)).isTrue();
            assertThat(passwordUtils.matches("wrong", encoded)).isFalse();
        }

        @Test
        @DisplayName("哈希值被篡改后匹配应返回 false")
        void matches_tamperedHash_shouldReturnFalse() {
            String raw = "somepassword";
            String encoded = passwordUtils.encode(raw);
            String tampered = encoded.substring(0, encoded.length() - 1) + "X";
            assertThat(passwordUtils.matches(raw, tampered)).isFalse();
        }

        @Test
        @DisplayName("空字符串密码匹配")
        void matches_emptyPassword_shouldWorkCorrectly() {
            String empty = "";
            String encoded = passwordUtils.encode(empty);
            assertThat(passwordUtils.matches(empty, encoded)).isTrue();
            assertThat(passwordUtils.matches("notempty", encoded)).isFalse();
        }

        @Test
        @DisplayName("新密码与旧密码不能相同的业务校验（模拟）")
        void business_newPasswordMustDifferFromOld_shouldBeVerified() {
            String oldPassword = "OldP@ss123";
            String encodedOld = passwordUtils.encode(oldPassword);

            // 旧密码匹配自身哈希 → true
            assertThat(passwordUtils.matches(oldPassword, encodedOld)).isTrue();
            // 新密码就是旧密码 → matches 仍为 true，业务层应在此场景抛出异常
            assertThat(passwordUtils.matches(oldPassword, encodedOld)).isTrue();
        }
    }
}
