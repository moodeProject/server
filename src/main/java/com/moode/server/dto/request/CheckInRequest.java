package com.moode.server.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 출근 요청. 인증이 아직 없어 workerId를 본문으로 받는다
 * (HazardActionRequest.adminId 와 같은 이유 — 인증 도입 후 principal 로 교체 예정).
 */
@Getter
@NoArgsConstructor
public class CheckInRequest {

    @NotNull(message = "workerId 는 필수입니다.")
    private Long workerId;
}
