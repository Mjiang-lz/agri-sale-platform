package com.rxt.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌工具。密钥与有效期来自配置项 rxt.jwt.*。
 */
@Slf4j
@Component
public class JwtUtils {

    private final SecretKey secretKey;
    private final long expireMillis;

    public JwtUtils(@Value("${rxt.jwt.secret}") String secret,
                    @Value("${rxt.jwt.expire-hours:24}") long expireHours) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 60 * 60 * 1000;
    }

    /**
     * 签发令牌。
     */
    public String generateToken(Long userId, String phone, String roleName) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("phone", phone)
                .claim("roleName", roleName)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析令牌，非法或过期返回 null。
     */
    public UserContext.LoginUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new UserContext.LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("phone", String.class),
                    claims.get("roleName", String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("令牌解析失败: {}", ex.getMessage());
            return null;
        }
    }
}
