package com.moode.server.dto.response;

import com.moode.server.domain.WorkerState;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SensorDataResponse {
    private Long id;
    private String deviceId;
    private WorkerState state;
    private boolean alertTriggered;
    private LocalDateTime recordedAt;
}
