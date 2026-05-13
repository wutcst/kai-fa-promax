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
}
