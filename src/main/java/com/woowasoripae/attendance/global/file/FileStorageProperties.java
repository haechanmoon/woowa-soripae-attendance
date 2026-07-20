package com.woowasoripae.attendance.global.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.file")
public record FileStorageProperties(String uploadDir, String baseUrl) {
}
