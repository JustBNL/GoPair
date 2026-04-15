package com.gopair.adminservice.context;

/**
 * 管理员会话上下文对象
 *
 * @author gopair
 */
public class AdminContext {

    private final Long adminId;
    private final String username;

    public AdminContext(Long adminId, String username) {
        this.adminId = adminId;
        this.username = username;
    }

    public Long getAdminId() {
        return adminId;
    }

    public String getUsername() {
        return username;
    }
}
