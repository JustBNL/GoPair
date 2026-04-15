package com.gopair.adminservice.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 管理员 JWT 工具类
 *
 * @author gopair
 */
@Slf4j
public class AdminJwtUtils {

    private static final int HS512_MIN_KEY_LENGTH = 64;

    public static String getUsernameFromToken(String token, String secret) {
        return getClaimFromToken(token, Claims::getSubject, secret);
    }

    public static String getAdminIdFromToken(String token, String secret) {
        return getClaimFromToken(token, claims -> claims.get("adminId", String.class), secret);
    }

    private static <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver, String secret) {
        final Claims claims = getAllClaimsFromToken(token, secret);
        return claimsResolver.apply(claims);
    }

    private static Claims getAllClaimsFromToken(String token, String secret) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey(secret))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private static Key getSigningKey(String secret) {
        if (secret == null || secret.isEmpty()) {
            log.error("[AdminJWT] 密钥不能为空，请检查 gopair.admin.jwt.secret 配置");
            throw new IllegalArgumentException("Admin JWT secret 不能为空");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < HS512_MIN_KEY_LENGTH) {
            log.error("[AdminJWT] 密钥长度不合规，HS512 算法要求至少 {} 字节", HS512_MIN_KEY_LENGTH);
            throw new IllegalArgumentException(
                    "Admin JWT secret 长度不足，HS512 要求至少 " + HS512_MIN_KEY_LENGTH + " 字节");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public static String generateToken(String username, Long adminId, String secret, long expiration) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("adminId", adminId.toString());
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(secret), SignatureAlgorithm.HS512)
                .compact();
    }

    public static Boolean validateToken(String token, String secret) {
        try {
            Claims claims = getAllClaimsFromToken(token, secret);
            return !claims.getExpiration().before(new Date());
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("[AdminJWT] 令牌已过期: {}", e.getMessage());
            return false;
        } catch (io.jsonwebtoken.security.SecurityException e) {
            log.warn("[AdminJWT] 签名验证失败: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("[AdminJWT] 令牌校验异常: {}", e.getMessage());
            return false;
        }
    }
}
