package com.moode.server.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 관리자 회원가입. 화면 설계서(docs/ui/admin) 기준 필드 — 연락처/소속은 선택. */
public record SignupRequest(

        @NotBlank(message = "아이디를 입력하세요.")
        @Pattern(regexp = "^[A-Za-z0-9]{4,}$", message = "아이디는 영문·숫자 조합 4자 이상이어야 합니다.")
        String loginId,

        @NotBlank(message = "비밀번호를 입력하세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                message = "비밀번호는 8자 이상, 영문·숫자·특수문자를 포함해야 합니다."
        )
        String password,

        @NotBlank(message = "비밀번호 확인을 입력하세요.")
        String passwordConfirm,

        @NotBlank(message = "이름을 입력하세요.")
        String name,

        @NotBlank(message = "이메일을 입력하세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        String phone,

        String department
) {
}
