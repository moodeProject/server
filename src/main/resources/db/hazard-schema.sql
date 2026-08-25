-- 영상 기반 외부 위험요소 감지 테이블 DDL
--
-- 이 프로젝트는 Flyway/Liquibase 를 쓰지 않는다.
--   local  : ddl-auto=update  -> 앱 기동 시 자동 생성되므로 이 스크립트를 실행할 필요 없음
--   prod   : ddl-auto=validate -> 테이블이 없으면 기동에 실패하므로 배포 전 이 스크립트를 수동 실행해야 함
--
-- Spring Boot 는 classpath 루트의 schema.sql 만 자동 실행하므로 db/ 아래의 이 파일은 자동 실행되지 않는다.

CREATE TABLE IF NOT EXISTS `zone` (
    zone_id                BIGINT      NOT NULL AUTO_INCREMENT,
    zone_code              VARCHAR(20) NOT NULL,
    zone_name              VARCHAR(50) NOT NULL,
    floor                  VARCHAR(20) NULL,
    risk_level             VARCHAR(10) NOT NULL DEFAULT 'LOW',
    access_restricted      BOOLEAN     NOT NULL DEFAULT FALSE,
    restricted_by_admin_id BIGINT      NULL COMMENT 'manager_account.admin_id',
    restricted_at          DATETIME    NULL,
    created_at             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (zone_id),
    UNIQUE KEY uk_zone_zone_code (zone_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS worker (
    worker_id           BIGINT       NOT NULL AUTO_INCREMENT,
    employee_no         VARCHAR(20)  NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    name                VARCHAR(50)  NOT NULL,
    phone               VARCHAR(20)  NULL,
    department          VARCHAR(50)  NULL,
    status              VARCHAR(10)  NOT NULL DEFAULT 'OFF_DUTY',
    current_zone_id     BIGINT       NULL,
    pos_x               DECIMAL(8,2) NULL,
    pos_y               DECIMAL(8,2) NULL,
    nearest_beacon_id   VARCHAR(30)  NULL,
    rssi                SMALLINT     NULL,
    position_updated_at DATETIME     NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (worker_id),
    UNIQUE KEY uk_worker_employee_no (employee_no),
    CONSTRAINT fk_worker_current_zone FOREIGN KEY (current_zone_id) REFERENCES `zone` (zone_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS helmet (
    helmet_id             BIGINT       NOT NULL AUTO_INCREMENT,
    helmet_no             VARCHAR(20)  NOT NULL COMMENT 'AI 서버가 보내는 helmetId("H-001")',
    device_id             VARCHAR(50)  NOT NULL,
    worker_id             BIGINT       NULL,
    sensor_status         VARCHAR(15)  NOT NULL DEFAULT 'DISCONNECTED',
    operational_status    VARCHAR(15)  NOT NULL DEFAULT 'STANDBY',
    last_communication_at DATETIME     NULL,
    api_key_hash          VARCHAR(255) NULL,
    registered_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (helmet_id),
    UNIQUE KEY uk_helmet_helmet_no (helmet_no),
    UNIQUE KEY uk_helmet_device_id (device_id),
    CONSTRAINT fk_helmet_worker FOREIGN KEY (worker_id) REFERENCES worker (worker_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS hazard_event (
    event_id        BIGINT       NOT NULL AUTO_INCREMENT,
    event_uid       CHAR(32)     NOT NULL COMMENT 'AI 서버가 만든 사건 식별자. 사건당 여러 행 가능',
    helmet_id       BIGINT       NOT NULL,
    worker_id       BIGINT       NULL COMMENT '수신 시점 배정 스냅샷. 미배정 헬멧이면 NULL',
    zone_id         BIGINT       NULL,
    event_category  VARCHAR(16)  NOT NULL COMMENT 'EXTERNAL / HEALTH / FALL',
    hazard_type     VARCHAR(32)  NOT NULL COMMENT 'PUDDLE / POTHOLE / FORKLIFT',
    severity        VARCHAR(16)  NOT NULL COMMENT 'WARNING / DANGER. 서버가 hazard_type 으로 판정',
    confidence      DECIMAL(5,4) NULL COMMENT 'AI 신뢰도 원본값. 명세 예시 0.8483 을 담으려면 소수 4자리 필요',
    source          VARCHAR(12)  NOT NULL DEFAULT 'VISION_AI' COMMENT 'VISION_AI / IMU_HEALTH / MANUAL',
    description     VARCHAR(255) NULL COMMENT '목록/상세의 상황 설명. 비면 hazard_type 기본 문구 사용',
    location_detail VARCHAR(100) NULL,

    -- 오버레이용. bbox 가 픽셀 좌표라 기준 해상도 없이는 프론트가 스케일을 맞출 수 없다.
    frame_width     INT          NULL,
    frame_height    INT          NULL,
    detections_json TEXT         NULL COMMENT 'bbox 배열 원본',

    occurred_at     DATETIME(3)  NOT NULL COMMENT '목록 정렬 기준(사건 발생 시각)',

    status          VARCHAR(16)  NOT NULL DEFAULT 'UNHANDLED' COMMENT 'UNHANDLED / IN_PROGRESS / RESOLVED',
    handled_by      BIGINT       NULL COMMENT 'manager_account.admin_id',
    handled_at      DATETIME(3)  NULL,

    -- 사고 원인 분석 / AI 리포트 담당 영역. 영상 감지 경로에서는 채우지 않는다.
    cause_primary         VARCHAR(100) NULL,
    cause_secondary       VARCHAR(100) NULL,
    cause_contributing    VARCHAR(200) NULL,
    cause_final_estimated VARCHAR(200) NULL,
    ai_report_text        TEXT         NULL,

    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수신 기록 생성 시각',

    PRIMARY KEY (event_id),
    -- event_uid 는 UNIQUE 가 아니다. 한 사건에서 위험이 연달아 감지되면 같은 event_uid 로
    -- 서로 다른 메시지가 여러 건 도착하고 각각 별도 알림 대상이므로 별도 행으로 남아야 한다.
    -- 중복 방지는 inbound_message.idempotency_key 가 담당한다.
    KEY idx_hazard_event_event_uid (event_uid),
    KEY idx_hazard_event_occurred_at (occurred_at),
    CONSTRAINT fk_hazard_event_helmet FOREIGN KEY (helmet_id) REFERENCES helmet (helmet_id),
    CONSTRAINT fk_hazard_event_worker FOREIGN KEY (worker_id) REFERENCES worker (worker_id),
    CONSTRAINT fk_hazard_event_zone   FOREIGN KEY (zone_id)   REFERENCES `zone` (zone_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS hazard_event_action (
    action_id       BIGINT      NOT NULL AUTO_INCREMENT,
    hazard_event_id BIGINT      NOT NULL,
    action_type     VARCHAR(20) NOT NULL COMMENT 'SEND_WARNING / BLOCK_ZONE / RESOLVE',
    performed_by    BIGINT      NULL COMMENT 'manager_account.admin_id',
    performed_at    DATETIME    NOT NULL,
    notified_count  INT         NULL,
    PRIMARY KEY (action_id),
    -- 같은 조치를 두 번 실행하면 409 로 막는다. 동시 클릭도 여기서 걸린다.
    UNIQUE KEY uk_hazard_event_action (hazard_event_id, action_type),
    CONSTRAINT fk_hazard_event_action_event FOREIGN KEY (hazard_event_id) REFERENCES hazard_event (event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS hazard_clip (
    clip_id                BIGINT       NOT NULL AUTO_INCREMENT,
    hazard_event_id        BIGINT       NOT NULL,
    storage_type           VARCHAR(8)   NOT NULL COMMENT 'S3 / LOCAL',
    bucket                 VARCHAR(64)  NULL,
    object_key             VARCHAR(512) NOT NULL COMMENT '공개 URL 이 아니라 객체 키. 조회 시 pre-signed URL 발급',
    duration_seconds       DECIMAL(6,2) NOT NULL,
    frame_count            INT          NOT NULL,
    marked_offset_seconds  DECIMAL(6,2) NULL COMMENT '클립 안에서 사고 시점 마커 위치',
    PRIMARY KEY (clip_id),
    UNIQUE KEY uk_hazard_clip_hazard_event_id (hazard_event_id),
    CONSTRAINT fk_hazard_clip_hazard_event FOREIGN KEY (hazard_event_id) REFERENCES hazard_event (event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS inbound_message (
    idempotency_key CHAR(32)    NOT NULL,
    event_uid       CHAR(32)    NOT NULL,
    event_type      VARCHAR(24) NOT NULL,
    received_at     DATETIME(3) NOT NULL,
    PRIMARY KEY (idempotency_key),
    KEY idx_inbound_message_event_uid (event_uid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- manager_account 테이블은 이 프로젝트에 아직 없어 admin 참조 FK 는 걸지 않았다.
-- 해당 테이블이 생기면 그때 추가할 것:
--   ALTER TABLE hazard_event        ADD CONSTRAINT fk_hazard_event_admin  FOREIGN KEY (handled_by) REFERENCES manager_account (admin_id);
--   ALTER TABLE hazard_event_action ADD CONSTRAINT fk_action_admin        FOREIGN KEY (performed_by) REFERENCES manager_account (admin_id);
--   ALTER TABLE `zone`              ADD CONSTRAINT fk_zone_restricted_by  FOREIGN KEY (restricted_by_admin_id) REFERENCES manager_account (admin_id);
