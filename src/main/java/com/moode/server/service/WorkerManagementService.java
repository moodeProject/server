package com.moode.server.service;

import com.moode.server.domain.WorkerOverallStatus;
import com.moode.server.dto.request.WorkerCreateRequest;
import com.moode.server.dto.request.WorkerUpdateRequest;
import com.moode.server.dto.response.WorkerDetailResponse;
import com.moode.server.dto.response.WorkerListResponse;

/** 작업자 관리(CRUD). 센서 상태 조회용 {@link WorkerService}와는 성격이 달라 분리한다. */
public interface WorkerManagementService {

    WorkerListResponse list(String q, WorkerOverallStatus status, int page, int size);

    WorkerDetailResponse getDetail(Long workerId);

    WorkerDetailResponse create(WorkerCreateRequest request);

    WorkerDetailResponse update(Long workerId, WorkerUpdateRequest request);

    void delete(Long workerId);
}
