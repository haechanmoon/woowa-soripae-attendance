package com.woowasoripae.attendance.web.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminPasswordRequest(@NotBlank String password) {
}
