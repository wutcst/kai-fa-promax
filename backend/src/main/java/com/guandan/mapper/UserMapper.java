package com.guandan.mapper;

import com.guandan.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    @Insert("INSERT INTO users (username, password_hash, nickname, avatar_url, phone) VALUES (#{username}, #{passwordHash}, #{nickname}, #{avatarUrl}, #{phone})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Select("SELECT * FROM users WHERE username = #{username} AND is_deleted = 0")
    User findByUsername(String username);

    @Select("SELECT * FROM users WHERE id = #{id} AND is_deleted = 0")
    User selectById(Long id);
}
