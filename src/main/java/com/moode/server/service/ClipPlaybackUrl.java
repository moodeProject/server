package com.moode.server.service;

import java.time.LocalDateTime;

/**
 * 재생용으로 그때그때 발급한 URL과 그 만료 시각.
 *
 * @param url       재생 URL. 저장된 값이 아니라 발급 시점에 만들어진 임시 URL이다
 * @param expiresAt 이 URL이 더 이상 동작하지 않는 시각
 */
public record ClipPlaybackUrl(String url, LocalDateTime expiresAt) {
}
