package com.guandan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guandan.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户数据访问接口
 *
 * 继承 MyBatis-Plus BaseMapper，提供基础的 CRUD 操作。
 * 复杂查询可通过注解 SQL 或 XML 映射文件扩展。
 *
 * 接口字段说明：
 * - BaseMapper<User>.insert(User) → int 插入记录
 * - BaseMapper<User>.selectById(Long) → User 按ID查询
 * - BaseMapper<User>.selectOne(Wrapper) → User 按条件查询单条
 * - UserMapper.findByUsername(String) → User 按用户名查询
 *
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
