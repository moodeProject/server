package com.moode.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 헬멧 등록(docs/ui/admin "헬멧 관리" 모달). helmetNo/deviceId 는 여기선 관리자가 직접 입력한다. */
@Getter
@NoArgsConstructor
public class HelmetCreateRequest {

    @NotBlank(message = "헬멧 번호는 필수입니다.")
    private String helmetNo;

    @NotBlank(message = "기기 ID는 필수입니다.")
    private String deviceId;

    /** 입력하면 등록과 동시에 해당 작업자에게 배정한다. */
    private Long workerId;

    private String memo;
}
