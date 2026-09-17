package com.rxt.security;

/**
 * 当前登录用户上下文。由 LoginInterceptor 在请求进入时写入、请求结束时清理。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long currentUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static String currentRole() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getRoleName();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 登录态载体。
     */
    public static class LoginUser {

        private Long userId;
        private String phone;
        private String roleName;

        public LoginUser() {
        }

        public LoginUser(Long userId, String phone, String roleName) {
            this.userId = userId;
            this.phone = phone;
            this.roleName = roleName;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }
    }
}
