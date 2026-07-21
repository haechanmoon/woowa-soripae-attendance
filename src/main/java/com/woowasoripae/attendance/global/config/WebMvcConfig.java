package com.woowasoripae.attendance.global.config;

import com.woowasoripae.attendance.global.file.FileStorageProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final FileStorageProperties properties;

    public WebMvcConfig(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(properties.baseUrl() + "/**")
                .addResourceLocations("file:" + properties.uploadDir() + "/");
    }

    /** MVP shortcut: 프론트가 별도 오리진(로컬 파일, 다른 서버 등)에서 API를 호출할 수 있도록 전체 허용. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
