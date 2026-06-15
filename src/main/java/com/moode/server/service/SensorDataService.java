package com.moode.server.service;

import com.moode.server.dto.request.SensorDataRequest;
import com.moode.server.dto.response.SensorDataResponse;

public interface SensorDataService {
    SensorDataResponse processSensorData(SensorDataRequest request);
}
