-- 작업자 로그인 데모용 마스터 계정 시드
--
-- local : ddl-auto=update 로 worker 테이블은 이미 있으므로 이 INSERT 만 실행하면 됨
-- prod  : 배포 전 manager-account-schema.sql 과 함께 수동 실행
--
-- 작업자는 회원가입 API 가 없어(데모 범위 밖) 로그인 테스트용 계정을 시드로만 공급한다.
-- 사번 WK-20241103 은 화면 설계서 작업자 홈 화면(docs/ui/worker/img_1.png)의 예시 사번과 맞췄다.
--
-- password_hash 는 평문 "Worker!2024" 의 BCrypt 해시(BCryptPasswordEncoder, strength=10 기본값)다.
-- 평문 자체는 application-local.yml 의 demo.worker.default-password 에 적어뒀다 — 데모 시연 때
-- 사람이 참고하는 값일 뿐, 서버 로직은 평문을 전혀 읽지 않는다. 인코더를 바꾸면 이 해시도 다시 만들어야 한다.
INSERT INTO worker (employee_no, password_hash, name, department, status)
VALUES ('WK-20241103', '$2a$10$K1BBbzbx6ilQh07PlHHka.vcWAnc6Q4nAp5R3Pp9mz.yLM0tf3Dm.', '김현석', '현장관리팀', 'WORKING')
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash);

-- 프론트 테스트용 작업자 계정. WK-20241103 은 2번 헬멧에 묶여 있어서, 1번 헬멧(H-001)이 배정된
-- 테스트 작업자 WK-0001 에도 로그인을 열어준다. 행 자체는 sensor-data-migration.sql 이 이미
-- 만들어 두므로(password_hash 는 자리표시자) 여기서는 해시만 실제 값으로 바꾼다 — 행이 없으면
-- 아무 일도 일어나지 않으니 sensor-data-migration.sql 을 먼저 실행할 것.
--
-- password_hash 는 평문 "Worker!0001" 의 BCrypt 해시(위와 같은 인코더 설정)다.
UPDATE worker
SET password_hash = '$2a$10$BxcQhMdJulElxSuQLrj2PeKv3rB9ebwaxCW9iN7qprYagOwrBfFiW'
WHERE employee_no = 'WK-0001';
