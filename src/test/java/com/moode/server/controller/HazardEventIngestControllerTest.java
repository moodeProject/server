package com.moode.server.controller;

import com.moode.server.config.InternalApiKeyInterceptor;
import com.moode.server.domain.HazardEvent;
import com.moode.server.domain.Helmet;
import com.moode.server.domain.HazardSeverity;
import com.moode.server.domain.HazardType;
import com.moode.server.repository.HazardClipRepository;
import com.moode.server.repository.HazardEventRepository;
import com.moode.server.repository.HelmetRepository;
import com.moode.server.repository.InboundMessageRepository;
import com.moode.server.repository.WorkerRepository;
import com.moode.server.repository.ZoneRepository;
import com.moode.server.service.impl.VisionWarningRegistry;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HazardEventIngestControllerTest {

    private static final String PATH = "/internal/v1/hazard-events";
    private static final String VALID_KEY = "test-internal-key";
    private static final String EVENT_UID = "dadeb552483643e4b32052f12ffbef3e";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private HazardEventRepository hazardEventRepository;
    @Autowired
    private HazardClipRepository hazardClipRepository;
    @Autowired
    private InboundMessageRepository inboundMessageRepository;
    @Autowired
    private HelmetRepository helmetRepository;
    @Autowired
    private WorkerRepository workerRepository;
    @Autowired
    private ZoneRepository zoneRepository;
    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private VisionWarningRegistry visionWarningRegistry;

    @BeforeEach
    void clean() {
        hazardClipRepository.deleteAll();
        hazardEventRepository.deleteAll();
        inboundMessageRepository.deleteAll();
        helmetRepository.deleteAll();
        workerRepository.deleteAll();
        zoneRepository.deleteAll();
        Helmet helmet = fixtures.defaultHelmet();
        // 영상 단독 감지는 헬멧 경고로만 끝나고 이벤트가 생기지 않는다. 이 클래스는 이벤트 저장·클립·SSE 경로를
        // 보므로 헬멧 센서 이상이 이미 있었던 상황(=위험으로 올라가는 경우)으로 둔다.
        // 영상 단독 경로는 VisionWarningEscalationTest 참고.
        visionWarningRegistry.clear();
        visionWarningRegistry.noteSensorAbnormal(helmet.getDeviceId());
    }

    private MockHttpServletRequestBuilder send(String body) {
        return post(PATH)
                .header(InternalApiKeyInterceptor.HEADER_NAME, VALID_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String hazardConfirmed(String idempotencyKey, String eventUid, String hazardType) {
        return """
                {
                  "idempotencyKey": "%s",
                  "eventId": "%s",
                  "eventType": "HAZARD_CONFIRMED",
                  "helmetId": "H-001",
                  "hazardTypes": ["%s"],
                  "detections": [
                    { "type": "%s", "confidence": 0.8483, "bbox": [214.9, 38.6, 806.2, 440.3] }
                  ],
                  "frameWidth": 960,
                  "frameHeight": 540,
                  "occurredAt": "2026-08-25T08:52:35.831808+00:00",
                  "streamOffsetSeconds": 2.04
                }
                """.formatted(idempotencyKey, eventUid, hazardType, hazardType);
    }

    private String clipReady(String idempotencyKey, String eventUid) {
        return """
                {
                  "idempotencyKey": "%s",
                  "eventId": "%s",
                  "eventType": "CLIP_READY",
                  "helmetId": "H-001",
                  "clip": {
                    "storage": "S3",
                    "bucket": "safe-helmet-clips",
                    "key": "clips/2026/08/25/H-001_083744.mp4",
                    "durationSeconds": 16.3,
                    "frameCount": 163
                  }
                }
                """.formatted(idempotencyKey, eventUid);
    }

    @Test
    @DisplayName("HAZARD_CONFIRMED 를 받으면 201 로 저장하고 severity 를 서버가 판정한다")
    void receiveHazardConfirmed() throws Exception {
        mockMvc.perform(send(hazardConfirmed("0bae5301b4f64746a2b9bb1db5de1e49", EVENT_UID, "forklift")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.eventId").isNumber());

        List<HazardEvent> events = hazardEventRepository.findAll();
        assertThat(events).hasSize(1);
        HazardEvent event = events.get(0);
        assertThat(event.getEventUid()).isEqualTo(EVENT_UID);
        assertThat(event.getHazardType()).isEqualTo(HazardType.FORKLIFT);
        // AI 서버는 등급을 보내지 않는다. 서버가 forklift -> DANGER 로 판정해야 한다.
        assertThat(event.getSeverity()).isEqualTo(HazardSeverity.DANGER);
        // 트랜잭션 밖이라 연관 엔티티는 id 로만 확인한다. 값 검증은 SSE 종단 테스트에서 한다.
        Helmet helmet = helmetRepository.findByHelmetNo("H-001").orElseThrow();
        assertThat(event.getHelmet().getId()).isEqualTo(helmet.getId());
        assertThat(event.getWorker().getId()).isEqualTo(helmet.getWorker().getId());
        assertThat(event.getZone().getId()).isEqualTo(helmet.getWorker().getCurrentZone().getId());
        assertThat(event.getDetectionsJson()).contains("forklift");
    }

    @Test
    @DisplayName("같은 idempotencyKey 로 두 번 보내면 두 번째는 200 DUPLICATE_IGNORED 이고 행은 1개다")
    void duplicateIdempotencyKeyIsIgnored() throws Exception {
        String body = hazardConfirmed("0bae5301b4f64746a2b9bb1db5de1e49", EVENT_UID, "forklift");

        mockMvc.perform(send(body)).andExpect(status().isCreated());

        mockMvc.perform(send(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE_IGNORED"))
                .andExpect(jsonPath("$.eventId").doesNotExist());

        assertThat(hazardEventRepository.findAll()).hasSize(1);
        assertThat(inboundMessageRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("같은 eventId 라도 idempotencyKey 가 다르면 중복이 아니라 둘 다 저장된다")
    void sameEventIdWithDifferentIdempotencyKeyIsNotDuplicate() throws Exception {
        // 한 사건에서 물웅덩이 -> 지게차가 연달아 감지된 상황
        mockMvc.perform(send(hazardConfirmed("11111111111111111111111111111111", EVENT_UID, "puddle")))
                .andExpect(status().isCreated());
        mockMvc.perform(send(hazardConfirmed("22222222222222222222222222222222", EVENT_UID, "forklift")))
                .andExpect(status().isCreated());

        List<HazardEvent> events = hazardEventRepository.findByEventUidOrderByOccurredAtAsc(EVENT_UID);
        assertThat(events).hasSize(2);
        assertThat(events).extracting(HazardEvent::getHazardType)
                .containsExactlyInAnyOrder(HazardType.PUDDLE, HazardType.FORKLIFT);
        // 영상+센서 이상이 겹쳐 올라온 이벤트는 종류와 상관없이 위험이다 (물웅덩이도 DANGER)
        assertThat(events).extracting(HazardEvent::getSeverity)
                .containsOnly(HazardSeverity.DANGER);
    }

    @Test
    @DisplayName("CLIP_READY 가 HAZARD_CONFIRMED 보다 먼저 오면 404 다")
    void clipReadyBeforeHazardConfirmedIsNotFound() throws Exception {
        mockMvc.perform(send(clipReady("a71f0c33e2b84e10bbd2f9a1c0e33d55", EVENT_UID)))
                .andExpect(status().isNotFound());

        assertThat(hazardClipRepository.findAll()).isEmpty();
        // 실패한 메시지는 처리된 것으로 기록되면 안 된다. 재전송 시 다시 처리될 수 있어야 한다.
        assertThat(inboundMessageRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("CLIP_READY 는 bucket 과 key 만 저장하고 사고 시점 마커를 옮긴다")
    void clipReadyAttachesClip() throws Exception {
        mockMvc.perform(send(hazardConfirmed("0bae5301b4f64746a2b9bb1db5de1e49", EVENT_UID, "forklift")))
                .andExpect(status().isCreated());

        mockMvc.perform(send(clipReady("a71f0c33e2b84e10bbd2f9a1c0e33d55", EVENT_UID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        Long eventId = hazardEventRepository.findAll().get(0).getId();
        assertThat(hazardClipRepository.findByHazardEventId(eventId))
                .hasValueSatisfying(clip -> {
                    assertThat(clip.getBucket()).isEqualTo("safe-helmet-clips");
                    assertThat(clip.getObjectKey()).isEqualTo("clips/2026/08/25/H-001_083744.mp4");
                    assertThat(clip.getFrameCount()).isEqualTo(163);
                });
    }

    @Test
    @DisplayName("API 키가 틀리면 401 이고 아무것도 저장되지 않는다")
    void wrongApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(InternalApiKeyInterceptor.HEADER_NAME, "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hazardConfirmed("0bae5301b4f64746a2b9bb1db5de1e49", EVENT_UID, "forklift")))
                .andExpect(status().isUnauthorized());

        assertThat(hazardEventRepository.findAll()).isEmpty();
        assertThat(inboundMessageRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("API 키 헤더가 아예 없어도 401 이다")
    void missingApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hazardConfirmed("0bae5301b4f64746a2b9bb1db5de1e49", EVENT_UID, "forklift")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("알 수 없는 hazardType 은 재시도해도 소용없으므로 400 이다")
    void unknownHazardTypeIsBadRequest() throws Exception {
        mockMvc.perform(send(hazardConfirmed("0bae5301b4f64746a2b9bb1db5de1e49", EVENT_UID, "unicorn")))
                .andExpect(status().isBadRequest());

        assertThat(hazardEventRepository.findAll()).isEmpty();
        assertThat(inboundMessageRepository.findAll()).isEmpty();
    }
}
