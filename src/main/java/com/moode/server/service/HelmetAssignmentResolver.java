package com.moode.server.service;

/**
 * AI 서버가 보낸 헬멧 코드("H-001")를 helmet/worker/zone 의 id 로 변환한다.
 *
 * <p>helmet, worker, zone 테이블이 아직 이 프로젝트에 없어 현재는 임시 구현만 있다.
 * 해당 엔티티가 생기면 이 인터페이스의 구현만 갈아끼우면 되고 수신 로직은 손대지 않는다.
 */
public interface HelmetAssignmentResolver {

    /**
     * @throws com.moode.server.exception.NotFoundException 코드에 해당하는 헬멧을 찾을 수 없을 때
     */
    HelmetAssignment resolve(String helmetCode);
}
