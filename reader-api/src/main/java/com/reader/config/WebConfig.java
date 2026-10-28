package com.reader.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA 路由回退：前端 History 路由（/、/login、/register、/settings、/reader/*）
 * 直接访问或刷新时，回退到 index.html，由前端 Vue Router 接管。
 * 静态资源（/assets/** 等）仍由 Spring Boot 默认静态资源处理器提供。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/login").setViewName("forward:/index.html");
        registry.addViewController("/register").setViewName("forward:/index.html");
        registry.addViewController("/settings").setViewName("forward:/index.html");
        registry.addViewController("/reader/**").setViewName("forward:/index.html");
    }
}
