package com.moode.server.controller;

import com.moode.server.common.ApiResponse;
import com.moode.server.domain.SensorAlertType;
import com.moode.server.dto.response.HeatRiskResponse;
import com.moode.server.dto.response.LatestVitalsResponse;
import com.moode.server.dto.response.WorkerStatusResponse;
import com.moode.server.service.WorkerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @Operation(summary = "추락·건강 이상 센서 이력 조회",
            description = "type=FALL 이면 추락 단계(휘청거림 이상)인 기록만, type=HEALTH 면 건강 조치 필요 기록만 준다. "
                    + "비우면 둘 중 하나라도 해당하는 기록 전체(예전 동작). 추락 이력 화면은 type=FALL 을 쓴다.")
    @GetMapping("/alerts")
    public ApiResponse<List<WorkerStatusResponse>> getAlerts(
            @Parameter(description = "FALL 또는 HEALTH. 생략하면 전체")
            @RequestParam(required = false) SensorAlertType type) {
        return ApiResponse.success(workerService.getAlerts(type));
    }

    @Operation(summary = "헬멧별 최근 유효 생체 측정값",
            description = "센서 오류로 0 이 들어온 수신은 건너뛰고, 심박·SpO₂·HRV 각각 0 보다 큰 마지막 값을 준다.")
    @GetMapping("/{deviceId}/vitals/latest")
    public ApiResponse<LatestVitalsResponse> getLatestVitals(@PathVariable String deviceId) {
        return ApiResponse.success(workerService.getLatestVitals(deviceId));
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
