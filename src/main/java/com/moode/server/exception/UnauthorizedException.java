package com.moode.server.exception;

import lombok.Getter;

@Getter
public class UnauthorizedException extends RuntimeException {

    /** 프론트가 분기할 안정적인 코드. 없으면 메시지만으로 처리하던 기존 호출부와 동일하게 동작한다. */
    private final String code;

    public UnauthorizedException(String message) {
        super(message);
        this.code = null;
    }

    public UnauthorizedException(String code, String message) {
        super(message);
        this.code = code;
    }
}
