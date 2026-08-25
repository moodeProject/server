package com.moode.server.repository;

import com.moode.server.domain.InboundMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class InboundMessageRepositoryTest {

    @Autowired
    private InboundMessageRepository inboundMessageRepository;

    private InboundMessage message(String key) {
        return InboundMessage.builder()
                .idempotencyKey(key)
                .eventUid("dadeb552483643e4b32052f12ffbef3e")
                .eventType("HAZARD_CONFIRMED")
                .build();
    }

    @Test
    @DisplayName("같은 idempotencyKey 를 두 번 저장하면 조용히 덮어쓰지 않고 제약 위반이 난다")
    void duplicateKeyViolatesConstraint() {
        inboundMessageRepository.saveAndFlush(message("0bae5301b4f64746a2b9bb1db5de1e49"));

        // Persistable 을 구현하지 않으면 Spring Data 가 이걸 merge(UPDATE)로 처리해
        // 예외 없이 통과하고, 동시 수신 시 멱등성이 무력화된다.
        assertThatThrownBy(() ->
                inboundMessageRepository.saveAndFlush(message("0bae5301b4f64746a2b9bb1db5de1e49")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("idempotencyKey 가 다르면 같은 eventUid 여도 함께 저장된다")
    void differentKeysCoexist() {
        inboundMessageRepository.saveAndFlush(message("11111111111111111111111111111111"));
        inboundMessageRepository.saveAndFlush(message("22222222222222222222222222222222"));

        assertThat(inboundMessageRepository.findAll()).hasSize(2);
    }
}
