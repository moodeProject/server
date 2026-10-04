package com.moode.server.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 헬멧 정보 수정. helmetNo/deviceId는 하드웨어 식별자라 등록 후 바꾸지 않는다(센서 수신 매칭이
 * 꼬일 수 있음) — 작업자 배정 변경은 assign/unassign 전용 API를 쓴다. 지금은 메모만 수정 대상이다.
 */
@Getter
@NoArgsConstructor
public class HelmetUpdateRequest {

    private String memo;
}
