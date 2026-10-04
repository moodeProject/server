package com.moode.server.security;

/** 로그인/인증 실패를 프론트가 분기할 수 있도록 ApiResponse.code 에 실어 보내는 값들. */
public final class AuthErrorCode {

    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String ACCOUNT_PENDING_APPROVAL = "ACCOUNT_PENDING_APPROVAL";
    public static final String ACCOUNT_REJECTED = "ACCOUNT_REJECTED";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";

    private AuthErrorCode() {
    }
}
