package com.moode.server.exception;

import lombok.Getter;

/** 이미 같은 조치를 실행한 경우 등, 현재 상태와 충돌하는 요청. -> 409 */
@Getter
public class ConflictException extends RuntimeException {

    /** 프론트가 분기할 안정적인 코드. 없으면 메시지만으로 처리하던 기존 호출부와 동일하게 동작한다. */
    private final String code;

    public ConflictException(String message) {
        super(message);
        this.code = null;
    }

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }
}
