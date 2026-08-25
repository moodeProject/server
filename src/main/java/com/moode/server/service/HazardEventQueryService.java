package com.moode.server.service;

import com.moode.server.domain.HazardCategory;
import com.moode.server.domain.HazardSeverity;
import com.moode.server.domain.HazardStatus;
import com.moode.server.dto.response.HazardEventDetailResponse;
import com.moode.server.dto.response.HazardEventListResponse;

import java.time.OffsetDateTime;

public interface HazardEventQueryService {

    HazardEventListResponse search(HazardCategory category,
                                   HazardSeverity severity,
                                   HazardStatus status,
                                   Long workerId,
                                   OffsetDateTime from,
                                   OffsetDateTime to,
                                   int page,
                                   int size);

    HazardEventDetailResponse getDetail(Long eventId);
}
