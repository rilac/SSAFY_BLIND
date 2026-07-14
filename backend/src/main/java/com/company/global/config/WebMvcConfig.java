package com.company.global.config;

import com.company.global.security.AdminStepUpInterceptor;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// R8: 관리자 step-up 인터셉터를 /api/admin/** 에 등록.
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminStepUpInterceptor adminStepUpInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminStepUpInterceptor)
                .addPathPatterns("/api/admin/**");
    }
}
