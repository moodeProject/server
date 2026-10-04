package com.moode.server.service.impl;

import com.moode.server.domain.Helmet;
import com.moode.server.service.HelmetReconnectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LoggingHelmetReconnectService implements HelmetReconnectService {

    @Override
    public void requestReconnect(Helmet helmet) {
        log.info("[헬멧 재연결 요청] helmetNo={}, deviceId={} — 실제 하드웨어 연동 전이라 기록만 남긴다.",
                helmet.getHelmetNo(), helmet.getDeviceId());
    }
}
