package com.moode.server.dto.response;

import com.moode.server.domain.UserType;
import lombok.Builder;

@Builder
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserType userType,
        Long userId,
        String loginId,
        String name
) {
}
