package com.moode.server.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moode.server.domain.HazardType;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 영상 위에 그릴 감지 박스 하나.
 * 저장해둔 detections 원본 JSON 을 그대로 역직렬화하는 대상이자 응답 타입이다.
 *
 * <p>bbox 는 [x1, y1, x2, y2] 픽셀 좌표이고 기준 해상도는 상위의 frameWidth/frameHeight 다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@NoArgsConstructor
public class HazardDetectionBoxResponse {

    private String type;
    private BigDecimal confidence;
    private List<BigDecimal> bbox;

    /**
     * 화면에 그대로 쓸 한글 라벨. 알 수 없는 type 이면 null 로 두고 박스는 그대로 그린다.
     * (AI 서버가 새 종류를 먼저 배포해도 상세 화면이 깨지지 않도록)
     */
    public String getLabel() {
        if (type == null) {
            return null;
        }
        try {
            return HazardType.from(type).getLabel();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
