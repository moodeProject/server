package com.moode.server.service;

import com.moode.server.dto.request.HelmetCreateRequest;
import com.moode.server.dto.request.HelmetUpdateRequest;
import com.moode.server.dto.response.HelmetResponse;

import java.util.List;

public interface HelmetManagementService {

    List<HelmetResponse> list(String q);

    HelmetResponse create(HelmetCreateRequest request);

    HelmetResponse update(Long helmetId, HelmetUpdateRequest request);

    void delete(Long helmetId);

    HelmetResponse assign(Long helmetId, Long workerId);

    HelmetResponse unassign(Long helmetId);

    HelmetResponse reconnect(Long helmetId);
}
