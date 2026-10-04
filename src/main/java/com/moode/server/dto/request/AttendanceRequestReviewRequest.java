package com.moode.server.dto.request;

import com.moode.server.domain.AttendanceRequestStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리자의 근태 신청 승인/반려. */
@Getter
@NoArgsConstructor
public class AttendanceRequestReviewRequest {

    @NotNull(message = "status 는 필수입니다.")
    private AttendanceRequestStatus status;

    // 인증 도입 전까지 본문으로 받는다.
    private Long adminId;

    private String comment;
}
