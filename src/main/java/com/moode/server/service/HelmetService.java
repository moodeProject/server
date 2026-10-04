package com.moode.server.service;

import com.moode.server.dto.response.HelmetStatusResponse;

import java.util.List;

/** 헬멧 상태 조회(d~g). 헬멧 관리(CRUD/배정)는 HelmetManagementService 담당. */
public interface HelmetService {
    List<HelmetStatusResponse> getHelmetStatuses();
    HelmetStatusResponse getHelmetStatus(Long helmetId);
}
