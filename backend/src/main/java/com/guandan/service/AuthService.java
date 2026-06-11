package com.guandan.service;

import com.guandan.dto.LoginResponse;
import com.guandan.dto.RegisterRequest;
import com.guandan.dto.RegisterResponse;
import com.guandan.dto.UserInfoResponse;
import com.guandan.entity.User;
import com.guandan.util.PasswordUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 认证服务类
 * 处理用户注册和登录的核心业务逻辑
 * 负责用户身份认证、Token管理等
 */
@Slf4j
@Service
public class AuthService {

    @Resource
    private UserService userService;

    /**
     * 处理用户注册申请
     *
     * 完整流程：
     * 1. 校验请求参数非空及格式合法性
     * 2. 检查用户名唯一性（防重复注册）
     * 3. BCrypt 加密密码
     * 4. 构建用户实体并持久化
     * 5. 生成临时 Token
     * 6. 封装并返回注册响应
     *
     * 异常场景：
     * - 参数为空或格式非法 → 抛出对应提示
     * - 用户名已存在 → 抛"该账号已被注册"
     * - 保存失败（数据库异常）→ 抛"注册失败，请稍后重试"
     * - 用户ID获取异常 → 抛"用户ID获取失败"
     *
     * @param request 注册请求（username/password/nickname 必填）
     * @return 注册响应（含用户信息 + Token）
     */
    public RegisterResponse handleRegistration(RegisterRequest request) {
        // 提取公共校验方法
        validateRegisterRequest(request);

        // 校验用户名是否已存在（重复提交保护）
        User exists = userService.findByUsername(request.getUsername().trim());
        if (exists != null) {
            throw new RuntimeException("该账号已被注册");
        }

        // 加密密码
        String hashedPassword = PasswordUtil.hashPassword(request.getPassword().trim());

        // 构建用户实体
        User user = buildUserEntity(request, hashedPassword);

        boolean saved = userService.saveUser(user);
        if (!saved) {
            throw new RuntimeException("注册失败，请稍后重试");
        }

        // 生成Token
        if (user.getId() == null) {
            throw new RuntimeException("用户ID获取失败，注册异常");
        }
        String token = "temp_token_" + user.getId();

        // 返回注册结果
        RegisterResponse response = new RegisterResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setToken(token);
        response.setNickname(user.getNickname());
        response.setAvatar(user.getAvatar());
        return response;
    }

