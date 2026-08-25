package com.moode.server.service;

import com.moode.server.domain.HazardEvent;
import com.moode.server.domain.Worker;
import com.moode.server.domain.Zone;

import java.util.List;

/**
 * 위험 알림 발송.
 *
 * <p>AI 서버가 클립 완성을 기다리지 않고 HAZARD_CONFIRMED 를 먼저 보내주는 구조라,
 * 수신 시점 알림을 클립 도착까지 미루면 그 설계가 무의미해진다.
 */
public interface HazardNotificationService {

    /**
     * 해당 작업자에게 경고 알림.
     *
     * @return 실제로 발송한 인원 수
     */
    int notifyWorker(HazardEvent event, Worker worker);

    /**
     * 해당 구역의 작업자 전원에게 접근 금지 알림.
     *
     * @return 실제로 발송한 인원 수
     */
    int notifyZoneWorkers(HazardEvent event, Zone zone, List<Worker> workers);
}
