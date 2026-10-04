package com.moode.server.service.impl;

import com.moode.server.domain.AttendanceRequest;
import com.moode.server.domain.AttendanceRequestStatus;
import com.moode.server.domain.AttendanceRequestType;
import com.moode.server.domain.WorkLog;
import com.moode.server.domain.Worker;
import com.moode.server.dto.request.AttendanceRequestCreateRequest;
import com.moode.server.dto.request.AttendanceRequestReviewRequest;
import com.moode.server.dto.response.AttendanceHistoryResponse;
import com.moode.server.dto.response.AttendanceRequestResponse;
import com.moode.server.dto.response.AttendanceSummaryResponse;
import com.moode.server.dto.response.WorkLogResponse;
import com.moode.server.exception.ConflictException;
import com.moode.server.exception.NotFoundException;
import com.moode.server.exception.UnprocessableEntityException;
import com.moode.server.repository.AttendanceRequestRepository;
import com.moode.server.repository.WorkLogRepository;
import com.moode.server.repository.WorkerRepository;
import com.moode.server.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final WorkerRepository workerRepository;
    private final WorkLogRepository workLogRepository;
    private final AttendanceRequestRepository attendanceRequestRepository;

    @Override
    @Transactional(readOnly = true)
    public AttendanceHistoryResponse getAttendance(Long workerId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        List<WorkLog> logs = workLogRepository
                .findByWorkerIdAndWorkDateBetweenOrderByWorkDateDesc(workerId, from, to);

        List<AttendanceRequest> approvedLeaves = attendanceRequestRepository
                .findByWorkerIdAndTypeAndStatusAndTargetDateBetween(
                        workerId, AttendanceRequestType.LEAVE, AttendanceRequestStatus.APPROVED, from, to);

        List<WorkLogResponse> items = new java.util.ArrayList<>();
        logs.forEach(log -> items.add(WorkLogResponse.from(log)));
        approvedLeaves.forEach(leave -> items.add(WorkLogResponse.fromApprovedLeave(leave)));
        items.sort(Comparator.comparing(WorkLogResponse::getDate).reversed());

        int totalWorkMinutes = logs.stream()
                .map(WorkLog::getTotalWorkMinutes)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        long overtimeDays = logs.stream().filter(WorkLog::isOvertime).count();

        AttendanceSummaryResponse summary = AttendanceSummaryResponse.builder()
                .workDays(logs.size())
                .overtimeDays((int) overtimeDays)
                .leaveDays(approvedLeaves.size())
                .totalWorkMinutes(totalWorkMinutes)
                .build();

        return AttendanceHistoryResponse.builder()
                .summary(summary)
                .items(items)
                .build();
    }

    @Override
    @Transactional
    public WorkLogResponse checkIn(Long workerId) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 작업자입니다. " + workerId));

        LocalDate today = LocalDate.now();
        if (workLogRepository.findByWorkerIdAndWorkDate(workerId, today).isPresent()) {
            throw new ConflictException("오늘 이미 출근 처리된 근무가 있습니다.");
        }

        WorkLog saved = workLogRepository.save(WorkLog.builder()
                .worker(worker)
                .workDate(today)
                .checkInAt(LocalDateTime.now())
                .workZone(worker.getCurrentZone())
                .build());

        worker.startWork();

        log.info("출근 처리: workerId={}, workLogId={}", workerId, saved.getId());
        return WorkLogResponse.from(saved);
    }

    @Override
    @Transactional
    public WorkLogResponse checkOut(Long workerId) {
        WorkLog latest = workLogRepository.findTop1ByWorkerIdOrderByCheckInAtDesc(workerId)
                .orElseThrow(() -> new UnprocessableEntityException("출근 기록이 없어 퇴근 처리할 수 없습니다."));

        if (!latest.isOpen()) {
            throw new UnprocessableEntityException("이미 퇴근 처리된 근무입니다.");
        }

        latest.checkOut(LocalDateTime.now());
        latest.getWorker().endWork();

        log.info("퇴근 처리: workerId={}, workLogId={}, totalWorkMinutes={}",
                workerId, latest.getId(), latest.getTotalWorkMinutes());
        return WorkLogResponse.from(latest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceRequestResponse> getAttendanceRequests(Long workerId) {
        return attendanceRequestRepository.findByWorkerIdOrderByCreatedAtDesc(workerId)
                .stream()
                .map(AttendanceRequestResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public AttendanceRequestResponse createAttendanceRequest(AttendanceRequestCreateRequest request) {
        Worker worker = workerRepository.findById(request.getWorkerId())
                .orElseThrow(() -> new NotFoundException("존재하지 않는 작업자입니다. " + request.getWorkerId()));

        if (request.getType() != AttendanceRequestType.LEAVE
                && (request.getStartTime() == null || request.getEndTime() == null)) {
            throw new IllegalArgumentException("야근/근무수정 신청은 시작·종료 시각이 필요합니다.");
        }

        AttendanceRequest saved = attendanceRequestRepository.save(AttendanceRequest.builder()
                .worker(worker)
                .type(request.getType())
                .targetDate(request.getTargetDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .reason(request.getReason())
                .build());

        log.info("근태 신청 접수: workerId={}, type={}, targetDate={}",
                request.getWorkerId(), request.getType(), request.getTargetDate());
        return AttendanceRequestResponse.from(saved);
    }

    @Override
    @Transactional
    public AttendanceRequestResponse reviewAttendanceRequest(
            Long requestId, AttendanceRequestReviewRequest request) {
        AttendanceRequest entity = attendanceRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 근태 신청입니다. " + requestId));

        if (!entity.isPending()) {
            throw new ConflictException("이미 처리된 신청입니다. " + entity.getStatus());
        }

        entity.review(request.getStatus(), request.getAdminId());

        log.info("근태 신청 처리: requestId={}, status={}, adminId={}",
                requestId, request.getStatus(), request.getAdminId());
        return AttendanceRequestResponse.from(entity);
    }
}
