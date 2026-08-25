package com.moode.server.repository;

import com.moode.server.domain.InboundMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundMessageRepository extends JpaRepository<InboundMessage, String> {
}
