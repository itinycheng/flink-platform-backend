package com.itiger.persona;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author Shik
 * @Title: PersonaPlatformWebApplication
 * @ProjectName: persona-platform-backend
 * @Description: TODO
 * @Date: 2021/4/9 下午2:31
 */
@SpringBootApplication(exclude = DruidDataSourceAutoConfigure.class)
public class PersonaPlatformWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonaPlatformWebApplication.class, args);
    }

}
