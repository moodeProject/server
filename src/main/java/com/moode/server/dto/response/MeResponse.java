package com.moode.server.dto.response;

import com.moode.server.domain.UserType;
import lombok.Builder;

@Builder
public record MeResponse(
        Long userId,
        UserType userType,
        String loginId,
        String name
) {
}
