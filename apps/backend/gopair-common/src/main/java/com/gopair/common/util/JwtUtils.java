package com.gopair.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

/**
 * JWT工具类
 * 
 * 用于生成和验证JWT令牌，提供令牌的创建、解析和验证功能
 * 
 * @author gopair
 */
@Slf4j
public class JwtUtils {

    /** HS512 算法最小密钥长度（64 字节 = 512 bit） */
    private static final int HS512_MIN_KEY_LENGTH = 64;

    /**
     * 从令牌中获取昵称
     * 
     * @param token JWT令牌
     * @param secret 密钥
     * @return 昵称
     */
    public static String getNicknameFromToken(String token, String secret) {
        return getClaimFromToken(token, Claims::getSubject, secret);
    }

    /**
     * 从令牌中获取过期时间
     * 
     * @param token JWT令牌
     * @param secret 密钥
     * @return 过期时间
     */
    public static Date getExpirationDateFromToken(String token, String secret) {
        return getClaimFromToken(token, Claims::getExpiration, secret);
    }

    /**
     * 从令牌中获取指定的声明信息
     * 
     * @param token JWT令牌
     * @param claimsResolver 声明解析函数
     * @param secret 密钥
     * @return 声明信息
     */
    public static <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver, String secret) {
        final Claims claims = getAllClaimsFromToken(token, secret);
        return claimsResolver.apply(claims);
    }

    /**
     * 解析令牌获取所有声明信息
     * 
     * @param token JWT令牌
     * @param secret 密钥
     * @return 所有声明信息
     */
    private static Claims getAllClaimsFromToken(String token, String secret) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey(secret))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 获取签名密钥
     * 
     * @param secret 密钥字符串
     * @return 签名密钥
     */
    private static Key getSigningKey(String secret) {
        if (secret == null || secret.isEmpty()) {
            log.error("[JWT] 密钥不能为空，请检查 gopair.jwt.secret 配置");
            throw new IllegalArgumentException("JWT secret 不能为空");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < HS512_MIN_KEY_LENGTH) {
            log.error("[JWT] 密钥长度不合规，HS512 算法要求至少 {} 字节，当前 {} 字节", 
                    HS512_MIN_KEY_LENGTH, keyBytes.length);
            throw new IllegalArgumentException(
                    "JWT secret 长度不足，HS512 要求至少 " + HS512_MIN_KEY_LENGTH + " 字节");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成令牌
     * 
     * @param nickname 昵称
     * @param secret 密钥
     * @param expiration 过期时间（毫秒）
     * @return JWT令牌
     */
    public static String generateToken(String nickname, String secret, long expiration) {
        Map<String, Object> claims = new HashMap<>();
        return doGenerateToken(claims, nickname, secret, expiration);
    }

    /**
     * 生成令牌并包含用户ID
     * 
     * @param nickname 昵称
     * @param userId 用户ID
     * @param secret 密钥
     * @param expiration 过期时间（毫秒）
     * @return JWT令牌
     */
    public static String generateToken(String nickname, String userId, String secret, long expiration) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        return doGenerateToken(claims, nickname, secret, expiration);
    }

    /**
     * 生成令牌的核心方法
     * 
     * @param claims 声明信息
     * @param subject 主题（通常是昵称）
     * @param secret 密钥
     * @param expiration 过期时间（毫秒）
     * @return JWT令牌
     */
    private static String doGenerateToken(Map<String, Object> claims, String subject, String secret, long expiration) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(secret), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * 验证令牌（含签名校验）
     * 同时检查过期时间与签名有效性，任一失败均返回 false。
     * 内部避免重复解析：过期时间从已解析的 Claims 中直接读取。
     *
     * @param token JWT令牌
     * @param secret 密钥
     * @return 若令牌签名有效且未过期则返回 true，否则返回 false
     */
    public static Boolean validateToken(String token, String secret) {
        try {
            Claims claims = getAllClaimsFromToken(token, secret);
            Date expiration = claims.getExpiration();
            return !expiration.before(new Date());
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("[JWT] 令牌已过期: {}", e.getMessage());
            return false;
        } catch (io.jsonwebtoken.security.SecurityException e) {
            log.warn("[JWT] 签名验证失败: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("[JWT] 令牌校验异常: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 从令牌中获取用户ID
     *
     * @param token JWT令牌
     * @param secret 密钥
     * @return 用户ID，解析失败时返回 null
     */
    public static String getUserIdFromToken(String token, String secret) {
        try {
            Claims claims = getAllClaimsFromToken(token, secret);
            return claims.get("userId", String.class);
        } catch (Exception e) {
            log.warn("[JWT] 解析 userId 失败，Token 可能缺少 userId 声明: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 验证令牌（含签名校验）
     * 同时检查 nickname 与过期时间，任一失败均返回 false。
     *
     * @param token JWT令牌
     * @param nickname 昵称
     * @param secret 密钥
     * @return 如果令牌有效则返回true，否则返回false
     */
    public static Boolean validateToken(String token, String nickname, String secret) {
        try {
            Claims claims = getAllClaimsFromToken(token, secret);
            String tokenNickname = claims.getSubject();
            Date expiration = claims.getExpiration();
            return nickname.equals(tokenNickname) && !expiration.before(new Date());
        } catch (Exception e) {
            log.debug("[JWT] 三参数验证失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 从Cookie中验证JWT令牌
     * 
     * @param cookieValue Cookie值
     * @param secret 密钥
     * @return 如果令牌有效则返回true，否则返回false
     */
    public static Boolean validateTokenFromCookie(String cookieValue, String secret) {
        try {
            return validateToken(cookieValue, secret);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 从查询参数中验证JWT令牌
     * 
     * @param token 查询参数中的token
     * @param secret 密钥
     * @return 如果令牌有效则返回true，否则返回false
     */
    public static Boolean validateTokenFromQueryParam(String token, String secret) {
        try {
            return validateToken(token, secret);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 从WebSocket请求头中提取用户信息
     *
     * @param headers WebSocket请求头
     * @param secret JWT密钥
     * @return 包含用户信息的Map，包含userId和nickname
     */
    public static Map<String, Object> extractUserInfoFromWebSocketHeaders(Map<String, java.util.List<String>> headers, String secret) {
        Map<String, Object> userInfo = new HashMap<>();

        java.util.List<String> cookieHeaders = headers.get("cookie");
        if (cookieHeaders != null && !cookieHeaders.isEmpty()) {
            for (String cookieHeader : cookieHeaders) {
                String[] cookies = cookieHeader.split(";");
                for (String cookie : cookies) {
                    cookie = cookie.trim();
                    if (cookie.startsWith("token=")) {
                        String token = cookie.substring(6);
                        if (token.isEmpty()) {
                            continue;
                        }
                        try {
                            // 一次解析获取所有 Claims，避免重复解析
                            Claims claims = getAllClaimsFromToken(token, secret);
                            Date expiration = claims.getExpiration();
                            if (!expiration.before(new Date())) {
                                userInfo.put("userId", claims.get("userId", String.class));
                                userInfo.put("nickname", claims.getSubject());
                                userInfo.put("valid", true);
                                log.debug("[JWT] WebSocket Header 身份解析成功: userId={}", userInfo.get("userId"));
                                return userInfo;
                            } else {
                                log.debug("[JWT] WebSocket Header Cookie Token 已过期");
                            }
                        } catch (io.jsonwebtoken.ExpiredJwtException e) {
                            log.debug("[JWT] WebSocket Header Cookie Token 已过期");
                        } catch (io.jsonwebtoken.security.SecurityException e) {
                            log.debug("[JWT] WebSocket Header Cookie Token 签名无效");
                        } catch (Exception e) {
                            log.debug("[JWT] WebSocket Header Cookie Token 解析异常: {}", e.getMessage());
                        }
                    }
                }
            }
        }

        log.debug("[JWT] WebSocket Header 中未找到有效 Token");
        userInfo.put("valid", false);
        return userInfo;
    }
} 