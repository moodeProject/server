package com.moode.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moode.server.domain.*;
import com.moode.server.repository.*;
import com.moode.server.security.JwtTokenProvider;
import com.moode.server.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkerManagementControllerTest {

    private static final String PATH = "/api/workers";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private WorkerRepository workerRepository;
    @Autowired private HelmetRepository helmetRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private AdminRepository adminRepository;
    @Autowired private SensorDataRepository sensorDataRepository;
    @Autowired private HazardEventRepository hazardEventRepository;
    @Autowired private HazardEventActionRepository hazardEventActionRepository;
    @Autowired private HazardClipRepository hazardClipRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TestFixtures fixtures;

    private String adminToken;

    @BeforeEach
    void setUp() {
        // 공유 H2 인메모리 DB에서 다른 테스트 클래스가 남긴 행과 FK 가 얽히지 않도록
        // 자식 -> 부모 순서로 지운다 (HazardEventControllerTest 와 동일한 순서).
        hazardEventActionRepository.deleteAll();
        hazardClipRepository.deleteAll();
        hazardEventRepository.deleteAll();
        sensorDataRepository.deleteAll();
        helmetRepository.deleteAll();
        workerRepository.deleteAll();
        zoneRepository.deleteAll();
        adminRepository.deleteAll();

        Admin admin = adminRepository.save(Admin.builder()
                .loginId("admin-wm")
                .passwordHash(passwordEncoder.encode("Admin!2024"))
                .name("관리자")
                .email("admin-wm@example.com")
                .status(AdminStatus.APPROVED)
                .build());
        adminToken = jwtTokenProvider.generateToken(admin.getId(), UserType.ADMIN, admin.getLoginId(), admin.getName());
    }

    private String workerToken() {
        Worker worker = fixtures.worker("WK-90001", "토큰용작업자", null);
        return jwtTokenProvider.generateToken(worker.getId(), UserType.WORKER, worker.getEmployeeNo(), worker.getName());
    }

    private String createBody(String name, String zoneName, String helmetNo) throws Exception {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("name", name);
        map.put("zoneName", zoneName);
        if (helmetNo != null) {
            map.put("helmetNo", helmetNo);
        }
        return objectMapper.writeValueAsString(map);
    }

    // ===================== 등록 =====================

    @Test
    @DisplayName("작업자를 등록하면 사번이 WK- 형식으로 자동 채번되고 구역이 새로 만들어진다")
    void createAssignsEmployeeNoAndCreatesZone() throws Exception {
        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("홍길동", "A구역 3층", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.employeeNo").value(org.hamcrest.Matchers.matchesPattern("WK-\\d{5}")))
                .andExpect(jsonPath("$.data.zoneName").value("A구역 3층"));

        org.assertj.core.api.Assertions.assertThat(zoneRepository.findByZoneName("A구역 3층")).isPresent();
    }

    @Test
    @DisplayName("같은 소속 구역 이름으로 두 번째 작업자를 등록하면 구역을 재사용한다")
    void createReusesExistingZoneByName() throws Exception {
        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("첫째", "B구역 2층", null)))
                .andExpect(status().isOk());
        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("둘째", "B구역 2층", null)))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(zoneRepository.findAllByOrderByZoneCodeAsc())
                .filteredOn(z -> z.getZoneName().equals("B구역 2층"))
                .hasSize(1);
    }

    @Test
    @DisplayName("헬멧 번호를 함께 입력하면 새 헬멧이 생성되어 바로 배정된다")
    void createWithHelmetNoCreatesAndAssignsHelmet() throws Exception {
        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("박민수", "A구역", "H-101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.helmetNo").value("H-101"));

        var helmet = helmetRepository.findByHelmetNo("H-101").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(helmet.getWorker()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(helmet.getDeviceId()).isNotBlank();
    }

    @Test
    @DisplayName("이미 존재하는 헬멧 번호로 작업자를 등록하면 409를 받는다")
    void createFailsWhenHelmetNoAlreadyExists() throws Exception {
        fixtures.helmet("H-101", "DEV-EXIST", null);

        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("박민수", "A구역", "H-101")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELMET_NO_DUPLICATE"));
    }

    // ===================== 상세 / 존재하지 않음 =====================

    @Test
    @DisplayName("작업자 상세 조회는 외부요인/건강/추락 3블록을 반환한다")
    void getDetailReturnsThreeBlocks() throws Exception {
        Zone zone = fixtures.zone("A-3F", "A구역 3층");
        Worker worker = fixtures.worker("WK-20241103", "박민수", zone);
        var helmet = fixtures.helmet("H-001", "ESP32-001", worker);

        sensorDataRepository.save(SensorData.builder()
                .deviceId(helmet.getDeviceId())
                .heartRate(118.0)
                .fatigueAbnormal(true)
                .fallState(WorkerState.FALLEN)
                .healthState(WorkerState.RECOMMEND)
                .build());

        mockMvc.perform(get(PATH + "/" + worker.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DANGER"))
                .andExpect(jsonPath("$.data.helmetNo").value("H-001"))
                .andExpect(jsonPath("$.data.fall.status").value("DANGER"))
                .andExpect(jsonPath("$.data.fall.message").value("추락 감지됨"))
                .andExpect(jsonPath("$.data.health.status").value("CAUTION"))
                .andExpect(jsonPath("$.data.health.heartRate").value(118.0))
                .andExpect(jsonPath("$.data.external.status").value("NORMAL"))
                .andExpect(jsonPath("$.data.external.message").value("위험 요소 없음"));
    }

    @Test
    @DisplayName("센서 데이터가 없는 신규 작업자는 심박수/피로도가 null이고 상태는 NORMAL이다")
    void getDetailWithNoSensorDataReturnsNulls() throws Exception {
        Zone zone = fixtures.zone("C-1F", "C구역 1층");
        Worker worker = fixtures.worker("WK-30001", "신규작업자", zone);

        mockMvc.perform(get(PATH + "/" + worker.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NORMAL"))
                .andExpect(jsonPath("$.data.health.heartRate").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.helmetNo").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("존재하지 않는 작업자를 조회하면 404를 받는다")
    void getDetailFailsWhenNotFound() throws Exception {
        mockMvc.perform(get(PATH + "/999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ===================== 검색/필터 =====================

    @Test
    @DisplayName("이름/사번으로 검색할 수 있다")
    void listSearchesByNameOrEmployeeNo() throws Exception {
        Zone zone = fixtures.zone("A-1F", "A구역");
        fixtures.worker("WK-10001", "검색대상", zone);
        fixtures.worker("WK-10002", "다른사람", zone);

        mockMvc.perform(get(PATH).param("q", "검색").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("검색대상"));

        mockMvc.perform(get(PATH).param("q", "WK-10002").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].employeeNo").value("WK-10002"));
    }

    @Test
    @DisplayName("상태(DANGER)로 필터링하면 해당 작업자만 나온다")
    void listFiltersByStatus() throws Exception {
        Zone zone = fixtures.zone("A-1F", "A구역");
        Worker normalWorker = fixtures.worker("WK-40001", "정상작업자", zone);
        Worker dangerWorker = fixtures.worker("WK-40002", "위험작업자", zone);
        var helmet = fixtures.helmet("H-401", "DEV-401", dangerWorker);
        sensorDataRepository.save(SensorData.builder()
                .deviceId(helmet.getDeviceId())
                .fallState(WorkerState.FALLEN)
                .healthState(WorkerState.NORMAL)
                .build());

        mockMvc.perform(get(PATH).param("status", "DANGER").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("위험작업자"))
                .andExpect(jsonPath("$.data.dangerCount").value(1));
    }

    // ===================== 수정 / 삭제 =====================

    @Test
    @DisplayName("작업자 정보를 수정하면 이름과 구역이 바뀐다")
    void updateChangesNameAndZone() throws Exception {
        Zone zone = fixtures.zone("A-1F", "A구역");
        Worker worker = fixtures.worker("WK-50001", "이전이름", zone);

        var body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("name", "바뀐이름");
            put("zoneName", "D구역 4층");
        }});

        mockMvc.perform(put(PATH + "/" + worker.getId()).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("바뀐이름"))
                .andExpect(jsonPath("$.data.zoneName").value("D구역 4층"));
    }

    @Test
    @DisplayName("존재하지 않는 작업자를 수정하면 404를 받는다")
    void updateFailsWhenNotFound() throws Exception {
        var body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("name", "x");
            put("zoneName", "y");
        }});
        mockMvc.perform(put(PATH + "/999999").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("작업자를 삭제하면 소프트 삭제되어 이후 조회에 안 잡히고, 배정된 헬멧은 해제된다")
    void deleteSoftDeletesWorkerAndUnassignsHelmet() throws Exception {
        Zone zone = fixtures.zone("A-1F", "A구역");
        Worker worker = fixtures.worker("WK-60001", "삭제대상", zone);
        var helmet = fixtures.helmet("H-601", "DEV-601", worker);

        mockMvc.perform(delete(PATH + "/" + worker.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(PATH + "/" + worker.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        var reloaded = helmetRepository.findById(helmet.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getWorker()).isNull();
    }

    // ===================== 권한 =====================

    @Test
    @DisplayName("토큰 없이 작업자 목록을 조회하면 401을 받는다")
    void listRejectsAnonymous() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("WORKER 토큰으로 작업자 목록을 조회하면 403을 받는다")
    void listRejectsWorkerRole() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + workerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN 토큰으로 작업자 목록을 조회하면 200을 받는다")
    void listAllowsAdminRole() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
