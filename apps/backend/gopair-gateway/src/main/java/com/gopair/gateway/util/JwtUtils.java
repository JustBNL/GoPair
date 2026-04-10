package com.gopair.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * 网关专用 JWT 工具类
 *
 * 提供批量提取 userId + nickname 的单次解析方法，避免重复验签。
 *
 * @author gopair
 */
public class JwtUtils {

    /**
     * JWT 解析结果容器，一次解析同时返回 userId 和 nickname。
     */
    public record JwtUserInfo(String userId, String nickname) {}

    /**
     * 一次解析同时获取 userId 和 nickname，避免重复验签。
     *
     * @param token  JWT 令牌
     * @param secret 密钥
     * @return JwtUserInfo 或 null（解析失败时）
     */
    public static JwtUserInfo getUserInfoFromToken(String token, String secret) {
        try {
            Claims claims = parseClaims(token, secret);
            String userId = claims.get("userId", String.class);
            String nickname = claims.getSubject();
            return new JwtUserInfo(userId, nickname);
        } catch (Exception e) {
            return null;
        }
    }

    private static Claims parseClaims(String token, String secret) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
