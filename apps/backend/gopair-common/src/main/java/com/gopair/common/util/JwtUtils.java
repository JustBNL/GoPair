package com.gopair.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT工具类
 * 
 * 用于生成和验证JWT令牌，提供令牌的创建、解析和验证功能
 * 
 * @author gopair
 */
public class JwtUtils {

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
        byte[] keyBytes = secret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 检查令牌是否已过期
     * 
     * @param token JWT令牌
     * @param secret 密钥
     * @return 如果令牌已过期则返回true，否则返回false
     */
    private static Boolean isTokenExpired(String token, String secret) {
        final Date expiration = getExpirationDateFromToken(token, secret);
        return expiration.before(new Date());
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
     *
     * @param token JWT令牌
     * @param secret 密钥
     * @return 若令牌签名有效且未过期则返回 true，否则返回 false
     */
    public static Boolean validateToken(String token, String secret) {
        try {
            getAllClaimsFromToken(token, secret);
            return !isTokenExpired(token, secret);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 从令牌中获取用户ID
     * 
     * @param token JWT令牌
     * @param secret 密钥
     * @return 用户ID
     */
    public static String getUserIdFromToken(String token, String secret) {
        try {
            Claims claims = getAllClaimsFromToken(token, secret);
            return claims.get("userId", String.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 验证令牌
     * 
     * @param token JWT令牌
     * @param nickname 昵称
     * @param secret 密钥
     * @return 如果令牌有效则返回true，否则返回false
     */
    public static Boolean validateToken(String token, String nickname, String secret) {
        final String tokenNickname = getNicknameFromToken(token, secret);
        return (nickname.equals(tokenNickname) && !isTokenExpired(token, secret));
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
        
        // 尝试从Cookie中获取JWT
        java.util.List<String> cookieHeaders = headers.get("cookie");
        if (cookieHeaders != null && !cookieHeaders.isEmpty()) {
            for (String cookieHeader : cookieHeaders) {
                String[] cookies = cookieHeader.split(";");
                for (String cookie : cookies) {
                    cookie = cookie.trim();
                    if (cookie.startsWith("token=")) {
                        String token = cookie.substring(6);
                        if (validateToken(token, secret)) {
                            userInfo.put("userId", getUserIdFromToken(token, secret));
                            userInfo.put("nickname", getNicknameFromToken(token, secret));
                            userInfo.put("valid", true);
                            return userInfo;
                        }
                    }
                }
            }
        }
        
        userInfo.put("valid", false);
        return userInfo;
    }
} 