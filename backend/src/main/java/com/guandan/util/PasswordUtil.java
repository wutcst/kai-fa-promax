package com.guandan.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密工具类
 * 提供密码加密和验证的静态方法
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
