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
