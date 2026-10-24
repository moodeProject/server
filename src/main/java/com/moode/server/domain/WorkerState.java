package com.moode.server.domain;

public enum WorkerState {
    NORMAL,
    RECOMMEND,
    ACTION_REQUIRED,
    FALLING,
    FALLEN,
    // 낙상 후 일정 시간 움직임이 없어 헬멧이 의식불명으로 확정한 상태. 추락 트랙의 최고 단계라
    // FALLEN 뒤에 둔다 (위험 이벤트는 ordinal 이 올라갈 때만 만들어지므로 순서가 의미를 가진다).
    UNCONSCIOUS
}
