package com.moode.server.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * AI 서버가 파싱하는 수신 응답. 기존 프론트용 API 와 달리 ApiResponse 로 감싸지 않고
 * 약속된 형태를 그대로 내보낸다. (연동 규약이 본문 모양까지 고정하고 있어서)
 *
 * <p>여기서의 eventId 는 요청 본문의 eventId(사건 UID)가 아니라 저장된 hazard_event 의 PK 다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Builder
public class HazardIngestResponse {

    private Long eventId;
    private String status;
}
