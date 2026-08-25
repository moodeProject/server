# hazard_event ERD 갱신 (명세 기준)

`hazard-event-erd-patch.json` 은 기존 ERD의 `hazard_event` 를 프론트 API 명세에 맞게 고친 것과,
그 과정에서 분리된 3개 테이블을 담고 있다.

## 적용 방법

ERD 파일의 `entityData` 배열에서

- `_id` 가 `g2g46Aa3R9Tw7jua3` 인 `hazard_event` 항목을 **패치 파일의 것으로 교체**
- 나머지 3개(`hazard_clip`, `inbound_message`, `hazard_event_action`)를 **배열에 추가**

`hazard_event` 의 `_id`(`g2g46Aa3R9Tw7jua3`)와 PK `event_id` 의 `_id`(`i64xZank4JoE9vxyP`)는
그대로 유지했다. `notification.event_id` 가 이 값들로 참조하고 있어서, 바꾸면 그쪽 관계선이 끊긴다.

`manager_account` / `worker` / `helmet` / `zone` / `health_metric` / `notification` /
`sos_request` / `work_log` 은 건드리지 않았다.

## hazard_event 변경 내역과 이유

| 변경 | 이유 |
|---|---|
| `idempotency_key` 제거 → `inbound_message` 테이블로 분리 | 이벤트에 인라인하면 한 사건에서 연달아 감지된 다른 위험(물웅덩이 → 지게차)을 저장할 수 없다. 중복 판정은 메시지 단위(`idempotency_key`), 사건 식별은 `event_uid` 로 분리해야 한다 |
| `event_uid CHAR(32)` 추가 (UNIQUE 아닌 INDEX) | AI 서버의 사건 식별자. 클립이 이 값으로 이벤트를 찾아온다. 사건당 여러 행이 정상이라 UNIQUE 를 걸면 안 된다 |
| `clip_s3_url` / `clip_thumbnail_s3_url` / `clip_duration_sec` / `clip_cctv_id` 제거 → `hazard_clip` 으로 분리 | 명세상 재생 URL 은 "요청할 때마다 새로 발급되는 15분짜리 임시 링크"다. URL 을 컬럼에 저장하면 만료 관리가 불가능하다. 저장은 `bucket` + `object_key` 만 하고 조회 시점에 pre-signed URL 을 발급한다. 또 클립은 감지보다 10~20초 늦게 도착해 "이벤트는 있고 클립은 없는" 상태가 정상이라 별도 테이블이 맞다 |
| `frame_width` / `frame_height` / `detections_json` 추가 | 명세의 `detection` 블록에 필요하다. bbox 가 픽셀 좌표라 기준 해상도가 없으면 프론트가 영상 위 박스 스케일을 맞출 수 없다 |
| `confidence` `DECIMAL(4,3)` → `DECIMAL(5,4)` | 명세 예시값 `0.8483` 이 `DECIMAL(4,3)` 에 안 들어간다. `0.848` 로 반올림된다 |
| `process_status ENUM('UNPROCESSED','IN_PROGRESS','REST_RECOMMENDED','RESOLVED')` → `status ENUM('UNHANDLED','IN_PROGRESS','RESOLVED')` | 명세가 `UNHANDLED / IN_PROGRESS / RESOLVED` 로 합의되어 있다. `UNPROCESSED` 와 이름이 달라 그대로 두면 프론트가 매칭에 실패한다 |
| `handled_by` / `handled_at` 추가 | 조치 실행 결과(누가·언제)를 남겨야 상세의 `status` 갱신과 이력 조회가 성립한다 |
| `worker_id` NOT NULL → NULL | 미배정 헬멧에서도 위험은 감지된다. NOT NULL 이면 그 이벤트를 아예 저장하지 못해 유실된다 |
| `hazard_type` `VARCHAR(30)` → `VARCHAR(32)` | 실무상 차이는 없고 코드와 맞춘 값 |
| `created_at` 유지 | `occurred_at`(사건 발생)과 구분되는 수신 기록 생성 시각 |
| `description` / `location_detail` / `source` / `cause_*` / `ai_report_text` 유지 | 원인 분석·AI 리포트 담당 영역. 영상 감지 경로에서는 채우지 않지만 전부 nullable 이라 그대로 둔다. `description` 은 비어 있으면 `hazard_type` 의 기본 문구로 대체된다 |

## 새로 추가된 3개 테이블

- **`hazard_clip`** — 증거 영상. `hazard_event_id` 에 UNIQUE 를 걸어 1:0..1 을 강제한다. 공개 URL 을 저장하지 않는다
- **`inbound_message`** — AI 서버 메시지의 멱등 처리 로그. PK 제약으로 재전송 중복을 막는다
- **`hazard_event_action`** — 조치 실행 이력. 명세의 409(이미 같은 조치를 실행함) 판정에 필요하다. `status` 만으로는 `SEND_WARNING` 을 이미 보냈는지 구분할 수 없다. ERD의 `notification` 테이블이 만들어지면 그쪽으로 옮겨도 된다

## 코드/DDL 동기화 상태

`src/main/java/com/moode/server/domain/` 의 엔티티와
`src/main/resources/db/hazard-schema.sql` 이 이 ERD와 일치한다.
운영은 `ddl-auto: validate` 라 배포 전 해당 SQL 을 수동 실행해야 한다.
