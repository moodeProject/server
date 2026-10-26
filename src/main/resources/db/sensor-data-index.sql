-- sensor_data 조회 인덱스 (선택 사항 — 코드 수정만으로도 타임아웃은 해소됨)
--
-- 헬멧별 최신 상태 조회(findLatestStatusPerDevice, findTop1ByDeviceIdOrderByIdDesc)가
-- deviceId + id 로 찾으므로 이 인덱스가 있으면 테이블 전체를 훑지 않는다.
-- 로컬 MySQL 30만 건 기준: 인덱스 없이 약 0.8초 -> 인덱스 있으면 약 0.2초. 생성 자체는 약 1.6초.
--
-- prod 는 ddl-auto=validate 라 자동으로 만들어지지 않는다. EC2 에서 RDS 에 접속해 수동 실행할 것.
-- mysql 클라이언트로 넣을 때는 --default-character-set=utf8mb4 를 줄 것 (sensor-data-migration.sql 참고).

CREATE INDEX idx_sensor_data_device_id_id ON sensor_data (device_id, id);
