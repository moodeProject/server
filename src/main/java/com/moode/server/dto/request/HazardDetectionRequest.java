package com.moode.server.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 서버가 보낸 개별 감지 박스. 서버는 내용을 해석하지 않고 프론트 오버레이용으로 그대로 보존한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@NoArgsConstructor
public class HazardDetectionRequest {

    private String type;          // puddle / pothole / forklift
    private BigDecimal confidence;
    private List<BigDecimal> bbox; // [x1, y1, x2, y2]
}
