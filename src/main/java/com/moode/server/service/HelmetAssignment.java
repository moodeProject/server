package com.moode.server.service;

import com.moode.server.domain.Helmet;
import com.moode.server.domain.Worker;
import com.moode.server.domain.Zone;

/**
 * 수신 시점에 확정한 헬멧 배정 스냅샷. hazard_event 에 그대로 박제된다.
 * 나중에 배정이 바뀌어도 사건 기록은 당시 배정을 유지해야 하므로 조회가 아니라 복사다.
 *
 * @param helmet 항상 존재
 * @param worker 미배정 헬멧이면 null
 * @param zone   작업자의 현재 구역. 알 수 없으면 null
 */
public record HelmetAssignment(Helmet helmet, Worker worker, Zone zone) {

    public String helmetNo() {
        return helmet == null ? null : helmet.getHelmetNo();
    }
}
