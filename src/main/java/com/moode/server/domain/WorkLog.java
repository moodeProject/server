package com.moode.server.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 출퇴근 기록. ERD 기준(work_log). 하루 1건이 정상 경로이고, 퇴근하지 않은(checkOutAt null)
 * 상태로 체크인만 있는 행이 "현재 근무 중"을 뜻한다.
 *
 * <p>work_log 에는 근무 유형(근무/야근/연차) 컬럼이 없다. 연차는 출퇴근 자체가 없어 이 테이블에
 * 남지 않고, 야근 여부는 조회 시점에 totalWorkMinutes 로 판정한다 ({@link #isOvertime()}).
 */
@Entity
@Table(name = "work_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class WorkLog {

    /** 하루 표준 근무 시간(분). 이를 넘는 완료된 근무를 야근으로 본다. */
    public static final int STANDARD_WORK_MINUTES = 8 * 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "work_log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_work_log_worker"))
    private Worker worker;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "check_in_at", nullable = false)
    private LocalDateTime checkInAt;

    @Column(name = "check_out_at")
    private LocalDateTime checkOutAt;

    @Column(name = "expected_check_out_at")
    private LocalDateTime expectedCheckOutAt;

    @Column(name = "break_start_at")
    private LocalDateTime breakStartAt;

    @Column(name = "break_end_at")
    private LocalDateTime breakEndAt;

    // 체크인 시점의 작업 구역 스냅샷. 이후 작업자가 이동해도 그날 근무 기록의 구역은 유지한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_zone_id",
            foreignKey = @ForeignKey(name = "fk_work_log_zone"))
    private Zone workZone;

    @Column(name = "total_work_minutes")
    private Integer totalWorkMinutes;

    /** 퇴근 처리. 총 근무 분(totalWorkMinutes)을 함께 계산해 둔다. */
    public void checkOut(LocalDateTime checkOutAt) {
        this.checkOutAt = checkOutAt;
        this.totalWorkMinutes = (int) ChronoUnit.MINUTES.between(this.checkInAt, checkOutAt);
    }

    public boolean isOpen() {
        return this.checkOutAt == null;
    }

    /** 완료된 근무 중 표준 시간을 넘긴 경우만 야근으로 본다. 미완료 근무는 false. */
    public boolean isOvertime() {
        return totalWorkMinutes != null && totalWorkMinutes > STANDARD_WORK_MINUTES;
    }

    /** 표준 시간을 넘긴 분량. 야근이 아니면 0. */
    public int overtimeMinutes() {
        return isOvertime() ? totalWorkMinutes - STANDARD_WORK_MINUTES : 0;
    }
}
