package com.moode.server.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** SOS 요청 */
@Getter
@NoArgsConstructor
public class SosCreateRequest {

    // TODO: 로그인(인증) 연동되면 body 대신 로그인한 작업자로 바꾼다.
    @Schema(description = "SOS 를 누른 작업자 ID. 로그인 연동 전까지 body 로 받는다", example = "1")
    @NotNull
    private Long requesterId;
}
