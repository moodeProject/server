package com.moode.server.dto.response;

import com.moode.server.common.Times;
import com.moode.server.domain.AttendanceRequest;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Getter
@Builder
public class AttendanceRequestResponse {

    private Long id;
    private String type;
    private LocalDate targetDate;

    private LocalTime startTime;
    private LocalTime endTime;

    private String reason;
    private String status;

    private Long reviewedBy;
    private OffsetDateTime reviewedAt;
    private OffsetDateTime createdAt;

    public static AttendanceRequestResponse from(AttendanceRequest request) {
        return AttendanceRequestResponse.builder()
                .id(request.getId())
                .type(request.getType().name())
                .targetDate(request.getTargetDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .reason(request.getReason())
                .status(request.getStatus().name())
                .reviewedBy(request.getReviewedBy())
                .reviewedAt(Times.toOffset(request.getReviewedAt()))
                .createdAt(Times.toOffset(request.getCreatedAt()))
                .build();
    }
}
