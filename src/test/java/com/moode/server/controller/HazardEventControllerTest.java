package com.moode.server.controller;

import com.moode.server.domain.*;
import com.moode.server.repository.*;
import com.moode.server.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HazardEventControllerTest {

    private static final String PATH = "/api/v1/hazard-events";

    @Autowired private MockMvc mockMvc;
    @Autowired private HazardEventRepository hazardEventRepository;
    @Autowired private HazardClipRepository hazardClipRepository;
    @Autowired private HazardEventActionRepository hazardEventActionRepository;
    @Autowired private HelmetRepository helmetRepository;
    @Autowired private WorkerRepository workerRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TestFixtures fixtures;

    private Helmet helmet;

    @BeforeEach
    void clean() {
        hazardEventActionRepository.deleteAll();
        hazardClipRepository.deleteAll();
        hazardEventRepository.deleteAll();
        helmetRepository.deleteAll();
        workerRepository.deleteAll();
        zoneRepository.deleteAll();
        helmet = fixtures.defaultHelmet();
    }

    private HazardEvent saveEvent(String uid, HazardType type, HazardSeverity severity,
                                  HazardStatus status, LocalDateTime occurredAt) {
        return saveEvent(uid, type, severity, status, occurredAt, HazardCategory.EXTERNAL, helmet);
    }

    private HazardEvent saveEvent(String uid, HazardType type, HazardSeverity severity,
                                  HazardStatus status, LocalDateTime occurredAt,
                                  HazardCategory category, Helmet helmet) {
        return hazardEventRepository.save(HazardEvent.builder()
                .eventUid(uid)
                .helmet(helmet)
                .worker(helmet.getWorker())
                .zone(helmet.getWorker() == null ? null : helmet.getWorker().getCurrentZone())
                .category(category)
                .hazardType(type)
                .severity(severity)
                .confidence(new BigDecimal("0.8483"))
                .frameWidth(960)
                .frameHeight(540)
                .detectionsJson("""
                        [{"type":"%s","confidence":0.8802,"bbox":[412.0,233.5,690.2,388.1]}]
                        """.formatted(type.name().toLowerCase()))
                .occurredAt(occurredAt)
                .status(status)
                .build());
    }

    // ===================== 1. 목록 =====================

    @Test
    @DisplayName("목록은 최신순이고 라벨과 작업자/구역/헬멧 정보를 함께 준다")
    void listReturnsSpecShape() throws Exception {
        saveEvent("aaa", HazardType.PUDDLE, HazardSeverity.WARNING, HazardStatus.UNHANDLED,
                LocalDateTime.of(2026, 8, 25, 10, 0));
        saveEvent("bbb", HazardType.FORKLIFT, HazardSeverity.DANGER, HazardStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 8, 25, 12, 0));

        mockMvc.perform(get(PATH).param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                // ApiResponse 로 감싸지 않고 명세 그대로 내려간다
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                // hazardType 은 AI 서버 원본과 같은 소문자
                .andExpect(jsonPath("$.content[0].hazardType").value("forklift"))
                .andExpect(jsonPath("$.content[0].hazardLabel").value("지게차 접근"))
                .andExpect(jsonPath("$.content[0].severity").value("DANGER"))
                .andExpect(jsonPath("$.content[0].severityLabel").value("위험"))
                .andExpect(jsonPath("$.content[0].statusLabel").value("처리중"))
                .andExpect(jsonPath("$.content[0].worker.name").value("박민수"))
                .andExpect(jsonPath("$.content[0].worker.employeeNo").value("WK-20241103"))
                .andExpect(jsonPath("$.content[0].zone.code").value("A-3F"))
                .andExpect(jsonPath("$.content[0].zone.name").value("A구역 3층"))
                .andExpect(jsonPath("$.content[0].helmetNo").value("H-001"))
                .andExpect(jsonPath("$.content[0].hasClip").value(false))
                // 오프셋이 붙은 ISO8601
                .andExpect(jsonPath("$.content[0].occurredAt").value(org.hamcrest.Matchers.containsString("+09:00")))
                .andExpect(jsonPath("$.content[1].hazardType").value("puddle"));
    }

    @Test
    @DisplayName("summary 는 현재 필터와 무관하게 항상 전체 기준 집계다")
    void summaryIgnoresFilters() throws Exception {
        saveEvent("aaa", HazardType.PUDDLE, HazardSeverity.WARNING, HazardStatus.UNHANDLED,
                LocalDateTime.of(2026, 8, 25, 10, 0));
        saveEvent("bbb", HazardType.FORKLIFT, HazardSeverity.DANGER, HazardStatus.UNHANDLED,
                LocalDateTime.of(2026, 8, 25, 12, 0));
        saveEvent("ccc", HazardType.PUDDLE, HazardSeverity.WARNING, HazardStatus.UNHANDLED,
                LocalDateTime.of(2026, 8, 25, 13, 0), HazardCategory.FALL, helmet);

        mockMvc.perform(get(PATH).param("severity", "DANGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                // 필터로 1건만 조회됐어도 배지는 전체 기준
                .andExpect(jsonPath("$.summary.all").value(3))
                .andExpect(jsonPath("$.summary.external").value(2))
                .andExpect(jsonPath("$.summary.fall").value(1))
                .andExpect(jsonPath("$.summary.health").value(0));
    }

    @Test
    @DisplayName("category / status / workerId / 기간 필터가 적용된다")
    void filtersApply() throws Exception {
        saveEvent("aaa", HazardType.PUDDLE, HazardSeverity.WARNING, HazardStatus.UNHANDLED,
                LocalDateTime.of(2026, 8, 25, 10, 0));
        saveEvent("bbb", HazardType.FORKLIFT, HazardSeverity.DANGER, HazardStatus.RESOLVED,
                LocalDateTime.of(2026, 8, 25, 12, 0));

        mockMvc.perform(get(PATH).param("status", "RESOLVED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].hazardType").value("forklift"));

        mockMvc.perform(get(PATH).param("category", "HEALTH"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get(PATH).param("workerId", String.valueOf(helmet.getWorker().getId())))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get(PATH).param("workerId", "999999"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get(PATH)
                        .param("from", "2026-08-25T11:00:00+09:00")
                        .param("to", "2026-08-25T13:00:00+09:00"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].hazardType").value("forklift"));
    }

    // ===================== 2. 상세 =====================

    @Test
    @DisplayName("상세는 오버레이용 detection 을 주고, 클립이 없으면 clip 은 null 이다")
    void detailWithoutClip() throws Exception {
        Long eventId = saveEvent("aaa", HazardType.PUDDLE, HazardSeverity.WARNING,
                HazardStatus.UNHANDLED, LocalDateTime.of(2026, 8, 25, 12, 0)).getId();

        mockMvc.perform(get(PATH + "/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hazardLabel").value("물웅덩이"))
                .andExpect(jsonPath("$.riskDescription").value("미끄럼 및 낙상"))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.recommendedAction").exists())
                .andExpect(jsonPath("$.detection.frameWidth").value(960))
                .andExpect(jsonPath("$.detection.frameHeight").value(540))
                .andExpect(jsonPath("$.detection.boxes[0].type").value("puddle"))
                .andExpect(jsonPath("$.detection.boxes[0].label").value("물웅덩이"))
                .andExpect(jsonPath("$.detection.boxes[0].bbox[0]").value(412.0))
                // 아직 녹화 중. 오류가 아니라 정상 상태다.
                .andExpect(jsonPath("$.clip").doesNotExist());
    }

    @Test
    @DisplayName("클립이 있으면 저장된 값이 아니라 발급된 재생 URL 을 내려주고 내부 저장 위치는 감춘다")
    void detailIssuesPlaybackUrl() throws Exception {
        HazardEvent event = saveEvent("aaa", HazardType.FORKLIFT, HazardSeverity.DANGER,
                HazardStatus.UNHANDLED, LocalDateTime.of(2026, 8, 25, 12, 0));
        hazardClipRepository.save(HazardClip.builder()
                .hazardEvent(event)
                .storageType(StorageType.LOCAL)
                .objectKey("clips/2026/08/25/H-001_083744.mp4")
                .durationSeconds(new BigDecimal("16.30"))
                .frameCount(163)
                .markedOffsetSeconds(new BigDecimal("7.90"))
                .build());

        mockMvc.perform(get(PATH + "/" + event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clip.durationSeconds").value(16.30))
                .andExpect(jsonPath("$.clip.markedOffsetSeconds").value(7.90))
                .andExpect(jsonPath("$.clip.playbackUrl")
                        .value("http://localhost:8080/clips/clips/2026/08/25/H-001_083744.mp4"))
                .andExpect(jsonPath("$.clip.bucket").doesNotExist())
                .andExpect(jsonPath("$.clip.objectKey").doesNotExist());

        // 목록의 hasClip 도 함께 true 가 된다
        mockMvc.perform(get(PATH))
                .andExpect(jsonPath("$.content[0].hasClip").value(true));
    }

    @Test
    @DisplayName("없는 이벤트 상세는 404 다")
    void detailNotFound() throws Exception {
        mockMvc.perform(get(PATH + "/999999")).andExpect(status().isNotFound());
    }

    // ===================== 3. 조치 =====================

    private String action(String actionType) {
        return """
                {"actionType": "%s", "adminId": 7}
                """.formatted(actionType);
    }

    @Test
    @DisplayName("SEND_WARNING 은 IN_PROGRESS 로 바꾸고 알림 인원수와 토스트 문구를 준다")
    void sendWarning() throws Exception {
        Long eventId = saveEvent("aaa", HazardType.FORKLIFT, HazardSeverity.DANGER,
                HazardStatus.UNHANDLED, LocalDateTime.of(2026, 8, 25, 12, 0)).getId();

        mockMvc.perform(post(PATH + "/" + eventId + "/actions")
                        .contentType(MediaType.APPLICATION_JSON).content(action("SEND_WARNING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.actionType").value("SEND_WARNING"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.statusLabel").value("처리중"))
                .andExpect(jsonPath("$.notifiedCount").value(1))
                .andExpect(jsonPath("$.message").value("박민수 작업자에게 경고 알림을 전송했습니다."))
                .andExpect(jsonPath("$.performedAt").exists());

        assertThat(hazardEventRepository.findById(eventId).orElseThrow().getStatus())
                .isEqualTo(HazardStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("같은 조치를 두 번 실행하면 409 다")
    void duplicateActionIsConflict() throws Exception {
        Long eventId = saveEvent("aaa", HazardType.FORKLIFT, HazardSeverity.DANGER,
                HazardStatus.UNHANDLED, LocalDateTime.of(2026, 8, 25, 12, 0)).getId();

        mockMvc.perform(post(PATH + "/" + eventId + "/actions")
                        .contentType(MediaType.APPLICATION_JSON).content(action("SEND_WARNING")))
                .andExpect(status().isOk());

        mockMvc.perform(post(PATH + "/" + eventId + "/actions")
                        .contentType(MediaType.APPLICATION_JSON).content(action("SEND_WARNING")))
                .andExpect(status().isConflict());

        assertThat(hazardEventActionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("BLOCK_ZONE 은 구역을 접근 금지로 바꾸고 구역 작업자에게 알린다")
    void blockZone() throws Exception {
        Long eventId = saveEvent("aaa", HazardType.FORKLIFT, HazardSeverity.DANGER,
                HazardStatus.UNHANDLED, LocalDateTime.of(2026, 8, 25, 12, 0)).getId();
        Long zoneId = helmet.getWorker().getCurrentZone().getId();

        mockMvc.perform(post(PATH + "/" + eventId + "/actions")
                        .contentType(MediaType.APPLICATION_JSON).content(action("BLOCK_ZONE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionType").value("BLOCK_ZONE"))
                .andExpect(jsonPath("$.notifiedCount").value(1));

        Zone zone = zoneRepository.findById(zoneId).orElseThrow();
        assertThat(zone.isAccessRestricted()).isTrue();
        assertThat(zone.getRestrictedByAdminId()).isEqualTo(7L);
        assertThat(zone.getRestrictedAt()).isNotNull();
    }

    @Test
    @DisplayName("RESOLVE 는 상태를 RESOLVED 로 바꾸고 알림은 보내지 않는다")
    void resolve() throws Exception {
        Long eventId = saveEvent("aaa", HazardType.FORKLIFT, HazardSeverity.DANGER,
                HazardStatus.UNHANDLED, LocalDateTime.of(2026, 8, 25, 12, 0)).getId();

        mockMvc.perform(post(PATH + "/" + eventId + "/actions")
                        .contentType(MediaType.APPLICATION_JSON).content(action("RESOLVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.statusLabel").value("확인완료"))
                .andExpect(jsonPath("$.notifiedCount").value(0));

        assertThat(hazardEventRepository.findById(eventId).orElseThrow().getStatus())
                .isEqualTo(HazardStatus.RESOLVED);
    }

    @Test
    @DisplayName("작업자에게 연결된 기기가 없으면 422 다")
    void noDeviceIsUnprocessable() throws Exception {
        // 헬멧이 배정되지 않은 작업자의 이벤트
        Zone zone = fixtures.zone("B-1F", "B구역");
        Worker orphan = fixtures.worker("WK-99999999", "무배정", zone);
        Helmet unassigned = fixtures.helmet("H-999", "ESP32-999", null);

        HazardEvent event = hazardEventRepository.save(HazardEvent.builder()
                .eventUid("zzz")
                .helmet(unassigned)
                .worker(orphan)
                .zone(zone)
                .category(HazardCategory.EXTERNAL)
                .hazardType(HazardType.PUDDLE)
                .severity(HazardSeverity.WARNING)
                .occurredAt(LocalDateTime.of(2026, 8, 25, 12, 0))
                .status(HazardStatus.UNHANDLED)
                .build());

        mockMvc.perform(post(PATH + "/" + event.getId() + "/actions")
                        .contentType(MediaType.APPLICATION_JSON).content(action("SEND_WARNING")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("알 수 없는 actionType 은 400 이다")
    void unknownActionTypeIsBadRequest() throws Exception {
        Long eventId = saveEvent("aaa", HazardType.FORKLIFT, HazardSeverity.DANGER,
                HazardStatus.UNHANDLED, LocalDateTime.of(2026, 8, 25, 12, 0)).getId();

        mockMvc.perform(post(PATH + "/" + eventId + "/actions")
                        .contentType(MediaType.APPLICATION_JSON).content(action("EXPLODE")))
                .andExpect(status().isBadRequest());
    }
}
