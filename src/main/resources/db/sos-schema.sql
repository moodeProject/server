-- SOS 요청 테이블
--
--   local : ddl-auto=update   -> 앱 기동 시 자동 생성되므로 이 스크립트를 실행할 필요 없음
--   prod  : ddl-auto=validate -> 테이블이 없으면 기동에 실패하므로 develop 에 머지(=자동 배포)하기 전에
--                                 이 스크립트를 반드시 수동 실행해야 함
--
-- ⚠ Windows 의 mysql.exe 로 실행할 때는 --default-character-set=utf8mb4 를 줄 것
--      mysql -u root -p --default-character-set=utf8mb4 safehelmet < sos-schema.sql
--
-- 참조 대상(작업자/헬멧/구역)이 삭제돼도 SOS 기록은 남도록 ON DELETE SET NULL.
-- 누가 신고했는지는 requester_name / requester_employee_no 로 남는다.

CREATE TABLE IF NOT EXISTS sos (
    sos_id                BIGINT      NOT NULL AUTO_INCREMENT,
    requester_id          BIGINT      NULL,
    requester_name        VARCHAR(50) NOT NULL COMMENT '신고 시점 이름 (작업자 삭제 대비)',
    requester_employee_no VARCHAR(20) NOT NULL COMMENT '신고 시점 사번',
    helmet_id             BIGINT      NULL COMMENT '신고 시점 착용 헬멧',
    zone_id               BIGINT      NULL COMMENT '신고 시점 구역',
    zone_updated_at       DATETIME    NULL COMMENT '그 구역이 기록된 시각',
    status                VARCHAR(10) NOT NULL DEFAULT 'REQUESTED',
    created_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at           DATETIME    NULL,
    PRIMARY KEY (sos_id),
    KEY idx_sos_status_created (status, created_at),
    CONSTRAINT fk_sos_requester FOREIGN KEY (requester_id) REFERENCES worker (worker_id) ON DELETE SET NULL,
    CONSTRAINT fk_sos_helmet    FOREIGN KEY (helmet_id)    REFERENCES helmet (helmet_id) ON DELETE SET NULL,
    CONSTRAINT fk_sos_zone      FOREIGN KEY (zone_id)      REFERENCES `zone` (zone_id)   ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
