package com.moode.server.repository;

import com.moode.server.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ZoneRepository extends JpaRepository<Zone, Long> {
    Optional<Zone> findByZoneCode(String zoneCode);

    // 목록 조회용. 등록 순서(id)는 의미가 없고 화면에는 코드순으로 보이는 편이 낫다.
    List<Zone> findAllByOrderByZoneCodeAsc();
}
