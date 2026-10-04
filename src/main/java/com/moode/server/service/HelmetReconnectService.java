package com.moode.server.service;

import com.moode.server.domain.Helmet;

/**
 * 헬멧 재연결 요청. 실제 하드웨어(MQTT/펌웨어) 연동이 아직 없어 지금은 요청을 기록만 하는
 * 스텁 구현({@code LoggingHelmetReconnectService})만 있다. {@link HelmetAssignmentResolver} 와
 * 같은 이유로 인터페이스로 분리해뒀다 — 실제 연동이 생기면 구현체만 갈아끼우면 되고
 * {@code HelmetManagementServiceImpl} 은 손대지 않는다.
 */
public interface HelmetReconnectService {

    void requestReconnect(Helmet helmet);
}
