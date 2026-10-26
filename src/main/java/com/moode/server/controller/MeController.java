package com.moode.server.controller;

import com.moode.server.common.ApiResponse;
import com.moode.server.dto.response.MyHomeResponse;
import com.moode.server.security.AuthenticatedUser;
import com.moode.server.service.MyHomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 작업자 앱 전용(ROLE_WORKER). 경로에 작업자/헬멧 식별자를 받지 않고 토큰의 본인만 조회하므로
 * 다른 작업자 데이터는 구조적으로 볼 수 없다.
 *
 * <p>/api/workers/** 는 관리자 전용 게이트가 걸려 있어서 /api/workers/me 가 아니라 /api/me 아래에 둔다.
 */
@Tag(name = "작업자 앱", description = "로그인한 작업자 본인 데이터 조회")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final MyHomeService myHomeService;

    @Operation(summary = "홈 화면 조회",
            description = "내 정보·현재 구역, 배정된 헬멧과 연결 상태, 최신 센서 측정값(추락/건강/온열). "
                    + "헬멧 미배정이면 helmet·sensor 가 null, 센서 데이터가 아직 없으면 sensor 가 null.")
    @GetMapping("/home")
    public ApiResponse<MyHomeResponse> home(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(myHomeService.getHome(user.userId()));
    }
}
