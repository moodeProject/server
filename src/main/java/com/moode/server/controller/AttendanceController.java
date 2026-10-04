package com.moode.server.controller;

import com.moode.server.common.ApiResponse;
import com.moode.server.dto.request.AttendanceRequestCreateRequest;
import com.moode.server.dto.request.AttendanceRequestReviewRequest;
import com.moode.server.dto.request.CheckInRequest;
import com.moode.server.dto.request.CheckOutRequest;
import com.moode.server.dto.response.AttendanceHistoryResponse;
import com.moode.server.dto.response.AttendanceRequestResponse;
import com.moode.server.dto.response.WorkLogResponse;
import com.moode.server.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

/**
 * 근태(출퇴근/신청) API.
 *
 * <p>인증이 아직 없어 작업자 식별이 필요한 출퇴근 요청은 본문의 workerId 로 받는다.
 * 인증 도입 후에는 principal 에서 가져오도록 교체한다 (HazardActionRequest.adminId 와 동일한 임시 패턴).
 */
@Tag(name = "근태", description = "Frontend: 출퇴근 및 근태 신청 API")
@RestController
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Operation(summary = "월별 근태 이력 조회",
            description = "work_log(근무/야근) 와 승인된 연차 신청을 합쳐 날짜 내림차순으로 반환한다.")
    @GetMapping("/api/workers/{workerId}/attendance")
    public ApiResponse<AttendanceHistoryResponse> getAttendance(
            @PathVariable Long workerId,
            @Parameter(description = "조회할 월. 생략하면 이번 달", example = "2026-09")
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ApiResponse.success(
                attendanceService.getAttendance(workerId, month == null ? YearMonth.now() : month));
    }

    @Operation(summary = "출근")
    @PostMapping("/api/attendance/check-in")
    public ApiResponse<WorkLogResponse> checkIn(@Valid @RequestBody CheckInRequest request) {
        return ApiResponse.success(attendanceService.checkIn(request.getWorkerId()));
    }

    @Operation(summary = "퇴근")
    @PostMapping("/api/attendance/check-out")
    public ApiResponse<WorkLogResponse> checkOut(@Valid @RequestBody CheckOutRequest request) {
        return ApiResponse.success(attendanceService.checkOut(request.getWorkerId()));
    }

    @Operation(summary = "근태 신청 이력 조회")
    @GetMapping("/api/workers/{workerId}/attendance-requests")
    public ApiResponse<java.util.List<AttendanceRequestResponse>> getAttendanceRequests(
            @PathVariable Long workerId) {
        return ApiResponse.success(attendanceService.getAttendanceRequests(workerId));
    }

    @Operation(summary = "근태 신청 등록", description = "type: LEAVE(연차) / OVERTIME(야근) / CORRECTION(근무수정)")
    @PostMapping("/api/attendance-requests")
    public ApiResponse<AttendanceRequestResponse> createAttendanceRequest(
            @Valid @RequestBody AttendanceRequestCreateRequest request) {
        return ApiResponse.success(attendanceService.createAttendanceRequest(request));
    }

    @Operation(summary = "근태 신청 승인/반려", description = "status: APPROVED / REJECTED. 이미 처리된 신청은 409.")
    @PatchMapping("/api/attendance-requests/{requestId}")
    public ApiResponse<AttendanceRequestResponse> reviewAttendanceRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody AttendanceRequestReviewRequest request) {
        return ApiResponse.success(attendanceService.reviewAttendanceRequest(requestId, request));
    }
}
