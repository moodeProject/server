package com.moode.server.infrastructure.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AiResponse {
    private boolean fallDetected;
    private Double confidence;
}
