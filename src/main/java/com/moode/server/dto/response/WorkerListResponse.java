package com.moode.server.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 작업자 관리 목록. 화면 상단의 "10명 등록 중 · 정상 6 주의 3 위험 1" 요약을 함께 내려준다. */
@Getter
@Builder
public class WorkerListResponse {

    private List<WorkerListItemResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    private long normalCount;
    private long cautionCount;
    private long dangerCount;
}
