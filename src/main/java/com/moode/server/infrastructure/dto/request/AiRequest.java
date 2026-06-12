package com.moode.server.infrastructure.dto.request;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiRequest {
    private Double ax;
    private Double ay;
    private Double az;
    private Double gx;
    private Double gy;
    private Double gz;
}
