package com.moode.server.controller;

import com.moode.server.common.ApiResponse;
import com.moode.server.dto.request.LoginRequest;
import com.moode.server.dto.request.SignupRequest;
import com.moode.server.dto.response.LoginResponse;
import com.moode.server.dto.response.MeResponse;
import com.moode.server.dto.response.SignupResponse;
import com.moode.server.security.AuthenticatedUser;
import com.moode.server.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증", description = "작업자/관리자 로그인, 관리자 회원가입")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "로그인", description = "userType=WORKER 는 사번, ADMIN 은 아이디로 로그인한다.")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(summary = "로그아웃", description = "Access Token만 쓰는 데모 구성이라 서버 상태는 없다. 클라이언트가 토큰을 폐기한다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success(null);
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(authService.me(user));
    }

    @Operation(summary = "관리자 회원가입", description = "auth.admin.auto-approve-signup 이 꺼져 있으면 승인 전까지 로그인할 수 없다.")
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success("회원가입 신청이 접수되었습니다.", authService.signup(request));
    }
}
