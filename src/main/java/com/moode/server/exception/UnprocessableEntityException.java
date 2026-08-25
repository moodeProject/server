package com.moode.server.exception;

/**
 * 요청 형식은 맞지만 업무 규칙상 수행할 수 없는 경우. -> 422
 * (예: 작업자에게 연결된 기기가 없어 알림을 보낼 수 없음)
 */
public class UnprocessableEntityException extends RuntimeException {
    public UnprocessableEntityException(String message) {
        super(message);
    }
}
