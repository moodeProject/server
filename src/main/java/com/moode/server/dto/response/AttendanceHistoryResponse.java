package com.moode.server.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AttendanceHistoryResponse {
    private AttendanceSummaryResponse summary;
    private List<WorkLogResponse> items;
}
