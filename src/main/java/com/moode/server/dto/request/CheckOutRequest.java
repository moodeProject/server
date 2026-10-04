package com.moode.server.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CheckOutRequest {

    @NotNull(message = "workerId 는 필수입니다.")
    private Long workerId;
}
