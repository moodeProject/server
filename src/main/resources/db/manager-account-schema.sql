-- 관리자 계정(manager_account) 테이블 DDL
--
-- local : ddl-auto=update -> 앱 기동 시 자동 생성되므로 이 스크립트를 실행할 필요 없음
-- prod  : ddl-auto=validate -> 테이블이 없으면 기동에 실패하므로 배포 전 이 스크립트를 수동 실행해야 함
--
-- 회원가입 직후에는 status=PENDING 이고, 다른 관리자가 승인(APPROVED)해야 로그인이 가능하다
-- (화면 설계서 "가입 후 관리자 승인이 완료되면 로그인이 가능합니다"). 다만 최초 관리자를 누가
-- 승인하느냐는 문제는 auth.admin.auto-approve-signup 설정으로 해소한다 — 켜두면 가입 즉시
-- APPROVED 로 저장되어(이때 approved_by 는 승인한 사람이 없으므로 null) 별도 시드가 필요 없다.
--
-- zone / hazard_event / hazard_event_action / attendance_request / notification 에 남아있는
-- admin 참조 FK TODO 는 이 테이블이 생겼다고 바로 걸지 않는다 — 기존 기능에 영향 범위가 있어
-- 별도로 검토 후 처리.

CREATE TABLE IF NOT EXISTS manager_account (
    admin_id      BIGINT       NOT NULL AUTO_INCREMENT,
    login_id      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    email         VARCHAR(100) NOT NULL,
    phone         VARCHAR(20)  NULL,
    department    VARCHAR(50)  NULL,
    status        VARCHAR(10)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
    approved_by   BIGINT       NULL COMMENT 'manager_account.admin_id. 승인한 관리자',
    approved_at   DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (admin_id),
    UNIQUE KEY uk_manager_account_login_id (login_id),
    UNIQUE KEY uk_manager_account_email (email),
    CONSTRAINT fk_manager_account_approved_by FOREIGN KEY (approved_by) REFERENCES manager_account (admin_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
