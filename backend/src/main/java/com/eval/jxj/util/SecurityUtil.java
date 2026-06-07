package com.eval.jxj.util;

import com.eval.jxj.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    public static LoginUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser) {
            return (LoginUser) auth.getPrincipal();
        }
        return null;
    }

    public static String getCurrentUserId() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    public static String getCurrentRole() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getRole() : null;
    }
}
