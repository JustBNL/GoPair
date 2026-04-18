package com.gopair.adminservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AdminJwtUtils 纯单元测试。
 *
 * * [核心策略]
 * - 无 Spring 上下文，直接调用静态方法。
 * - 覆盖正常流程 + 边界异常（空密钥、长度不足、过期、伪造签名）。
 *
 * @author gopair
 */
@DisplayName("AdminJwtUtils 工具类单元测试")
class AdminJwtUtilsUnitTest {

    private static final String SECRET =
            "hZ3UDLoIEAiyUChiKDNVaMrXK65vrKY+e/MxxDeu3wdjQgsBsl6/fsPr/kR/fbs0W8/3AGbt515cqMXs/AkdDw==";
    private static final long EXPIRATION = 28800000L;

    @Nested
    @DisplayName("generateToken — 正常生成")
    class GenerateToken {

        @Test
        @DisplayName("生成包含正确 Claims 的 Token")
        void generateToken_ShouldContainCorrectClaims() {
            String token = AdminJwtUtils.generateToken("admin", 1L, SECRET, EXPIRATION);

            assertNotNull(token);
            assertFalse(token.isEmpty());

            String username = AdminJwtUtils.getUsernameFromToken(token, SECRET);
            assertEquals("admin", username);

            String adminId = AdminJwtUtils.getAdminIdFromToken(token, SECRET);
            assertEquals("1", adminId);
        }

        @Test
        @DisplayName("同一用户两次生成 Token，每次有效（相同毫秒内可能相同）")
        void generateToken_Twice_ShouldBothBeValid() {
            String token1 = AdminJwtUtils.generateToken("admin", 1L, SECRET, EXPIRATION);
            String token2 = AdminJwtUtils.generateToken("admin", 1L, SECRET, EXPIRATION);

            assertEquals("admin", AdminJwtUtils.getUsernameFromToken(token1, SECRET));
            assertEquals("admin", AdminJwtUtils.getUsernameFromToken(token2, SECRET));

            assertTrue(AdminJwtUtils.validateToken(token1, SECRET));
            assertTrue(AdminJwtUtils.validateToken(token2, SECRET));
        }
    }

    @Nested
    @DisplayName("validateToken — 验证")
    class ValidateToken {

        @Test
        @DisplayName("有效 Token 验证通过")
        void validateToken_WithValidToken_ShouldReturnTrue() {
            String token = AdminJwtUtils.generateToken("admin", 1L, SECRET, EXPIRATION);

            assertTrue(AdminJwtUtils.validateToken(token, SECRET));
        }

        @Test
        @DisplayName("已过期 Token 验证失败")
        void validateToken_WithExpiredToken_ShouldReturnFalse() {
            String token = AdminJwtUtils.generateToken("admin", 1L, SECRET, -1000L);

            assertFalse(AdminJwtUtils.validateToken(token, SECRET));
        }

        @Test
        @DisplayName("伪造签名 Token 验证失败")
        void validateToken_WithForgedToken_ShouldReturnFalse() {
            String validToken = AdminJwtUtils.generateToken("admin", 1L, SECRET, EXPIRATION);
            String forgedToken = validToken.substring(0, validToken.length() - 5) + "XXXXX";

            assertFalse(AdminJwtUtils.validateToken(forgedToken, SECRET));
        }

        @Test
        @DisplayName("不同密钥验证失败（防止跨环境攻击）")
        void validateToken_WithDifferentSecret_ShouldReturnFalse() {
            String token = AdminJwtUtils.generateToken("admin", 1L, SECRET, EXPIRATION);
            String anotherSecret =
                    "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";

            assertFalse(AdminJwtUtils.validateToken(token, anotherSecret));
        }

        @Test
        @DisplayName("空密钥验证时返回 false（异常被内部吞掉）")
        void validateToken_WithNullSecret_ShouldReturnFalse() {
            String token = AdminJwtUtils.generateToken("admin", 1L, SECRET, EXPIRATION);

            assertFalse(AdminJwtUtils.validateToken(token, null));
        }

        @Test
        @DisplayName("空密钥生成 Token 时抛出 IllegalArgumentException")
        void generateToken_WithNullSecret_ShouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> AdminJwtUtils.generateToken("admin", 1L, null, EXPIRATION));
        }

        @Test
        @DisplayName("密钥长度不足（< 64 字节）生成时抛出 IllegalArgumentException")
        void generateToken_WithShortSecret_ShouldThrowException() {
            String shortSecret = "too_short_secret_for_hs512_algorithm_requirement";

            assertThrows(IllegalArgumentException.class,
                    () -> AdminJwtUtils.generateToken("admin", 1L, shortSecret, EXPIRATION));
        }
    }

    @Nested
    @DisplayName("getUsernameFromToken — 提取用户名")
    class GetUsernameFromToken {

        @Test
        @DisplayName("正常提取用户名")
        void getUsernameFromToken_WithValidToken_ShouldReturnCorrectUsername() {
            String token = AdminJwtUtils.generateToken("superadmin", 99L, SECRET, EXPIRATION);

            assertEquals("superadmin", AdminJwtUtils.getUsernameFromToken(token, SECRET));
        }
    }

    @Nested
    @DisplayName("getAdminIdFromToken — 提取管理员 ID")
    class GetAdminIdFromToken {

        @Test
        @DisplayName("正常提取管理员 ID（字符串形式）")
        void getAdminIdFromToken_WithValidToken_ShouldReturnCorrectId() {
            String token = AdminJwtUtils.generateToken("admin", 42L, SECRET, EXPIRATION);

            assertEquals("42", AdminJwtUtils.getAdminIdFromToken(token, SECRET));
        }
    }
}
