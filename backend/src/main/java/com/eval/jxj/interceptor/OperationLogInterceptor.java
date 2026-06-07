package com.eval.jxj.interceptor;

import com.eval.jxj.entity.OperationLog;
import com.eval.jxj.security.LoginUser;
import com.eval.jxj.service.OperationLogService;
import com.eval.jxj.util.SecurityUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 自动记录所有变更类请求(POST/PUT/DELETE)到 operation_log。
 * 登录(/api/auth/login)单独在 AuthServiceImpl 内埋点，这里跳过。
 */
@Component
public class OperationLogInterceptor implements HandlerInterceptor {

    private final OperationLogService operationLogService;

    public OperationLogInterceptor(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            String method = request.getMethod();
            if (!isMutating(method)) {
                return;
            }
            String uri = request.getRequestURI();
            if (uri == null || !uri.startsWith("/api/")) {
                return;
            }
            // 登录单独埋点；刷新令牌无业务意义，忽略
            if (uri.equals("/api/auth/login") || uri.equals("/api/auth/refresh")) {
                return;
            }

            OperationLog logEntity = new OperationLog();
            LoginUser user = SecurityUtil.getCurrentUser();
            if (user != null) {
                logEntity.setOperatorId(user.getId());
                logEntity.setOperatorLoginId(user.getLoginId());
                logEntity.setOperatorName(user.getName());
                logEntity.setOperatorRole(user.getRole());
            }
            logEntity.setModule(resolveModule(uri));
            logEntity.setAction(resolveAction(method, uri));
            logEntity.setMethod(method);
            logEntity.setUri(uri);
            logEntity.setParams(truncate(request.getQueryString(), 1000));
            logEntity.setIp(resolveIp(request));

            boolean ok = ex == null && response.getStatus() < 400;
            logEntity.setSuccess(ok ? 1 : 0);
            if (!ok && ex != null) {
                logEntity.setErrorMsg(truncate(ex.getMessage(), 500));
            } else if (!ok) {
                logEntity.setErrorMsg("HTTP " + response.getStatus());
            }
            operationLogService.record(logEntity);
        } catch (Exception ignored) {
            // 记录日志失败绝不影响主流程
        }
    }

    private boolean isMutating(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private String resolveModule(String uri) {
        if (uri.startsWith("/api/users")) return "用户管理";
        if (uri.startsWith("/api/batches")) return "批次管理";
        if (uri.startsWith("/api/declarations")) return "申报管理";
        if (uri.startsWith("/api/declare")) return "申报管理";
        if (uri.startsWith("/api/audits")) return "审核管理";
        if (uri.startsWith("/api/awards")) return "奖项库";
        if (uri.startsWith("/api/award-categories")) return "奖项分类";
        if (uri.startsWith("/api/categories")) return "奖项分类";
        if (uri.startsWith("/api/basic-awards")) return "基础获奖";
        if (uri.startsWith("/api/notices")) return "公告管理";
        if (uri.startsWith("/api/attachments")) return "附件管理";
        if (uri.startsWith("/api/operation-logs")) return "操作记录";
        if (uri.startsWith("/api/config")) return "系统配置";
        if (uri.startsWith("/api/auth/password")) return "账号安全";
        return "其他";
    }

    private String resolveAction(String method, String uri) {
        if (uri.contains("/status")) return "启用/禁用账号";
        if (uri.contains("/reset-password")) return "重置密码";
        if (uri.contains("/import")) return "导入数据";
        if (uri.contains("/delete")) return "删除";
        switch (method.toUpperCase()) {
            case "POST": return "新增/提交";
            case "PUT": return "修改";
            case "PATCH": return "修改";
            case "DELETE": return "删除";
            default: return method;
        }
    }

    private String resolveIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
