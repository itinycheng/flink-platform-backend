package com.flink.platform.web.config;

import com.flink.platform.web.config.interceptor.LoginInterceptor;
import com.flink.platform.web.config.interceptor.PermissionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web mvc config. */
@Configuration
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AppConfiguration implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    private final PermissionInterceptor permissionInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] protectedPaths = {
            "/jobInfo/**", "/jobRun/**", "/jobParam/**",
            "/jobFlow/**", "/jobFlowRun/**", "/tag/**",
            "/alert/**", "/resource/**", "/user/**",
            "/worker/**", "/datasource/**", "/catalog/**",
            "/dashboard/**", "/config/**", "/workspace/**"
        };

        // must add loginInterceptor before permissionInterceptor.
        registry.addInterceptor(loginInterceptor).addPathPatterns(protectedPaths);
        registry.addInterceptor(permissionInterceptor).addPathPatterns(protectedPaths);
    }
}
