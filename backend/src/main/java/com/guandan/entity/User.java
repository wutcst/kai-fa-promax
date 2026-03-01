package com.guandan.entity;

public class User {
    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String status = "ONLINE";

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setId(Long id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
