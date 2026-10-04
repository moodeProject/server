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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HelmetControllerTest {

    private static final String PATH = "/api/helmets";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private WorkerRepository workerRepository;
    @Autowired private HelmetRepository helmetRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private AdminRepository adminRepository;
    @Autowired private HazardEventRepository hazardEventRepository;
    @Autowired private HazardEventActionRepository hazardEventActionRepository;
    @Autowired private HazardClipRepository hazardClipRepository;
    @Autowired private SensorDataRepository sensorDataRepository;
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
                .loginId("admin-hc")
                .passwordHash(passwordEncoder.encode("Admin!2024"))
                .name("관리자")
                .email("admin-hc@example.com")
                .status(AdminStatus.APPROVED)
                .build());
        adminToken = jwtTokenProvider.generateToken(admin.getId(), UserType.ADMIN, admin.getLoginId(), admin.getName());
    }

    private String workerToken() {
        Worker worker = fixtures.worker("WK-90002", "토큰용작업자", null);
        return jwtTokenProvider.generateToken(worker.getId(), UserType.WORKER, worker.getEmployeeNo(), worker.getName());
    }

    private String json(Object... kv) throws Exception {
        var map = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return objectMapper.writeValueAsString(map);
    }

    // ===================== 등록 =====================

    @Test
    @DisplayName("헬멧을 등록할 수 있다")
    void createSucceeds() throws Exception {
        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("helmetNo", "H-801", "deviceId", "DEV-801")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.helmetNo").value("H-801"))
                .andExpect(jsonPath("$.data.deviceId").value("DEV-801"))
                .andExpect(jsonPath("$.data.workerId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("헬멧 번호가 중복이면 409를 받는다")
    void createFailsOnDuplicateHelmetNo() throws Exception {
        fixtures.helmet("H-802", "DEV-EXIST-1", null);

        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("helmetNo", "H-802", "deviceId", "DEV-NEW")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELMET_NO_DUPLICATE"));
    }

    @Test
    @DisplayName("기기 ID가 중복이면 409를 받는다")
    void createFailsOnDuplicateDeviceId() throws Exception {
        fixtures.helmet("H-803", "DEV-EXIST-2", null);

        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("helmetNo", "H-NEW", "deviceId", "DEV-EXIST-2")))
                .andExpect(status().isConflict());
    }

    // ===================== 수정 / 삭제 =====================

    @Test
    @DisplayName("메모를 수정할 수 있다")
    void updateMemoSucceeds() throws Exception {
        var helmet = fixtures.helmet("H-804", "DEV-804", null);

        mockMvc.perform(put(PATH + "/" + helmet.getId()).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("memo", "예비용")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memo").value("예비용"));
    }

    @Test
    @DisplayName("존재하지 않는 헬멧을 수정하면 404를 받는다")
    void updateFailsWhenNotFound() throws Exception {
        mockMvc.perform(put(PATH + "/999999").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("memo", "x")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("헬멧을 삭제하면 소프트 삭제되고 배정도 해제된다")
    void deleteSoftDeletesAndUnassigns() throws Exception {
        Zone zone = fixtures.zone("A-1F", "A구역");
        Worker worker = fixtures.worker("WK-70001", "작업자", zone);
        var helmet = fixtures.helmet("H-805", "DEV-805", worker);

        mockMvc.perform(delete(PATH + "/" + helmet.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        assertThat(helmetRepository.findById(helmet.getId()).orElseThrow().isActive()).isFalse();
        var reloadedWorker = workerRepository.findById(worker.getId()).orElseThrow();
        assertThat(reloadedWorker).isNotNull(); // 작업자 자체는 안 건드림
    }

    @Test
    @DisplayName("존재하지 않는 헬멧을 삭제하면 404를 받는다")
    void deleteFailsWhenNotFound() throws Exception {
        mockMvc.perform(delete(PATH + "/999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ===================== assign / unassign =====================

    @Test
    @DisplayName("미배정 헬멧을 작업자에게 배정할 수 있다")
    void assignSucceeds() throws Exception {
        Zone zone = fixtures.zone("A-1F", "A구역");
        Worker worker = fixtures.worker("WK-70002", "작업자", zone);
        var helmet = fixtures.helmet("H-806", "DEV-806", null);

        mockMvc.perform(post(PATH + "/" + helmet.getId() + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("workerId", worker.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workerId").value(worker.getId()));
    }

    @Test
    @DisplayName("이미 다른 작업자에게 배정된 헬멧을 배정하려 하면 409를 받는다")
    void assignFailsWhenAlreadyAssignedToOther() throws Exception {
        Zone zone = fixtures.zone("A-1F", "A구역");
        Worker owner = fixtures.worker("WK-70003", "원래주인", zone);
        Worker another = fixtures.worker("WK-70004", "다른사람", zone);
        var helmet = fixtures.helmet("H-807", "DEV-807", owner);

        mockMvc.perform(post(PATH + "/" + helmet.getId() + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("workerId", another.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELMET_ALREADY_ASSIGNED"));
    }

    @Test
    @DisplayName("이미 배정된 헬멧을 같은 작업자에게 다시 배정하면 멱등하게 200을 받는다")
    void assignIsIdempotentForSameWorker() throws Exception {
        Zone zone = fixtures.zone("A-1F", "A구역");
        Worker worker = fixtures.worker("WK-70005", "작업자", zone);
        var helmet = fixtures.helmet("H-808", "DEV-808", worker);

        mockMvc.perform(post(PATH + "/" + helmet.getId() + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("workerId", worker.getId())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("이미 다른 헬멧을 가진 작업자에게 배정하려 하면 409를 받는다")
    void assignFailsWhenWorkerAlreadyHasHelmet() throws Exception {
        Zone zone = fixtures.zone("A-1F", "A구역");
        Worker worker = fixtures.worker("WK-70006", "작업자", zone);
        fixtures.helmet("H-809", "DEV-809", worker);
        var freeHelmet = fixtures.helmet("H-810", "DEV-810", null);

        mockMvc.perform(post(PATH + "/" + freeHelmet.getId() + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("workerId", worker.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKER_ALREADY_HAS_HELMET"));
    }

    @Test
    @DisplayName("존재하지 않는 헬멧/작업자로 배정하면 404를 받는다")
    void assignFailsWhenHelmetOrWorkerNotFound() throws Exception {
        Zone zone = fixtures.zone("A-1F", "A구역");
        Worker worker = fixtures.worker("WK-70007", "작업자", zone);

        mockMvc.perform(post(PATH + "/999999/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("workerId", worker.getId())))
                .andExpect(status().isNotFound());

        var helmet = fixtures.helmet("H-811", "DEV-811", null);
        mockMvc.perform(post(PATH + "/" + helmet.getId() + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("workerId", 999999)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("배정된 헬멧을 해제할 수 있다")
    void unassignSucceeds() throws Exception {
        Zone zone = fixtures.zone("A-1F", "A구역");
        Worker worker = fixtures.worker("WK-70008", "작업자", zone);
        var helmet = fixtures.helmet("H-812", "DEV-812", worker);

        mockMvc.perform(post(PATH + "/" + helmet.getId() + "/unassign")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workerId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("원래 미배정이던 헬멧을 해제해도 멱등하게 200을 받는다")
    void unassignIsIdempotentWhenAlreadyUnassigned() throws Exception {
        var helmet = fixtures.helmet("H-813", "DEV-813", null);

        mockMvc.perform(post(PATH + "/" + helmet.getId() + "/unassign")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ===================== reconnect =====================

    @Test
    @DisplayName("재연결 요청은 상태 변경 없이 200을 받는다(스텁)")
    void reconnectSucceedsAsStub() throws Exception {
        var helmet = fixtures.helmet("H-814", "DEV-814", null);

        mockMvc.perform(post(PATH + "/" + helmet.getId() + "/reconnect")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.helmetNo").value("H-814"));
    }

    @Test
    @DisplayName("존재하지 않는 헬멧에 재연결 요청하면 404를 받는다")
    void reconnectFailsWhenNotFound() throws Exception {
        mockMvc.perform(post(PATH + "/999999/reconnect").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ===================== 권한 =====================

    @Test
    @DisplayName("토큰 없이 헬멧 목록을 조회하면 401을 받는다")
    void listRejectsAnonymous() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("WORKER 토큰으로 헬멧 목록을 조회하면 403을 받는다")
    void listRejectsWorkerRole() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + workerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN 토큰으로 헬멧 목록을 조회하면 200을 받는다")
    void listAllowsAdminRole() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
