package com.woowasoripae.attendance.web.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** MVP shortcut: one shared password for all officers, no accounts/sessions. See AdminAuthController javadoc for follow-up. */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String password) {
}
