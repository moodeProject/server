package com.moode.server.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HelmetAssignRequest {

    @NotNull(message = "workerId는 필수입니다.")
    private Long workerId;
}
