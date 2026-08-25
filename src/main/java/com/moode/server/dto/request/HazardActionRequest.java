package com.moode.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대시보드 조치 요청.
 *
 * <p>adminId 를 본문으로 받는 이유: 이 프로젝트에 인증(Spring Security)이 없어
 * 서버가 요청자를 알 수 없기 때문. 인증이 도입되면 본문에서 빼고 인증 주체에서 가져와야 한다.
 */
@Getter
@NoArgsConstructor
public class HazardActionRequest {

    @NotBlank(message = "actionType 은 필수입니다.")
    private String actionType;

    private Long adminId;

    private String memo;
}
