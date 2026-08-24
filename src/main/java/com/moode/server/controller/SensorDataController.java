package com.moode.server.controller;

import com.moode.server.common.ApiResponse;
import com.moode.server.dto.request.SensorDataRequest;
import com.moode.server.dto.response.SensorDataResponse;
import com.moode.server.service.SensorDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "센서 데이터", description = "ESP 32 센서 데이터 수신 API")
@RestController
@RequestMapping("/api/sensor-data")
@RequiredArgsConstructor
public class SensorDataController {
    private final SensorDataService sensorDataService;

    @Operation(summary = "센서 데이터 수신 및 상태 판단")
    @PostMapping
    public ApiResponse<SensorDataResponse> receiveSensorData(
            @Valid @RequestBody SensorDataRequest request) {
        return ApiResponse.success(sensorDataService.processSensorData(request));
    }
}
