package com.moode.server.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 목록 응답. 프론트 명세가 본문 모양을 고정하고 있어 ApiResponse 로 감싸지 않는다.
 */
@Getter
@Builder
public class HazardEventListResponse {

    private List<HazardEventListItemResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    private HazardEventSummaryCountResponse summary;
}
