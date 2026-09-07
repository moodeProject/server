package com.moode.server.dto.response;

import com.moode.server.domain.WorkerState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WeatherStatusResponse {

    @Schema(description = "현재 캐싱된 체감온도(섭씨). 첫 갱신 전이면 null", nullable = true)
    private Double apparentTemperature;

    @Schema(description = "현재 현장 온열질환 위험도")
    private WorkerState heatRiskLevel;
}
