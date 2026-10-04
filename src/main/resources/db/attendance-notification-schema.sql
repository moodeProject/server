-- 근태(work_log, attendance_request) + 알림(notification) 테이블 DDL
--
-- local  : ddl-auto=update -> 앱 기동 시 자동 생성되므로 이 스크립트를 실행할 필요 없음
-- prod   : ddl-auto=validate -> 테이블이 없으면 기동에 실패하므로 배포 전 이 스크립트를 수동 실행해야 함
--
-- attendance_request 는 ERD(moode_erd.sql, 2026-09-07)에 없는 테이블이다. ERD의 work_log 는
-- 확정된 출퇴근 결과만 담고 신청/승인 흐름이 없어 새로 추가했다 — 팀 확인 후 ERD에 반영 필요.

CREATE TABLE IF NOT EXISTS work_log (
    work_log_id            BIGINT   NOT NULL AUTO_INCREMENT,
    worker_id              BIGINT   NOT NULL,
    work_date              DATE     NOT NULL,
    check_in_at            DATETIME NOT NULL,
    check_out_at           DATETIME NULL,
    expected_check_out_at  DATETIME NULL,
    break_start_at         DATETIME NULL,
    break_end_at           DATETIME NULL,
    work_zone_id           BIGINT   NULL,
    total_work_minutes     INT      NULL,
    PRIMARY KEY (work_log_id),
    CONSTRAINT fk_work_log_worker FOREIGN KEY (worker_id) REFERENCES worker (worker_id),
    CONSTRAINT fk_work_log_zone   FOREIGN KEY (work_zone_id) REFERENCES `zone` (zone_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS attendance_request (
    attendance_request_id BIGINT      NOT NULL AUTO_INCREMENT,
    worker_id              BIGINT      NOT NULL,
    request_type            VARCHAR(12) NOT NULL COMMENT 'LEAVE / OVERTIME / CORRECTION',
    target_date             DATE        NOT NULL,
    start_time              TIME        NULL COMMENT '연차는 NULL',
    end_time                TIME        NULL,
    reason                  VARCHAR(255) NULL,
    status                  VARCHAR(10) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
    reviewed_by             BIGINT      NULL COMMENT 'manager_account.admin_id. 해당 테이블 없어 FK 미설정',
    reviewed_at             DATETIME    NULL,
    created_at              DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (attendance_request_id),
    CONSTRAINT fk_attendance_request_worker FOREIGN KEY (worker_id) REFERENCES worker (worker_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS notification (
    notification_id        BIGINT      NOT NULL AUTO_INCREMENT,
    event_id                BIGINT      NULL,
    sos_id                  BIGINT      NULL COMMENT 'sos_request.sos_id. 해당 테이블 없어 FK 미설정',
    recipient_type          VARCHAR(10) NOT NULL COMMENT 'WORKER / COWORKER / MANAGER',
    recipient_worker_id     BIGINT      NULL,
    recipient_admin_id      BIGINT      NULL COMMENT 'manager_account.admin_id. 해당 테이블 없어 FK 미설정',
    channel                 VARCHAR(10) NOT NULL DEFAULT 'PUSH' COMMENT 'PUSH / SMS / IN_APP',
    message                 VARCHAR(255) NULL,
    status                  VARCHAR(10) NOT NULL DEFAULT 'SENT' COMMENT 'SENT / CONFIRMED / FAILED',
    triggered_by            VARCHAR(10) NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM / MANAGER',
    triggered_by_admin_id   BIGINT      NULL,
    sent_at                 DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at            DATETIME    NULL,
    PRIMARY KEY (notification_id),
    CONSTRAINT fk_notification_event           FOREIGN KEY (event_id) REFERENCES hazard_event (event_id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_recipient_worker FOREIGN KEY (recipient_worker_id) REFERENCES worker (worker_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- sos_request, manager_account 가 생기면 그때 추가할 것:
--   ALTER TABLE notification ADD CONSTRAINT fk_notification_sos FOREIGN KEY (sos_id) REFERENCES sos_request (sos_id);
--   ALTER TABLE notification ADD CONSTRAINT fk_notification_recipient_admin FOREIGN KEY (recipient_admin_id) REFERENCES manager_account (admin_id);
--   ALTER TABLE notification ADD CONSTRAINT fk_notification_triggered_by_admin FOREIGN KEY (triggered_by_admin_id) REFERENCES manager_account (admin_id);
--   ALTER TABLE attendance_request ADD CONSTRAINT fk_attendance_request_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES manager_account (admin_id);
