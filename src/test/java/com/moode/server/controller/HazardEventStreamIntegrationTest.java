package com.moode.server.controller;

import com.moode.server.config.InternalApiKeyInterceptor;
import com.moode.server.repository.HazardClipRepository;
import com.moode.server.repository.HazardEventRepository;
import com.moode.server.repository.InboundMessageRepository;
import com.moode.server.repository.HelmetRepository;
import com.moode.server.repository.WorkerRepository;
import com.moode.server.repository.ZoneRepository;
import com.moode.server.service.impl.SseHazardEventStreamService;
import com.moode.server.domain.Helmet;
import com.moode.server.service.impl.VisionWarningRegistry;
import com.moode.server.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSE 종단 검증. MockMvc 는 스트리밍 응답을 실제로 흘려주지 않으므로
 * 실제 Tomcat + 실제 HTTP 연결로 확인한다.
 * (curl -N 으로 하는 수동 확인과 같은 경로를 자동화한 것)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HazardEventStreamIntegrationTest {

    private static final String EVENT_UID = "dadeb552483643e4b32052f12ffbef3e";

    @LocalServerPort
    private int port;

    @Autowired
    private HazardEventRepository hazardEventRepository;
    @Autowired
    private HazardClipRepository hazardClipRepository;
    @Autowired
    private InboundMessageRepository inboundMessageRepository;

    @Autowired
    private SseHazardEventStreamService streamService;
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

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Thread> readers = new ArrayList<>();
    private final List<InputStream> openBodies = new ArrayList<>();

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

    @AfterEach
    void stopReaders() {
        closeStreams();
        readers.forEach(Thread::interrupt);
    }

    private void closeStreams() {
        for (InputStream body : openBodies) {
            try {
                body.close();
            } catch (Exception ignored) {
                // 이미 닫힘
            }
        }
        openBodies.clear();
    }

    /**
     * SSE 스트림을 백그라운드에서 읽어 큐에 쌓는다. curl -N 에 해당한다.
     */
    private BlockingQueue<String> openStream() throws Exception {
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/hazard-events/stream"))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        HttpResponse<InputStream> response =
                client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);

        openBodies.add(response.body());

        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    lines.add(line);
                }
            } catch (Exception ignored) {
                // 테스트 종료로 인한 끊김
            }
        });
        reader.setDaemon(true);
        reader.start();
        readers.add(reader);

        // 연결 확립 확인(connected 이벤트가 올 때까지 대기)
        awaitLine(lines, "connected", Duration.ofSeconds(5));
        return lines;
    }

    /**
     * 큐에서 기대하는 문자열이 포함된 줄이 나올 때까지 대기한다.
     */
    private String awaitLine(BlockingQueue<String> lines, String expected, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            String line = lines.poll(200, TimeUnit.MILLISECONDS);
            if (line != null && line.contains(expected)) {
                return line;
            }
        }
        throw new AssertionError("SSE 에서 '" + expected + "' 를 받지 못했습니다.");
    }

    private int postInternal(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/internal/v1/hazard-events"))
                .header("Content-Type", "application/json")
                .header(InternalApiKeyInterceptor.HEADER_NAME, "test-internal-key")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private String hazardConfirmed(String idempotencyKey) {
        return """
                {
                  "idempotencyKey": "%s",
                  "eventId": "%s",
                  "eventType": "HAZARD_CONFIRMED",
                  "helmetId": "H-001",
                  "hazardTypes": ["forklift"],
                  "detections": [
                    { "type": "forklift", "confidence": 0.8483, "bbox": [214.9, 38.6, 806.2, 440.3] }
                  ],
                  "frameWidth": 960,
                  "frameHeight": 540,
                  "occurredAt": "2026-08-25T08:52:35.831808+00:00",
                  "streamOffsetSeconds": 2.04
                }
                """.formatted(idempotencyKey, EVENT_UID);
    }

    @Test
    @DisplayName("HAZARD_CONFIRMED 수신 즉시 구독자에게 event: hazard 가 흘러나온다")
    void hazardConfirmedIsPushedToSubscribers() throws Exception {
        BlockingQueue<String> lines = openStream();

        assertThat(postInternal(hazardConfirmed("0bae5301b4f64746a2b9bb1db5de1e49"))).isEqualTo(201);

        assertThat(awaitLine(lines, "event:hazard", Duration.ofSeconds(5)))
                .as("event 이름이 hazard 여야 한다")
                .contains("hazard");

        // data 는 목록 API 항목과 같은 구조여야 한다.
        // 특히 worker/zone/helmetNo 는 트랜잭션 밖에서 직렬화되므로,
        // 지연 로딩이 초기화되지 않았다면 여기서 깨진다.
        String data = awaitLine(lines, "data:", Duration.ofSeconds(5));
        assertThat(data).contains("\"hazardType\":\"forklift\"");
        assertThat(data).contains("\"hazardLabel\":\"지게차 접근\"");
        assertThat(data).contains("\"severity\":\"DANGER\"");
        assertThat(data).contains("\"severityLabel\":\"위험\"");
        assertThat(data).contains("\"helmetNo\":\"H-001\"");
        assertThat(data).contains("\"name\":\"박민수\"");
        assertThat(data).contains("\"code\":\"A-3F\"");
        // 이 시점에는 클립이 아직 없다
        assertThat(data).contains("\"hasClip\":false");
        assertThat(data).contains("\"eventId\":");
    }

    @Test
    @DisplayName("CLIP_READY 수신 시 event: clip-ready 로 eventId 만 흘러나온다")
    void clipReadyIsPushedToSubscribers() throws Exception {
        assertThat(postInternal(hazardConfirmed("0bae5301b4f64746a2b9bb1db5de1e49"))).isEqualTo(201);

        // 구독은 클립 도착 직전에 연다(앞선 hazard 이벤트를 받지 않도록)
        BlockingQueue<String> lines = openStream();

        String clipReady = """
                {
                  "idempotencyKey": "a71f0c33e2b84e10bbd2f9a1c0e33d55",
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
                """.formatted(EVENT_UID);

        assertThat(postInternal(clipReady)).isEqualTo(201);

        awaitLine(lines, "event:clip-ready", Duration.ofSeconds(5));
        Long savedId = hazardEventRepository.findAll().get(0).getId();
        assertThat(awaitLine(lines, "data:", Duration.ofSeconds(5)))
                .isEqualTo("data:{\"eventId\":" + savedId + "}");
    }

    /**
     * 끊긴 emitter 는 다음 전송 시도에서 감지되어 제거된다.
     * 하트비트를 수동으로 돌려 그 감지를 앞당긴 뒤 구독자 수가 0 이 될 때까지 기다린다.
     */
    private void evictDeadSubscribers() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (streamService.subscriberCount() > 0 && System.nanoTime() < deadline) {
            streamService.heartbeat();
            TimeUnit.MILLISECONDS.sleep(200);
        }
    }

    @Test
    @DisplayName("끊긴 연결은 컬렉션에서 제거되어 쌓이지 않는다")
    void disconnectedEmittersAreRemoved() throws Exception {
        // 앞선 테스트에서 남은 죽은 연결을 먼저 비워 기준선을 0 으로 만든다
        evictDeadSubscribers();
        assertThat(streamService.subscriberCount()).isZero();

        openStream();
        openStream();
        assertThat(streamService.subscriberCount()).isEqualTo(2);

        // 클라이언트가 끊는다
        closeStreams();
        readers.forEach(Thread::interrupt);

        evictDeadSubscribers();

        // 제거하지 않으면 emitter 가 계속 쌓여 메모리가 샌다.
        assertThat(streamService.subscriberCount())
                .as("끊긴 emitter 가 모두 제거되어야 한다")
                .isZero();
    }

    @Test
    @DisplayName("구독자가 없거나 중간에 끊겨도 이벤트 수신은 201 로 성공한다")
    void ingestSucceedsWithoutSubscribers() throws Exception {
        // 구독자가 하나도 없는 상태
        assertThat(postInternal(hazardConfirmed("11111111111111111111111111111111"))).isEqualTo(201);

        // 구독했다가 바로 끊은 상태
        BlockingQueue<String> lines = openStream();
        readers.forEach(Thread::interrupt);

        // 끊긴 구독자가 있어도 다음 수신은 정상 처리되어야 한다
        assertThat(postInternal(hazardConfirmed("22222222222222222222222222222222"))).isEqualTo(201);

        assertThat(hazardEventRepository.findAll()).hasSize(2);
        assertThat(lines).isNotNull();
    }
}
