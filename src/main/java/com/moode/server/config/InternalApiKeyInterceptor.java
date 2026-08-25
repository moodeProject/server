package com.moode.server.config;

import com.moode.server.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * /internal/** 경로를 공유 시크릿으로 보호한다. AI 서버(FastAPI)만 호출하는 내부 경로다.
 *
 * <p>키가 설정되지 않은 상태면 통과시키지 않고 막는다(fail closed).
 * 설정 누락이 곧 "인증 없는 공개 엔드포인트"가 되면 안 되기 때문.
 */
@Slf4j
@Component
public class InternalApiKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER_NAME = "X-Internal-Api-Key";

    private final String configuredKey;

    public InternalApiKeyInterceptor(@Value("${internal.api.key:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (configuredKey == null || configuredKey.isBlank()) {
            log.error("internal.api.key 가 설정되지 않아 내부 API 호출을 모두 거부합니다.");
            throw new UnauthorizedException("내부 API 키가 설정되지 않았습니다.");
        }

        String provided = request.getHeader(HEADER_NAME);
        if (provided == null || !matches(provided)) {
            log.warn("내부 API 키 불일치: uri={}", request.getRequestURI());
            throw new UnauthorizedException("유효하지 않은 내부 API 키입니다.");
        }
        return true;
    }

    // 타이밍 공격을 피하려고 길이 비교로 빠져나가지 않는 상수 시간 비교를 쓴다.
    private boolean matches(String provided) {
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                configuredKey.getBytes(StandardCharsets.UTF_8));
    }
}
