package com.moode.server.controller;

import com.moode.server.common.ApiResponse;
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

    @Operation(summary = "특정 헬멧 상태 조회")
    @GetMapping("/{deviceId}")
    public ApiResponse<WorkerStatusResponse> getWorkerStatus(@PathVariable String deviceId) {
        return ApiResponse.success(workerService.getWorkerStatus(deviceId));
    }

    @Operation(summary = "추락 감지 이력 조회")
    @GetMapping("/alerts")
    public ApiResponse<List<WorkerStatusResponse>> getAlerts() {
        return ApiResponse.success(workerService.getAlerts());
    }
}
