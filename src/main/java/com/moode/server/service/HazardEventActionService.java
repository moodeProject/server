package com.moode.server.service;

import com.moode.server.dto.request.HazardActionRequest;
import com.moode.server.dto.response.HazardActionResponse;

public interface HazardEventActionService {

    HazardActionResponse act(Long eventId, HazardActionRequest request);
}
