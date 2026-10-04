package com.moode.server.security;

import com.moode.server.domain.UserType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer <token> 을 파싱해 SecurityContext 를 채운다.
 *
 * <p>토큰이 없거나 유효하지 않아도 여기서 막지 않는다 — 대부분의 API 가 permitAll 이므로
 * 인증이 꼭 필요한 요청만 SecurityConfig 의 authorizeHttpRequests 에서 걸러지고,
 * 그때 비로소 RestAuthenticationEntryPoint 가 401 을 내려준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HEADER_NAME);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            try {
                Claims claims = jwtTokenProvider.parseClaims(token);
                Long userId = Long.valueOf(claims.getSubject());
                UserType userType = UserType.valueOf(claims.get("userType", String.class));
                String loginId = claims.get("loginId", String.class);
                String name = claims.get("name", String.class);

                AuthenticatedUser principal = new AuthenticatedUser(userId, userType, loginId, name);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + userType.name()));
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("유효하지 않은 JWT: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