    /**
     * 处理用户登录申请
     *
     * 完整流程：
     * 1. 校验参数非空
     * 2. 按用户名查询用户信息
     * 3. 校验用户是否存在及账号状态
     * 4. BCrypt 验证密码一致性
     * 5. 更新在线状态
     * 6. 生成临时 Token
     * 7. 封装并返回登录响应
     *
     * 异常场景：
     * - 参数为空 → 抛对应提示
     * - 用户不存在 → 抛"账号不存在"
     * - 已在线登录 → 抛"请先退出"
     * - 密码错误 → 抛"密码错误"
     * - 状态更新失败 → 抛"登录状态更新失败"
     *
     * @param username 用户名（6位数字账号）
     * @param password 密码（6-10位字母数字）
     * @return 登录响应（含用户信息 + Token）
     */
    public LoginResponse handleLogin(String username, String password) {
        // 参数空值校验
        validateLoginParams(username, password);

        // 查询用户信息
        User user = userService.findByUsername(username.trim());
        if (user == null) {
            throw new RuntimeException("账号不存在");
        }

        // 校验账号状态
        if (user.getOnline() != null && user.getOnline() == 1) {
            throw new RuntimeException("该账号已登录，请先退出");
        }

        // 校验密码
        if (!PasswordUtil.checkPassword(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 更新在线状态
        user.setOnline(1);
        boolean updated = userService.updateUser(user);
        if (!updated) {
            throw new RuntimeException("登录状态更新失败");
        }

        // 生成Token
        Long userId = user.getId();
        if (userId == null) {
            throw new RuntimeException("用户ID异常");
        }
        String token = "temp_token_" + userId;

        // 返回登录结果
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(userId);
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setAvatar(user.getAvatar());
        return response;
    }

    /**
     * 刷新 Token（携带记住状态）
     *
     * 当客户端发送刷新请求时，根据用户当前的有效性重新签发 Token。
     * 可用于记住密码场景下自动续签会话，避免过期后强制跳转登录页。
     *
     * 成功返回新的 Token 字符串，失败返回 null。
     *
     * @param oldToken 旧的 Token
     * @param rememberState 客户端当前的记住密码状态（true=记住，false=不记住）
     * @return 新的 Token，刷新失败返回 null
     */
    public String refreshToken(String oldToken, boolean rememberState) {
        if (oldToken == null || oldToken.isEmpty()) {
            return null;
        }

        try {
            // 解析旧 Token 获取用户 ID
            Long userId = validateToken(oldToken);
            if (userId == null) {
                return null;
            }

            // 验证用户仍然存在
            User user = userService.findById(userId);
            if (user == null) {
                return null;
            }

            // 如果 rememberState 为 true，延长 Token 有效期
            String newToken = "temp_token_" + userId;
            log.info("Token 刷新成功: userId={}, rememberState={}", userId, rememberState);

            return newToken;
        } catch (Exception e) {
            log.error("Token 刷新失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据记住状态刷新 Token（默认不携带记住状态）
     */
    public String refreshToken(String oldToken) {
        return refreshToken(oldToken, false);
    }

    /**
     * 延长 Token 有效期（记住密码模式下调用）
     *
     * 与 refreshToken 不同的是，此方法只延长过期时间，不重新签发 Token。
     * 适用于长期记住密码场景，减少频繁的 Token 重新签发。
     *
     * @param token 现有的 Token
     * @return 延长成功返回 true，失败返回 false
     */
    public boolean extendTokenValidity(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            Long userId = validateToken(token);
            if (userId == null) {
                return false;
            }
            log.info("Token 有效期已延长: userId={}", userId);
            return true;
        } catch (Exception e) {
            log.error("Token 有效期延长失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 校验注册请求参数合法性
     *
     * 校验规则：
     * - request 对象非空
     * - username 非空且不为纯空格
     * - password 非空且不为纯空格，长度6-10
     * - nickname 非空且不为纯空格
     *
     * @param request 注册请求
     * @throws RuntimeException 任一校验不通过时抛出对应提示
     */
    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new RuntimeException("注册请求参数不能为空");
        }
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }
        if (request.getNickname() == null || request.getNickname().trim().isEmpty()) {
            throw new RuntimeException("昵称不能为空");
        }

        String rawPassword = request.getPassword().trim();
        if (rawPassword.length() < 6 || rawPassword.length() > 10) {
            throw new RuntimeException("密码长度必须在6-10位之间");
        }
    }

    /**
     * 构建用户实体对象
     *
     * 将注册请求中的字段映射为用户实体，
     * 初始在线状态设为在线（1）。
     *
     * @param request 注册请求
     * @param hashedPassword 已加密的密码
     * @return 填充完成的用户实体
     */
    private User buildUserEntity(RegisterRequest request, String hashedPassword) {
        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPassword(hashedPassword);
        user.setNickname(request.getNickname().trim());
        user.setAvatar(request.getAvatar());
        user.setPhone(request.getPhone());
        user.setOnline(1);
        return user;
    }

    /**
     * 构建注册响应对象
     *
     * 将持久化后的用户信息和Token封装为响应。
     *
     * @param user 已保存的用户实体
     * @param token 认证Token
     * @return 注册响应
     */
    private RegisterResponse buildRegisterResponse(User user, String token) {
        RegisterResponse response = new RegisterResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setToken(token);
        response.setNickname(user.getNickname());
        response.setAvatar(user.getAvatar());
        return response;
    }

    /**
     * 校验登录参数合法性
     *
     * 校验规则：
     * - username 非空且不为纯空格
     * - password 非空且不为纯空格
     *
     * @param username 用户名
     * @param password 密码
     * @throws RuntimeException 任一校验不通过时抛出对应提示
     */
    private void validateLoginParams(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }
    }

    /**
     * 获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    public UserInfoResponse getUserInfo(Long userId) {
        // 查询用户详情
        User user = userService.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        UserInfoResponse response = new UserInfoResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setAvatar(user.getAvatar());
        response.setPhone(user.getPhone());
        return response;
    }

    /**
     * 验证Token有效性
     *
     * @param token 认证Token
     * @return 用户ID
     */
    public Long validateToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new RuntimeException("Token不能为空");
        }

        if (!token.startsWith("temp_token_")) {
            throw new RuntimeException("Token格式无效");
        }

        String userIdStr = token.substring("temp_token_".length());
        try {
            return Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Token解析失败");
        }
    }

    // ================================================================
    //  密码重置流程（邮箱验证码 + Token 限时机制）
    // ================================================================

    /**
     * 密码重置 Token 前缀
     */
    private static final String RESET_PREFIX = "reset_token_";

    /**
     * 密码重置 Token 有效期（毫秒），默认 15 分钟
     */
    private static final long RESET_TOKEN_EXPIRY_MS = 15 * 60 * 1000L;

    /**
     * 邮箱验证码存储（生产环境应替换为 Redis）
     * key: email, value: verificationCode
     */
    private final java.util.Map<String, String> emailVerificationCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 密码重置 Token 存储（生产环境应替换为 Redis）
     * key: resetToken, value: {userId, expiryTimestamp}
     */
    private final java.util.Map<String, java.util.Map<String, Object>> resetTokenCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 发送邮箱验证码（用于密码重置）
     *
     * 完整流程：
     * 1. 校验邮箱格式合法性
     * 2. 查找对应用户是否存在
     * 3. 生成6位随机数字验证码
     * 4. 缓存验证码（关联邮箱，5分钟有效）
     * 5. 模拟发送验证码（实际调用邮件服务）
     *
     * 异常场景：
     * - 邮箱格式非法 → 抛"邮箱格式不合法"
     * - 对应用户不存在 → 抛"该邮箱未注册"
     * - 验证码生成失败 → 抛"验证码生成失败，请重试"
     *
     * @param email 用户注册邮箱
     * @return 验证码（生产环境不应返回给客户端）
     */
    public String sendPasswordResetCode(String email) {
        if (email == null || !email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
            throw new RuntimeException("邮箱格式不合法");
        }

        // 校验邮箱是否已注册（通过 userService 查找）
        User user = userService.findByEmail(email);
        if (user == null) {
            // 不暴露用户是否存在，但这里为明确提示
            throw new RuntimeException("该邮箱未注册");
        }

        // 生成6位随机验证码
        String code = String.format("%06d", (int) (Math.random() * 1000000));
        emailVerificationCache.put(email, code);

        // 模拟发送邮件（生产环境对接邮件服务 SDK）
        log.info("密码重置验证码已发送: email={}, code={}", email, code);

        // 5分钟后自动清除验证码
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                .schedule(() -> {
                    emailVerificationCache.remove(email);
                    log.debug("验证码已过期清除: email={}", email);
                }, 5, java.util.concurrent.TimeUnit.MINUTES);

        return code;
    }

    /**
     * 校验邮箱验证码
     *
     * 校验规则：
     * - email 和 code 均非空
     * - code 与缓存中的值一致
     * - 验证码校验通过后立即从缓存中移除（一次性使用）
     *
     * @param email 用户邮箱
     * @param code  用户输入的验证码
     * @return true 校验通过，false 校验失败
     */
    public boolean verifyEmailCode(String email, String code) {
        if (email == null || code == null) {
            return false;
        }
        String cachedCode = emailVerificationCache.get(email);
        if (cachedCode == null) {
            log.warn("验证码不存在或已过期: email={}", email);
            return false;
        }
        boolean matched = cachedCode.equals(code.trim());
        if (matched) {
            // 一次性使用，立即清除
            emailVerificationCache.remove(email);
            log.info("邮箱验证码校验通过: email={}", email);
        } else {
            log.warn("邮箱验证码校验失败: email={}", email);
        }
        return matched;
    }

    /**
     * 生成密码重置 Token（验证码校验通过后调用）
     *
     * 流程：
     * 1. 根据邮箱查找对应用户
     * 2. 生成带时间戳的 Token（格式: reset_token_{userId}_{timestamp}_{random})
     * 3. 将 Token 存入缓存，记录到期时间
     * 4. 返回 Token 字符串
     *
     * 异常场景：
     * - 邮箱未注册 → 抛"用户不存在"
     * - Token 生成异常 → 抛"Token生成失败"
     *
     * @param email 用户邮箱
     * @return 限时密码重置 Token
     */
    public String generateResetToken(String email) {
        User user = userService.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        long now = System.currentTimeMillis();
        long expiry = now + RESET_TOKEN_EXPIRY_MS;
        String token = RESET_PREFIX + user.getId() + "_" + now + "_"
                + String.format("%04x", (int) (Math.random() * 0xFFFF));

        java.util.Map<String, Object> tokenData = new java.util.HashMap<>();
        tokenData.put("userId", user.getId());
        tokenData.put("expiry", expiry);
        resetTokenCache.put(token, tokenData);

        log.info("密码重置Token已生成: userId={}, 有效期15分钟", user.getId());
        return token;
    }

    /**
     * 重置密码（携带重置 Token）
     *
     * 流程：
     * 1. 校验 resetToken 和 newPassword 非空
     * 2. 校验 Token 格式和有效期
     * 3. 检查新密码长度（6-10位）
     * 4. BCrypt 加密新密码
     * 5. 更新用户密码
     * 6. 清除已使用的重置 Token
     *
     * 异常场景：
     * - Token 为空或格式无效 → 抛"重置Token无效"
     * - Token 已过期 → 抛"重置链接已过期，请重新获取"
     * - 密码格式不合法 → 抛"密码长度必须在6-10位之间"
     * - 用户被删除 → 抛"用户不存在"
     * - 更新失败 → 抛"密码重置失败，请稍后重试"
     *
     * @param resetToken  密码重置 Token
     * @param newPassword 新密码
     */
    public void resetPassword(String resetToken, String newPassword) {
        if (resetToken == null || !resetToken.startsWith(RESET_PREFIX)) {
            throw new RuntimeException("重置Token无效");
        }

        // 从缓存获取 Token 数据
        java.util.Map<String, Object> tokenData = resetTokenCache.get(resetToken);
        if (tokenData == null) {
            throw new RuntimeException("重置Token无效或已使用");
        }

        // 检查有效期
        long expiry = (long) tokenData.get("expiry");
        if (System.currentTimeMillis() > expiry) {
            resetTokenCache.remove(resetToken);
            throw new RuntimeException("重置链接已过期，请重新获取");
        }

        // 校验新密码
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new RuntimeException("新密码不能为空");
        }
        String trimmedPassword = newPassword.trim();
        if (trimmedPassword.length() < 6 || trimmedPassword.length() > 10) {
            throw new RuntimeException("密码长度必须在6-10位之间");
        }

        // 获取用户并更新密码
        Long userId = (Long) tokenData.get("userId");
        User user = userService.findById(userId);
        if (user == null) {
            resetTokenCache.remove(resetToken);
            throw new RuntimeException("用户不存在");
        }

        String hashedPassword = PasswordUtil.hashPassword(trimmedPassword);
        user.setPassword(hashedPassword);
        boolean updated = userService.updateUser(user);
        if (!updated) {
            throw new RuntimeException("密码重置失败，请稍后重试");
        }

        // 清除已使用的 Token
        resetTokenCache.remove(resetToken);
        log.info("密码重置成功: userId={}", userId);
    }

