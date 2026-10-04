package com.moode.server.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AttendanceSummaryResponse {
    private int workDays;
    private int overtimeDays;
    private int leaveDays;
    private int totalWorkMinutes;
}
