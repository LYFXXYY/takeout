package com.sky.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * ============================================================================
 *  JWT 工具类 单元测试（JwtUtil）
 * ----------------------------------------------------------------------------
 *  【为什么单独测工具类】
 *  JwtUtil 是纯静态工具，没有外部依赖，是入门单元测试最好的"零 Mock"例子——
 *  不需要 @Mock，直接调用静态方法、直接断言返回值即可。
 *
 *  【测什么】
 *    1) 往返一致性：生成的 token 用同一个密钥能解析回原始信息
 *    2) 安全性：用错误密钥解析必须失败（防止伪造 token）
 *    3) 过期：超过有效期的 token 解析必须失败
 *
 *  【注意一个小坑】
 *  jjwt 会把数字类型的 claim 反序列化成 Integer，而不是我们放进去的 Long。
 *  所以断言 empId 时用 ((Number) ...).longValue() 转一下再比，别直接和 1L 比对象。
 * ============================================================================
 */
@DisplayName("JWT 工具类测试")
class JwtUtilTest {

    private static final String SECRET = "itcast"; // 与项目配置里的 admin-secret-key 一致，HS256 短密钥在此版本可用
    private static final long TTL = 7200000L;      // 2 小时（毫秒）

    @Test
    @DisplayName("往返：用相同密钥生成的 token 能被正确解析")
    void should_parseClaimsBack_when_createdWithSameSecret() {
        // Given：往 token 里塞一个 empId=1 的信息
        Map<String, Object> claims = new HashMap<>();
        claims.put("empId", 1L);

        // When：生成再用同一密钥解析
        String token = JwtUtil.createJWT(SECRET, TTL, claims);
        Claims parsed = JwtUtil.parseJWT(SECRET, token);

        // Then：能拿到原始的 empId
        assertNotNull(token);
        assertNotNull(parsed);
        assertEquals(1L, ((Number) parsed.get("empId")).longValue());
    }

    @Test
    @DisplayName("安全：用错误的密钥解析 token 应抛异常")
    void should_throw_when_parsedWithWrongSecret() {
        // Given
        Map<String, Object> claims = new HashMap<>();
        claims.put("empId", 1L);
        String token = JwtUtil.createJWT(SECRET, TTL, claims);

        // When & Then：换一个密钥去解析，签名对不上，必须失败
        assertThrows(
                JwtException.class,
                () -> JwtUtil.parseJWT("wrong-secret", token)
        );
    }

    @Test
    @DisplayName("过期：超过有效期的 token 解析应抛异常")
    void should_throw_when_tokenExpired() {
        // Given：把有效期设成负数，相当于生成即过期
        Map<String, Object> claims = new HashMap<>();
        claims.put("empId", 1L);
        String expired = JwtUtil.createJWT(SECRET, -1000L, claims);

        // When & Then
        assertThrows(
                JwtException.class,
                () -> JwtUtil.parseJWT(SECRET, expired)
        );
    }
}
