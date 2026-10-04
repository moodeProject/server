package com.moode.server.repository;

import com.moode.server.domain.AttendanceRequest;
import com.moode.server.domain.AttendanceRequestStatus;
import com.moode.server.domain.AttendanceRequestType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceRequestRepository extends JpaRepository<AttendanceRequest, Long> {

    List<AttendanceRequest> findByWorkerIdOrderByCreatedAtDesc(Long workerId);

    /** 월별 이력에 합쳐 보여줄 승인된 연차 조회용. */
    List<AttendanceRequest> findByWorkerIdAndTypeAndStatusAndTargetDateBetween(
            Long workerId, AttendanceRequestType type, AttendanceRequestStatus status,
            LocalDate from, LocalDate to);
}
