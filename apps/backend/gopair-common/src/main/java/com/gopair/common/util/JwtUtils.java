package com.gopair.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

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
@Component
public class JwtUtils {

    @Value("${gopair.jwt.secret}")
    private String secret;

    @Value("${gopair.jwt.expiration}")
    private long expiration;
    
    // 静态实例，用于静态方法调用
    private static JwtUtils instance;
    
    @PostConstruct
    public void init() {
        instance = this;
    }

    /**
     * 从令牌中获取用户名
     * 
     * @param token JWT令牌
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    /**
     * 从令牌中获取过期时间
     * 
     * @param token JWT令牌
     * @return 过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * 从令牌中获取指定的声明信息
     * 
     * @param token JWT令牌
     * @param claimsResolver 声明解析函数
     * @return 声明信息
     */
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    /**
     * 解析令牌获取所有声明信息
     * 
     * @param token JWT令牌
     * @return 所有声明信息
     */
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 获取签名密钥
     * 
     * @return 签名密钥
     */
    private Key getSigningKey() {
        byte[] keyBytes = secret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 检查令牌是否已过期
     * 
     * @param token JWT令牌
     * @return 如果令牌已过期则返回true，否则返回false
     */
    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    /**
     * 生成令牌
     * 
     * @param username 用户名
     * @return JWT令牌
     */
    public String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        return doGenerateToken(claims, username);
    }

    /**
     * 生成令牌的核心方法
     * 
     * @param claims 声明信息
     * @param subject 主题（通常是用户名）
     * @return JWT令牌
     */
    private String doGenerateToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * 验证令牌（静态方法）
     * 
     * @param token JWT令牌
     * @return 如果令牌有效则返回true，否则返回false
     */
    public static Boolean validateToken(String token) {
        try {
            return instance != null && !instance.isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 从令牌中获取用户名（静态方法）
     * 
     * @param token JWT令牌
     * @return 用户名
     */
    public static String getUsernameFromTokenStatic(String token) {
        try {
            return instance != null ? instance.getUsernameFromToken(token) : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 从令牌中获取用户ID（静态方法）
     * 
     * @param token JWT令牌
     * @return 用户ID
     */
    public static String getUserIdFromToken(String token) {
        try {
            if (instance != null) {
                Claims claims = instance.getAllClaimsFromToken(token);
                return claims.get("userId", String.class);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 验证令牌
     * 
     * @param token JWT令牌
     * @param username 用户名
     * @return 如果令牌有效则返回true，否则返回false
     */
    public Boolean validateToken(String token, String username) {
        final String tokenUsername = getUsernameFromToken(token);
        return (username.equals(tokenUsername) && !isTokenExpired(token));
    }
} 