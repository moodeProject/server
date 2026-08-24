package com.moode.server.infrastructure.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@NoArgsConstructor
public class AiResponse {
    private boolean fallDetected;
    private Double confidence;
    private boolean buffered;   // true면 AI가 아직 50개 샘플을 못 채워서 판단을 안 한 상태
    private Integer bufferSize; // 지금까지 AI 쪽에 쌓인 샘플 개수
}
