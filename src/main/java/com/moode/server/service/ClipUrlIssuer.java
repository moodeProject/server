package com.moode.server.service;

import com.moode.server.domain.HazardClip;

/**
 * 비공개 버킷의 클립을 재생할 수 있는 임시 URL을 발급한다.
 *
 * <p>URL은 DB에 저장하지 않는다. 저장해두면 만료를 관리할 수 없어, 만료된 URL을 계속 내려주거나
 * 반대로 만료되지 않는 공개 링크를 방치하게 된다. 조회 요청마다 새로 발급하는 게 핵심이다.
 */
public interface ClipUrlIssuer {

    ClipPlaybackUrl issue(HazardClip clip);
}
