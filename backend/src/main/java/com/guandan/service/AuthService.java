package com.guandan.service;

import com.guandan.dto.LoginResponse;
import com.guandan.dto.RegisterRequest;
import com.guandan.dto.RegisterResponse;
import com.guandan.dto.UserInfoResponse;
import com.guandan.entity.User;
import com.guandan.util.PasswordUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 认证服务类
 * 处理用户注册和登录的核心业务逻辑
 * 负责用户身份认证、Token管理等
 */
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
}
