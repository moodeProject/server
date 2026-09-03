-- sensor_data 컬럼 추가 (커밋 368453c "MAX30102 데이터 필드 추가 및 응답 구조 개선" 반영분)
--
-- 이 변경은 영상 위험요소 감지 기능의 것이 아니라 기존 센서 기능의 것이다.
-- 다만 운영이 ddl-auto=validate 라 이 컬럼들이 없으면 앱 기동 자체가 실패하므로,
-- 배포 전에 hazard-schema.sql 과 함께 반영되어 있어야 한다.
--
-- 기존 state 컬럼은 fall_state / health_state 로 분리되었으나 nullable 이라 그대로 두어도
-- INSERT 는 깨지지 않는다. 데이터 이관이 끝난 뒤 별도로 정리할 것.
--
-- MySQL 8.0 이상에서 IF NOT EXISTS 를 지원하지 않으므로, 이미 적용된 환경에서 재실행하면
-- "Duplicate column name" 오류가 난다. 그 경우 이미 반영된 것이니 무시해도 된다.

ALTER TABLE sensor_data
    ADD COLUMN heart_rate      DOUBLE       NULL COMMENT 'MAX30102 심박수',
    ADD COLUMN spo2            DOUBLE       NULL COMMENT 'MAX30102 산소포화도',
    ADD COLUMN health_abnormal BIT(1)       NULL,
    ADD COLUMN fall_state      VARCHAR(255) NULL COMMENT '기존 state 를 추락/건강으로 분리한 것',
    ADD COLUMN health_state    VARCHAR(255) NULL,
    ADD COLUMN fall_confidence DOUBLE       NULL COMMENT '추락 판단 AI 신뢰도. 호출 실패 시 NULL';

-- 자세 상태(posture) 컬럼 추가 (근로자 자세 이상 조회 API 연동분)
-- 위와 마찬가지로 ddl-auto=validate 라 배포 전 반영 필수. 재실행 시 "Duplicate column name" 나면 이미 반영된 것.
ALTER TABLE sensor_data
    ADD COLUMN posture VARCHAR(255) NULL COMMENT '센서가 보낸 자세 상태 원본 STABLE/STUMBLE/COLLAPSE';
