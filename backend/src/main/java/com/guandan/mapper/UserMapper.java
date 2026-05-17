package com.guandan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guandan.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户数据访问接口
 * <p>
 * 继承 MyBatis-Plus BaseMapper，提供基础的 CRUD 操作。
 * 复杂查询可通过注解 SQL 或 XML 映射文件扩展。
 * <p>
 * ── 维护说明 ──────────────────────────────────────────────
 * 1. 自定义查询方法命名规范：findBy{字段名} / countBy{字段名}
 * 2. 所有查询默认过滤逻辑删除（deleted = 0），
 *    除非明确需要查询已删除记录（方法名后缀 IncludeDeleted）
 * 3. 批量操作使用 BaseMapper 提供的默认方法，
 *    非必要不手写 SQL
 * 4. 新增查询方法时必须添加对应的单测用例
 * 5. 涉及联表查询的放在对应业务领域的 Mapper 中，
 *    不跨领域在 UserMapper 中引用其他表
 * ─────────────────────────────────────────────────────────
 * <p>
 * 回归验证点：
 * 1. BaseMapper<User>.insert() 插入用户并返回自增ID
 * 2. BaseMapper<User>.selectById() 按主键查询，不存在返回null
 * 3. UserMapper.findByUsername() 按用户名查询，过滤已删除
 * 4. username字段有UNIQUE约束，重复插入抛异常
 * <p>
 * 异常场景：
 * - 插入时 username 违反 UNIQUE 约束 → 抛 DuplicateKeyException
 * - 查询不存在记录 → 返回 null
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名（6位数字）
     * @return 用户实体或null
     */
    @Select("SELECT * FROM user WHERE username = #{username} AND (deleted IS NULL OR deleted = 0)")
    User findByUsername(String username);
}
