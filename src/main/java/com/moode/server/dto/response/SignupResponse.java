package com.moode.server.dto.response;

import com.moode.server.domain.AdminStatus;
import lombok.Builder;

@Builder
public record SignupResponse(
        Long adminId,
        String loginId,
        AdminStatus status
) {
}
