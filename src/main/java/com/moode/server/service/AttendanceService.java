package com.moode.server.service;

import com.moode.server.dto.request.AttendanceRequestCreateRequest;
import com.moode.server.dto.request.AttendanceRequestReviewRequest;
import com.moode.server.dto.response.AttendanceHistoryResponse;
import com.moode.server.dto.response.AttendanceRequestResponse;
import com.moode.server.dto.response.WorkLogResponse;

import java.time.YearMonth;
import java.util.List;

public interface AttendanceService {

    AttendanceHistoryResponse getAttendance(Long workerId, YearMonth month);

    WorkLogResponse checkIn(Long workerId);

    WorkLogResponse checkOut(Long workerId);

    List<AttendanceRequestResponse> getAttendanceRequests(Long workerId);

    AttendanceRequestResponse createAttendanceRequest(AttendanceRequestCreateRequest request);

    AttendanceRequestResponse reviewAttendanceRequest(Long requestId, AttendanceRequestReviewRequest request);
}
