package com.moode.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WorkerUpdateRequest {

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @NotBlank(message = "소속 구역은 필수입니다.")
    private String zoneName;

    /** 화면에는 있지만 Worker 에 대응 컬럼이 없어 현재는 받기만 하고 저장하지 않는다. */
    private String detailLocation;
}
