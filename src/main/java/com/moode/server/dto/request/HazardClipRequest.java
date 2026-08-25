package com.moode.server.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * CLIP_READY 메시지의 clip 블록.
 * 공개 URL 이 아니라 bucket + key 만 받는다. 조회 시점에 pre-signed URL 을 발급하기 때문.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@NoArgsConstructor
public class HazardClipRequest {

    private String storage;   // S3 / LOCAL
    private String bucket;
    private String key;
    private BigDecimal durationSeconds;
    private Integer frameCount;
}
