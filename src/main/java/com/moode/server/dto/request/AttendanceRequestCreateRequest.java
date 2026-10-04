package com.moode.server.dto.request;

import com.moode.server.domain.AttendanceRequestType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/** 근태 신청(연차/야근/근무수정) 생성 요청. */
@Getter
@NoArgsConstructor
public class AttendanceRequestCreateRequest {

    @NotNull(message = "workerId 는 필수입니다.")
    private Long workerId;

    @NotNull(message = "type 은 필수입니다.")
    private AttendanceRequestType type;

    @NotNull(message = "targetDate 는 필수입니다.")
    private LocalDate targetDate;

    // 연차는 null. 야근/근무수정만 사용.
    private LocalTime startTime;
    private LocalTime endTime;

    private String reason;
}
