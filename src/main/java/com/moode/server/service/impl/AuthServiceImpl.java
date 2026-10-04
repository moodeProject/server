package com.moode.server.service.impl;

import com.moode.server.domain.Admin;
import com.moode.server.domain.AdminStatus;
import com.moode.server.domain.UserType;
import com.moode.server.domain.Worker;
import com.moode.server.dto.request.LoginRequest;
import com.moode.server.dto.request.SignupRequest;
import com.moode.server.dto.response.LoginResponse;
import com.moode.server.dto.response.MeResponse;
import com.moode.server.dto.response.SignupResponse;
import com.moode.server.exception.ConflictException;
import com.moode.server.exception.NotFoundException;
import com.moode.server.exception.UnauthorizedException;
import com.moode.server.repository.AdminRepository;
import com.moode.server.repository.WorkerRepository;
import com.moode.server.security.AuthErrorCode;
import com.moode.server.security.AuthenticatedUser;
import com.moode.server.security.JwtTokenProvider;
import com.moode.server.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final WorkerRepository workerRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${auth.admin.auto-approve-signup:false}")
    private boolean autoApproveSignup;

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        return request.userType() == UserType.WORKER
                ? loginWorker(request.loginId(), request.password())
                : loginAdmin(request.loginId(), request.password());
    }

    private LoginResponse loginWorker(String employeeNo, String rawPassword) {
        Worker worker = workerRepository.findByEmployeeNo(employeeNo)
                .orElseThrow(() -> invalidCredentials());

        if (!passwordEncoder.matches(rawPassword, worker.getPasswordHash())) {
            throw invalidCredentials();
        }

        String token = jwtTokenProvider.generateToken(worker.getId(), UserType.WORKER, worker.getEmployeeNo(), worker.getName());
        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInSeconds(jwtTokenProvider.getValiditySeconds())
                .userType(UserType.WORKER)
                .userId(worker.getId())
                .loginId(worker.getEmployeeNo())
                .name(worker.getName())
                .build();
    }

    private LoginResponse loginAdmin(String loginId, String rawPassword) {
        Admin admin = adminRepository.findByLoginId(loginId)
                .orElseThrow(() -> invalidCredentials());

        if (!passwordEncoder.matches(rawPassword, admin.getPasswordHash())) {
            throw invalidCredentials();
        }

        if (admin.getStatus() == AdminStatus.PENDING) {
            throw new UnauthorizedException(AuthErrorCode.ACCOUNT_PENDING_APPROVAL, "가입 후 관리자 승인이 완료되면 로그인이 가능합니다.");
        }
        if (admin.getStatus() == AdminStatus.REJECTED) {
            throw new UnauthorizedException(AuthErrorCode.ACCOUNT_REJECTED, "가입이 반려된 계정입니다.");
        }

        String token = jwtTokenProvider.generateToken(admin.getId(), UserType.ADMIN, admin.getLoginId(), admin.getName());
        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInSeconds(jwtTokenProvider.getValiditySeconds())
                .userType(UserType.ADMIN)
                .userId(admin.getId())
                .loginId(admin.getLoginId())
                .name(admin.getName())
                .build();
    }

    // 아이디/사번 불일치와 비밀번호 불일치를 같은 메시지로 묶는다 — 둘을 구분해 응답하면
    // 공격자가 유효한 아이디를 추려낼 수 있다(계정 존재 여부 유출).
    private UnauthorizedException invalidCredentials() {
        return new UnauthorizedException(AuthErrorCode.INVALID_CREDENTIALS, "아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    @Override
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new IllegalArgumentException("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }
        if (adminRepository.existsByLoginId(request.loginId())) {
            throw new ConflictException("이미 사용 중인 아이디입니다.");
        }
        if (adminRepository.existsByEmail(request.email())) {
            throw new ConflictException("이미 사용 중인 이메일입니다.");
        }

        Admin admin = Admin.builder()
                .loginId(request.loginId())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .email(request.email())
                .phone(request.phone())
                .department(request.department())
                .status(AdminStatus.PENDING)
                .build();

        if (autoApproveSignup) {
            admin.autoApprove();
            log.info("auth.admin.auto-approve-signup=true 로 관리자 가입 즉시 승인: loginId={}", admin.getLoginId());
        }

        Admin saved = adminRepository.save(admin);
        return SignupResponse.builder()
                .adminId(saved.getId())
                .loginId(saved.getLoginId())
                .status(saved.getStatus())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public MeResponse me(AuthenticatedUser user) {
        if (user.userType() == UserType.WORKER) {
            Worker worker = workerRepository.findById(user.userId())
                    .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
            return MeResponse.builder()
                    .userId(worker.getId())
                    .userType(UserType.WORKER)
                    .loginId(worker.getEmployeeNo())
                    .name(worker.getName())
                    .build();
        }

        Admin admin = adminRepository.findById(user.userId())
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        return MeResponse.builder()
                .userId(admin.getId())
                .userType(UserType.ADMIN)
                .loginId(admin.getLoginId())
                .name(admin.getName())
                .build();
    }
}
