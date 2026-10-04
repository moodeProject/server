package com.moode.server.dto.request;

import com.moode.server.domain.UserType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(

        @NotNull(message = "userType은 필수입니다.")
        UserType userType,

        @NotBlank(message = "아이디(또는 사번)를 입력하세요.")
        String loginId,

        @NotBlank(message = "비밀번호를 입력하세요.")
        String password
) {
}
