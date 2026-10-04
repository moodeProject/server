package com.moode.server.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 근태 신청(연차/야근/근무수정). ERD에는 없는 테이블이다 — ERD의 work_log 는 신청이 아니라
 * 확정된 출퇴근 결과만 담고 있어, 신청/승인 흐름을 담을 곳이 없었다. 팀 확인 후 ERD에 반영 필요.
 *
 * <p>승인(APPROVED) 시 work_log 에 자동으로 반영하지는 않는다 — 연차는 원래 출퇴근 기록이
 * 없고, 야근/근무수정은 당일 work_log 가 이미 있다는 보장이 없어 1차 범위에서는 신청 상태
 * 갱신까지만 처리한다.
 */
@Entity
@Table(name = "attendance_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AttendanceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendance_request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attendance_request_worker"))
    private Worker worker;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 12)
    private AttendanceRequestType type;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    // 연차는 시간 없음(null). 야근/근무수정만 사용.
    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private AttendanceRequestStatus status = AttendanceRequestStatus.PENDING;

    // manager_account.admin_id. 해당 엔티티가 아직 없어 hazard_event.handledBy 와 같은 이유로
    // 스칼라로 둔다.
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = AttendanceRequestStatus.PENDING;
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    /** 승인/반려 처리. 이미 처리된 신청이면 호출하지 않는 것이 호출자의 책임이다. */
    public void review(AttendanceRequestStatus status, Long adminId) {
        if (status == AttendanceRequestStatus.PENDING) {
            throw new IllegalArgumentException("승인 또는 반려 상태만 지정할 수 있습니다.");
        }
        this.status = status;
        this.reviewedBy = adminId;
        this.reviewedAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return this.status == AttendanceRequestStatus.PENDING;
    }
}
