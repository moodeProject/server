package com.moode.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moode.server.domain.*;
import com.moode.server.repository.AdminRepository;
import com.moode.server.repository.HazardClipRepository;
import com.moode.server.repository.HazardEventActionRepository;
import com.moode.server.repository.HazardEventRepository;
import com.moode.server.repository.HelmetRepository;
import com.moode.server.repository.WorkerRepository;
import com.moode.server.security.JwtTokenProvider;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * auth.admin.auto-approve-signup 은 application-test.yml 에서 false — 가입 테스트가
 * "승인 전까지는 로그인 불가" 라는 기본 동작을 그대로 검증하도록 둔다. 승인된 관리자가
 * 필요한 테스트는 AdminRepository 로 status=APPROVED 를 직접 심는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    private static final String PATH = "/api/auth";
    private static final String RAW_WORKER_PASSWORD = "Worker!2024";
    private static final String RAW_ADMIN_PASSWORD = "Admin!2024";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private WorkerRepository workerRepository;
    @Autowired private HelmetRepository helmetRepository;
    @Autowired private HazardEventActionRepository hazardEventActionRepository;
    @Autowired private HazardClipRepository hazardClipRepository;
    @Autowired private HazardEventRepository hazardEventRepository;
    @Autowired private AdminRepository adminRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void clean() {
        // H2 는 테스트 클래스 간에 공유되는 인메모리 DB라, worker/helmet 을 지우기 전에
        // 그걸 참조하는 hazard_event(와 그걸 참조하는 action/clip)부터 지운다 —
        // HazardEventControllerTest 와 동일한 순서(자식 -> 부모).
        hazardEventActionRepository.deleteAll();
        hazardClipRepository.deleteAll();
        hazardEventRepository.deleteAll();
        helmetRepository.deleteAll();
        workerRepository.deleteAll();
        adminRepository.deleteAll();
    }

    private Worker saveWorker(String employeeNo, String rawPassword) {
        return workerRepository.save(Worker.builder()
                .employeeNo(employeeNo)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .name("김현석")
                .department("현장관리팀")
                .status(WorkerStatus.WORKING)
                .build());
    }

    private Admin saveAdmin(String loginId, String rawPassword, AdminStatus status) {
        return adminRepository.save(Admin.builder()
                .loginId(loginId)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .name("관리자")
                .email(loginId + "@example.com")
                .status(status)
                .build());
    }

    private String loginRequestJson(String userType, String loginId, String password) throws Exception {
        return objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("userType", userType);
            put("loginId", loginId);
            put("password", password);
        }});
    }

    // ===================== 로그인 성공 =====================

    @Test
    @DisplayName("작업자는 사번+비밀번호로 로그인하면 WORKER 토큰을 받는다")
    void workerLoginSucceeds() throws Exception {
        saveWorker("WK-20241103", RAW_WORKER_PASSWORD);

        mockMvc.perform(post(PATH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson("WORKER", "WK-20241103", RAW_WORKER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.userType").value("WORKER"))
                .andExpect(jsonPath("$.data.loginId").value("WK-20241103"))
                .andExpect(jsonPath("$.data.name").value("김현석"));
    }

    @Test
    @DisplayName("승인된 관리자는 아이디+비밀번호로 로그인하면 ADMIN 토큰을 받는다")
    void adminLoginSucceeds() throws Exception {
        saveAdmin("admin01", RAW_ADMIN_PASSWORD, AdminStatus.APPROVED);

        mockMvc.perform(post(PATH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson("ADMIN", "admin01", RAW_ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.userType").value("ADMIN"));
    }

    // ===================== 로그인 실패 =====================

    @Test
    @DisplayName("비밀번호가 틀리면 401과 INVALID_CREDENTIALS 코드를 받는다")
    void loginFailsWithWrongPassword() throws Exception {
        saveWorker("WK-20241103", RAW_WORKER_PASSWORD);

        mockMvc.perform(post(PATH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson("WORKER", "WK-20241103", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("존재하지 않는 사번도 비밀번호 틀렸을 때와 같은 코드로 거절한다(계정 존재 여부 비유출)")
    void loginFailsWithUnknownLoginId() throws Exception {
        mockMvc.perform(post(PATH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson("WORKER", "WK-NOPE", RAW_WORKER_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("미승인(PENDING) 관리자는 비밀번호가 맞아도 ACCOUNT_PENDING_APPROVAL 로 거절된다")
    void adminLoginFailsWhenPending() throws Exception {
        saveAdmin("admin02", RAW_ADMIN_PASSWORD, AdminStatus.PENDING);

        mockMvc.perform(post(PATH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson("ADMIN", "admin02", RAW_ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("반려(REJECTED) 관리자는 ACCOUNT_REJECTED 로 거절된다")
    void adminLoginFailsWhenRejected() throws Exception {
        saveAdmin("admin03", RAW_ADMIN_PASSWORD, AdminStatus.REJECTED);

        mockMvc.perform(post(PATH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson("ADMIN", "admin03", RAW_ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_REJECTED"));
    }

    // ===================== 회원가입 =====================

    @Test
    @DisplayName("회원가입은 auto-approve-signup=false(test) 에서 PENDING 으로 저장된다")
    void signupCreatesPendingAdmin() throws Exception {
        String body = """
                {
                  "loginId": "newadmin1",
                  "password": "Passw0rd!",
                  "passwordConfirm": "Passw0rd!",
                  "name": "홍길동",
                  "email": "newadmin1@example.com"
                }
                """;

        mockMvc.perform(post(PATH + "/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // 승인 전이라 바로 로그인은 안 된다
        mockMvc.perform(post(PATH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson("ADMIN", "newadmin1", "Passw0rd!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("중복 아이디로 가입하면 409 Conflict 를 받는다")
    void signupFailsOnDuplicateLoginId() throws Exception {
        saveAdmin("dupuser", RAW_ADMIN_PASSWORD, AdminStatus.APPROVED);

        String body = """
                {
                  "loginId": "dupuser",
                  "password": "Passw0rd!",
                  "passwordConfirm": "Passw0rd!",
                  "name": "홍길동",
                  "email": "another@example.com"
                }
                """;

        mockMvc.perform(post(PATH + "/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("비밀번호 정책(특수문자 미포함)을 어기면 400을 받는다")
    void signupFailsOnWeakPassword() throws Exception {
        String body = """
                {
                  "loginId": "weakpwuser",
                  "password": "password1",
                  "passwordConfirm": "password1",
                  "name": "홍길동",
                  "email": "weakpw@example.com"
                }
                """;

        mockMvc.perform(post(PATH + "/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호와 비밀번호 확인이 다르면 400을 받는다")
    void signupFailsOnPasswordMismatch() throws Exception {
        String body = """
                {
                  "loginId": "mismatchuser",
                  "password": "Passw0rd!",
                  "passwordConfirm": "Different1!",
                  "name": "홍길동",
                  "email": "mismatch@example.com"
                }
                """;

        mockMvc.perform(post(PATH + "/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ===================== /me =====================

    @Test
    @DisplayName("토큰 없이 /me 를 호출하면 401(UNAUTHENTICATED)을 받는다")
    void meFailsWithoutToken() throws Exception {
        mockMvc.perform(get(PATH + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("유효한 WORKER 토큰으로 /me 를 호출하면 본인 정보를 받는다")
    void meSucceedsWithWorkerToken() throws Exception {
        Worker worker = saveWorker("WK-20241103", RAW_WORKER_PASSWORD);
        String token = jwtTokenProvider.generateToken(worker.getId(), UserType.WORKER, worker.getEmployeeNo(), worker.getName());

        mockMvc.perform(get(PATH + "/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userType").value("WORKER"))
                .andExpect(jsonPath("$.data.loginId").value("WK-20241103"));
    }

    // ===================== 권한 없는 접근 (/api/workers/**) =====================

    @Test
    @DisplayName("토큰 없이 /api/workers/** 에 접근하면 401을 받는다")
    void adminOnlyEndpointRejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/workers/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("WORKER 토큰으로 /api/workers/** 에 접근하면 403을 받는다")
    void adminOnlyEndpointRejectsWorkerRole() throws Exception {
        Worker worker = saveWorker("WK-20241103", RAW_WORKER_PASSWORD);
        String token = jwtTokenProvider.generateToken(worker.getId(), UserType.WORKER, worker.getEmployeeNo(), worker.getName());

        mockMvc.perform(get("/api/workers/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("ADMIN 토큰이면 /api/workers/** 에 접근할 수 있다")
    void adminOnlyEndpointAllowsAdminRole() throws Exception {
        Admin admin = saveAdmin("admin04", RAW_ADMIN_PASSWORD, AdminStatus.APPROVED);
        String token = jwtTokenProvider.generateToken(admin.getId(), UserType.ADMIN, admin.getLoginId(), admin.getName());

        mockMvc.perform(get("/api/workers/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
