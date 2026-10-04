package com.moode.server.domain;

/** 로그인 주체 구분. JWT 클레임과 로그인 요청(userType)에 공통으로 쓰인다. */
public enum UserType {
    WORKER,
    ADMIN
}
