package com.moode.server.exception;

/** 이미 같은 조치를 실행한 경우 등, 현재 상태와 충돌하는 요청. -> 409 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
