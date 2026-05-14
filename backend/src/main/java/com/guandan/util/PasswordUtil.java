package com.guandan.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密工具类
 *
 * 基于 Spring Security 的 BCryptPasswordEncoder 实现密码加密与验证。
 * BCrypt 算法自动加盐，加密结果不可逆且相同密码每次加密结果不同。
 *
 * 使用场景：
 * - 用户注册时对明文密码进行加密后存储
 * - 用户登录时验证明文密码与数据库密文是否匹配
 *
 * 安全说明：
 * - BCrypt 内建 salt 机制，无需额外处理
 * - 推荐的生产环境密码存储方案
 *
 * 接口字段：
 * - hashPassword(String) → String 加密后密文
 * - checkPassword(String, String) → boolean 验证结果
 *
 * 异常场景：
 * - 入参为 null 或空字符串时 BCrypt 会抛出 IllegalArgumentException
 */
public class PasswordUtil {

    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 对明文密码进行BCrypt加密
     *
     * @param plainPassword 明文密码
     * @return 加密后的密文
     */
    public static String hashPassword(String plainPassword) {
        return passwordEncoder.encode(plainPassword);
    }

    /**
     * 验证明文密码是否与密文匹配
     *
     * @param plainPassword  明文密码
     * @param hashedPassword 加密后的密文
     * @return 匹配返回true，否则false
     */
    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        return passwordEncoder.matches(plainPassword, hashedPassword);
    }
}
