package com.moode.server.dto.response;

import com.moode.server.common.Times;
import com.moode.server.domain.AttendanceRequest;
import com.moode.server.domain.WorkLog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 근태 이력 한 줄. work_log(근무/야근)와 승인된 연차 신청을 같은 모양으로 합쳐서 보여준다.
 * work_log 에는 근무 유형 컬럼이 없어 totalWorkMinutes 로 야근 여부를 조회 시점에 판정한다.
 */
@Getter
@Builder
public class WorkLogResponse {

    @Schema(allowableValues = {"WORK", "OVERTIME", "LEAVE"})
    private String type;

    private LocalDate date;

    @Schema(nullable = true)
    private OffsetDateTime checkInAt;

    @Schema(nullable = true)
    private OffsetDateTime checkOutAt;

    @Schema(description = "총 근무 분. 퇴근 전이면 null", nullable = true)
    private Integer totalWorkMinutes;

    @Schema(description = "표준 근무시간(8시간)을 넘긴 분량. 야근이 아니면 0")
    private int overtimeMinutes;

    public static WorkLogResponse from(WorkLog log) {
        return WorkLogResponse.builder()
                .type(log.isOvertime() ? "OVERTIME" : "WORK")
                .date(log.getWorkDate())
                .checkInAt(Times.toOffset(log.getCheckInAt()))
                .checkOutAt(Times.toOffset(log.getCheckOutAt()))
                .totalWorkMinutes(log.getTotalWorkMinutes())
                .overtimeMinutes(log.overtimeMinutes())
                .build();
    }

    /** 승인된 연차 신청 1건을 이력 한 줄로 변환. */
    public static WorkLogResponse fromApprovedLeave(AttendanceRequest request) {
        return WorkLogResponse.builder()
                .type("LEAVE")
                .date(request.getTargetDate())
                .checkInAt(null)
                .checkOutAt(null)
                .totalWorkMinutes(null)
                .overtimeMinutes(0)
                .build();
    }
}
