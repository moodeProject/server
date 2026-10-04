package com.moode.server.domain;

/**
 * 근태 신청 종류. ERD의 work_log 에는 이 구분이 없어(출퇴근 시각만 기록) 신청 단계에서만 쓴다.
 * LEAVE(연차) 는 work_log 에 출퇴근 기록이 생기지 않으므로 월별 이력 조회 시 승인된 신청을
 * work_log 와 합쳐서 보여준다.
 */
public enum AttendanceRequestType {
    LEAVE,
    OVERTIME,
    CORRECTION
}
