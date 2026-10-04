package com.moode.server.controller;

import com.moode.server.common.ApiResponse;
import com.moode.server.dto.response.HeatRiskResponse;
import com.moode.server.dto.response.WorkerStatusResponse;
import com.moode.server.service.WorkerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "작업자 상태", description = "Frontend: 작업자 상태 조회 API")
@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;

    // 전체/특정 헬멧 상태 조회(/status, /{deviceId})는 HelmetController 의
    // GET /api/helmets/status, GET /api/helmets/{helmetId}/status 로 이전했다.
    // (/{deviceId} 가 작업자 관리의 /api/workers/{workerId} 와 경로가 겹쳐서)

    @Operation(summary = "추락 감지 이력 조회")
    @GetMapping("/alerts")
    public ApiResponse<List<WorkerStatusResponse>> getAlerts() {
        return ApiResponse.success(workerService.getAlerts());
    }

    @Operation(summary = "전체 작업자 온열질환/피로도 현황 조회")
    @GetMapping("/heat-risk")
    public ApiResponse<List<HeatRiskResponse>> getAllHeatRiskStatus() {
        return ApiResponse.success(workerService.getAllHeatRiskStatus());
    }

    @Operation(summary = "특정 작업자 온열질환/피로도 상세 조회")
    @GetMapping("/{deviceId}/heat-risk")
    public ApiResponse<HeatRiskResponse> getHeatRiskStatus(@PathVariable String deviceId) {
        return ApiResponse.success(workerService.getHeatRiskStatus(deviceId));
    }
}
