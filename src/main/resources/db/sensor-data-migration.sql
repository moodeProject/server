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

-- 구역(zoneId) 컬럼 추가 (헬멧 BLE 비콘 구역 판정 연동분)
-- 헬멧이 보낸 구역 추정값 원본 문자열을 그대로 저장한다.
-- 위와 마찬가지로 ddl-auto=validate 라 배포 전 반영 필수. 재실행 시 "Duplicate column name" 나면 이미 반영된 것.
ALTER TABLE sensor_data
    ADD COLUMN zone_id VARCHAR(255) NULL COMMENT '헬멧이 BLE 비콘 스캔으로 추정한 구역 (예: ZONE-01)';

-- HRV / 피로 / 온열질환 컬럼 추가 (외부 기상 API 연동 온열질환 판정분, PR #14)
-- SensorData 엔티티에는 이미 필드가 있는데 이 ALTER 가 빠져 있었다. ddl-auto=validate 인
-- 운영에서는 엔티티에 있는 컬럼이 DB에 없으면 스키마 검증에 걸려 앱이 기동되지 않는다.
-- 재실행 시 "Duplicate column name" 나면 이미 반영된 것.
ALTER TABLE sensor_data
    ADD COLUMN hrv                DOUBLE NULL COMMENT '심박변이도 RMSSD(ms). 계산 불가 시 NULL',
    ADD COLUMN fatigue_abnormal   BIT(1) NULL COMMENT '개인 HRV 기준선 대비 이탈 여부',
    ADD COLUMN heat_risk_abnormal BIT(1) NULL COMMENT '활동량 대비 심박 지속 상승 여부(기상정보 미반영 1차 지표)';

-- zone_code 시드 (헬멧 BLE 비콘 구역 판정 연동분)
-- SensorDataServiceImpl 이 이 zone_code로 zone 테이블을 조회해서 Worker.currentZone을 갱신한다.
-- 실제 zone_code가 이미 등록되어 있는지 API로 확인할 방법이 없어서, 있으면 무시되고
-- 없으면 생성되도록 INSERT IGNORE로 넣는다. zone_name/floor는 임시값이니 실제 현장 명칭이
-- 정해지면 UPDATE로 바꿔도 된다 (zone_code 자체를 바꾸는 게 아니라서 안전함).
INSERT IGNORE INTO `zone` (zone_code, zone_name, floor, risk_level, access_restricted)
VALUES ('ZONE-01', '구역 1 (임시명)', NULL, 'LOW', FALSE),
       ('ZONE-02', '구역 2 (임시명)', NULL, 'LOW', FALSE);
