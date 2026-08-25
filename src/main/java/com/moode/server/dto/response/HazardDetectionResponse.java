package com.moode.server.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 상세의 detection 블록. 클립보다 먼저 도착하므로 영상이 없어도 항상 채워진다.
 */
@Getter
@Builder
public class HazardDetectionResponse {

    // bbox 좌표의 기준 해상도. 이게 없으면 프론트가 오버레이 스케일을 못 맞춘다.
    private Integer frameWidth;
    private Integer frameHeight;

    private List<HazardDetectionBoxResponse> boxes;
}
