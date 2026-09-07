package com.moode.server.controller;

import com.moode.server.common.ApiResponse;
import com.moode.server.dto.response.WeatherStatusResponse;
import com.moode.server.service.impl.WeatherHeatRiskCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "기상 상태", description = "Frontend: 현장 외부 기상/온열질환 위험도 조회 API")
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherHeatRiskCache weatherHeatRiskCache;

    @Operation(summary = "현재 현장 체감온도 및 온열질환 위험도 조회")
    @GetMapping("/heat-risk")
    public ApiResponse<WeatherStatusResponse> getHeatRiskStatus() {
        return ApiResponse.success(WeatherStatusResponse.builder()
                .apparentTemperature(weatherHeatRiskCache.currentApparentTemperature())
                .heatRiskLevel(weatherHeatRiskCache.currentLevel())
                .build());
    }
}
