package com.moode.server.service;

import com.moode.server.domain.SosStatus;
import com.moode.server.dto.request.SosCreateRequest;
import com.moode.server.dto.response.SosResponse;

import java.util.List;

public interface SosService {
    SosResponse request(SosCreateRequest request);
    List<SosResponse> getSosList(SosStatus status);
    SosResponse resolve(Long sosId);
}