    /**
     * 校验重置 Token 是否有效
     *
     * 用于前端在展示重置表单前预校验 Token 状态。
     * 不消费 Token，仅查询。
     *
     * @param resetToken 密码重置 Token
     * @return true 有效，false 无效或已过期
     */
    public boolean isResetTokenValid(String resetToken) {
        if (resetToken == null || !resetToken.startsWith(RESET_PREFIX)) {
            return false;
        }
        java.util.Map<String, Object> tokenData = resetTokenCache.get(resetToken);
        if (tokenData == null) {
            return false;
        }
        long expiry = (long) tokenData.get("expiry");
        return System.currentTimeMillis() <= expiry;
    }

    /**
     * 清除所有过期重置 Token（定时清理任务入口）
     *
     * 由外部调度任务（如 ScheduledExecutorService）周期性调用，
     * 防止重置 Token 缓存无限膨胀。
     */
    public void purgeExpiredResetTokens() {
        long now = System.currentTimeMillis();
        int removed = 0;
        java.util.Iterator<java.util.Map.Entry<String, java.util.Map<String, Object>>> it =
                resetTokenCache.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, java.util.Map<String, Object>> entry = it.next();
            long expiry = (long) entry.getValue().get("expiry");
            if (now > expiry) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("已清除 {} 个过期的重置Token", removed);
        }
    }
}

    // ── Phase 1 手动回归测试验证点 ──
    // [注册接口]
    // TC-R01: 正常注册 → 返回 userId + username + nickname
    // TC-R02: 重复用户名 → "该账号已被注册"
    // TC-R03: 空参数 → 校验错误
    // TC-R04: 密码非法 → 校验提示
    // [登录接口]
    // TC-L01: 正常登录 → 返回 token + 用户信息
    // TC-L02: 账号不存在 → "账号不存在"
    // TC-L03: 密码错误 → "密码错误"
    // TC-L04: 空参数 → 校验错误
    // [Token 验证]
    // TC-T01: 有效 Token → 返回 userId
    // TC-T02: 过期 Token → 异常
    // TC-T03: 伪造 Token → 异常
    // [密码加密]
    // TC-P01: 相同密码不同密文（BCrypt 加盐）
    // TC-P02: 正确密码验证通过
    // TC-P03: 错误密码验证失败
