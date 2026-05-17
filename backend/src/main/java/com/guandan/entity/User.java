package com.guandan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户实体
 *
 * 对应数据库 user 表，存储用户账号信息和基本资料。
 * 密码字段存储 BCrypt 加密后的密文，不存明文。
 *
 * 回归验证点：
 * 1. 字段username唯一约束，长度50
 * 2. 密码字段长度100，存储加密密文
 * 3. online字段默认0（离线）
 * 4. deleted字段用于逻辑删除
 * 5. create_time自动填充创建时间
 */
@Data
@TableName("user")
public class User {

    /** 用户ID（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名（6位纯数字系统分配账号） */
    private String username;

    /** 密码（BCrypt加密） */
    private String password;

    /** 昵称（显示用） */
    private String nickname;

    /** 头像（Base64 SVG / URL） */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 在线状态：0-离线，1-在线 */
    private Integer online;

    /** 逻辑删除标记：0-正常，1-已删除 */
    private Integer deleted;

    /** 创建时间 */
    private java.time.LocalDateTime createTime;

    /**
     * 检查用户账号是否正常可用
     *
     * @return true=可用，false=已删除或不存在
     */
    public boolean isActive() {
        return deleted == null || deleted == 0;
    }
}
