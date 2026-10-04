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

    @Operation(summary = "전체 작업자 현황 조회")
    @GetMapping("/status")
    public ApiResponse<List<WorkerStatusResponse>> getAllWorkersStatus() {
        return ApiResponse.success(workerService.getAllWorkersStatus());
    }

    // 작업자 관리(CRUD) API(WorkerManagementController)의 GET /api/workers/{workerId} 와
    // 경로 패턴이 완전히 같아 두 핸들러가 동시에 매칭되는 순간(기동 시가 아니라 요청마다) 스프링이
    // "Ambiguous handler methods" 로 500을 던진다. 그래서 /status 하위로 옮겼다 — 프론트에서
    // 이 엔드포인트를 쓰고 있다면 호출 경로를 같이 바꿔줘야 한다.
    @Operation(summary = "특정 헬멧 상태 조회")
    @GetMapping("/status/{deviceId}")
    public ApiResponse<WorkerStatusResponse> getWorkerStatus(@PathVariable String deviceId) {
        return ApiResponse.success(workerService.getWorkerStatus(deviceId));
    }

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
