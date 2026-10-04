-- 작업자/헬멧 관리(CRUD) 기능을 위한 스키마 변경
--
-- local : ddl-auto=update -> 앱 기동 시 자동 반영되므로 이 스크립트를 실행할 필요 없음
-- prod  : ddl-auto=validate -> 배포 전 이 스크립트를 수동 실행해야 함
--
-- 전부 기존 행을 건드리지 않는 추가(ADD COLUMN DEFAULT / 신규 UNIQUE)라 worker 시드(WK-20241103 등)는
-- 그대로 유지된다. 제약을 추가하는 두 줄은 기존 데이터에 위반 사례가 있으면 실패하므로, 먼저 아래
-- 확인 쿼리로 비어있는지 보고 실행할 것.

-- 1) 선행 확인 — 결과가 0건이어야 안전하게 유니크 제약을 걸 수 있다.
-- SELECT zone_name, COUNT(*) FROM zone GROUP BY zone_name HAVING COUNT(*) > 1;
-- SELECT worker_id, COUNT(*) FROM helmet WHERE worker_id IS NOT NULL GROUP BY worker_id HAVING COUNT(*) > 1;

-- 2) 소프트 삭제 플래그
ALTER TABLE worker ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE helmet ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- 3) 헬멧 등록 모달의 메모
ALTER TABLE helmet ADD COLUMN memo VARCHAR(255) NULL;

-- 4) 헬멧 1개 : 작업자 1명을 DB 레벨에서도 강제 (MySQL InnoDB 는 UNIQUE 컬럼의 NULL 끼리는
-- 중복으로 치지 않으므로, 미배정(worker_id=NULL) 헬멧이 여러 개 있어도 이 제약에 걸리지 않는다)
ALTER TABLE helmet ADD CONSTRAINT uk_helmet_worker_id UNIQUE (worker_id);

-- 5) "소속 구역" 텍스트로 find-or-create 할 때 이름 중복 방지
ALTER TABLE zone ADD CONSTRAINT uk_zone_zone_name UNIQUE (zone_name);
