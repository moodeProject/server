package com.moode.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 기동 시 스프링 컨텍스트가 정상적으로 올라오는지 확인한다.
 *
 * <p>test 프로필을 쓰는 이유: 기본 프로필(local)은 로컬 MySQL 을 찾아서
 * DB 가 없는 환경(CI 등)에서는 이 테스트가 항상 실패한다. test 프로필은 H2 를 쓴다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
