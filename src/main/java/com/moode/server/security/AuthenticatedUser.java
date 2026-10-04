package com.moode.server.security;

import com.moode.server.domain.UserType;

/** JWT 필터가 SecurityContext 에 심는 principal. @AuthenticationPrincipal 로 컨트롤러에서 바로 받는다. */
public record AuthenticatedUser(Long userId, UserType userType, String loginId, String name) {
}
