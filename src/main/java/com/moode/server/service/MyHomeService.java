package com.moode.server.service;

import com.moode.server.dto.response.MyHomeResponse;

/** 작업자 앱 홈 화면. 토큰의 작업자 본인 데이터만 다룬다 — 관리자용 조회는 HelmetService/WorkerService. */
public interface MyHomeService {
    MyHomeResponse getHome(Long workerId);
}
