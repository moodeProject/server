package com.moode.server.service;

import com.moode.server.dto.request.HazardEventIngestRequest;

/**
 * AI 서버(FastAPI)가 보낸 위험 감지 메시지 수신.
 */
public interface HazardEventIngestService {

    HazardIngestResult receive(HazardEventIngestRequest request);
}
