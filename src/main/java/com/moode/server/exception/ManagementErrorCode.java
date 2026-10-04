package com.moode.server.exception;

/** 작업자/헬멧 관리 API의 충돌 상황을 프론트가 분기할 수 있도록 ApiResponse.code 에 싣는 값들. */
public final class ManagementErrorCode {

    public static final String HELMET_NO_DUPLICATE = "HELMET_NO_DUPLICATE";
    public static final String HELMET_ALREADY_ASSIGNED = "HELMET_ALREADY_ASSIGNED";
    public static final String WORKER_ALREADY_HAS_HELMET = "WORKER_ALREADY_HAS_HELMET";

    private ManagementErrorCode() {
    }
}
