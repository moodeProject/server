package com.moode.server.dto.response;

import com.moode.server.common.Times;
import com.moode.server.domain.HazardActionType;
import com.moode.server.domain.HazardEventAction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 사고에 대해 관리자가 실행한 조치 1건. 상세 화면의 "조치 이력"에 시간순으로 보여준다.
 */
@Getter
@Builder
public class HazardActionHistoryResponse {

    @Schema(description = "조치 종류", example = "RESOLVE")
    private HazardActionType actionType;

    @Schema(description = "조치한 관리자 id. 요청에 없었으면 null", example = "1", nullable = true)
    private Long performedBy;

    @Schema(description = "조치 시각")
    private OffsetDateTime performedAt;

    @Schema(description = "조치 메모", example = "작업자 휴식 조치 후 상태 확인 완료", nullable = true)
    private String memo;

    @Schema(description = "이 조치로 알림을 받은 인원 수", example = "1", nullable = true)
    private Integer notifiedCount;

    public static HazardActionHistoryResponse from(HazardEventAction action) {
        return HazardActionHistoryResponse.builder()
                .actionType(action.getActionType())
                .performedBy(action.getPerformedBy())
                .performedAt(Times.toOffset(action.getPerformedAt()))
                .memo(action.getMemo())
                .notifiedCount(action.getNotifiedCount())
                .build();
    }
}
