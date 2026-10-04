package com.moode.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 작업자 추가(docs/ui/admin "작업자 추가" 모달). 사번/비밀번호는 입력받지 않는다 —
 * 사번은 서버가 자동 채번하고, 비밀번호는 로그인 불가 상태로 랜덤 생성해 버린다
 * (작업자 회원가입 API 자체가 없음 — 로그인은 데모 시드 계정 WK-20241103 전용).
 */
@Getter
@NoArgsConstructor
public class WorkerCreateRequest {

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @NotBlank(message = "소속 구역은 필수입니다.")
    private String zoneName;

    /** 화면에는 있지만 Worker 에 대응 컬럼이 없어 현재는 받기만 하고 저장하지 않는다. */
    private String detailLocation;

    /** 입력하면 같은 번호의 새 헬멧을 만들어 이 작업자에게 바로 배정한다 (기존 헬멧 조회가 아님). */
    private String helmetNo;
}
