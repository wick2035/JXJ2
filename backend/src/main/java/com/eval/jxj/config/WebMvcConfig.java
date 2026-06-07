package com.eval.jxj.config;

import com.eval.jxj.interceptor.OperationLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注意：上传文件不再通过 /uploads/** 静态资源匿名暴露。
 * 文件只能经鉴权接口 GET /api/attachments/{id}/file 访问（见 AttachmentController）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final OperationLogInterceptor operationLogInterceptor;

    public WebMvcConfig(OperationLogInterceptor operationLogInterceptor) {
        this.operationLogInterceptor = operationLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(operationLogInterceptor).addPathPatterns("/api/**");
    }
}
