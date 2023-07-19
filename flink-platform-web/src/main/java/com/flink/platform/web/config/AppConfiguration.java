package com.flink.platform.web.config;

import com.flink.platform.web.config.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web mvc config. */
@Configuration
public class AppConfiguration implements WebMvcConfigurer {

    @Bean
    public LoginInterceptor loginInterceptor() {
        return new LoginInterceptor();
    }

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
        registry.addInterceptor(loginInterceptor())
                .addPathPatterns(
                        "/jobInfo/**",
                        "/jobRun/**",
                        "/jobParam/**",
                        "/jobFlow/**",
                        "/jobFlowRun/**",
                        "/tag/**",
                        "/alert/**",
                        "/resource/**",
                        "/user/**",
                        "/worker/**",
                        "/datasource/**",
                        "/catalog/**",
                        "/dashboard/**");
    }
}
