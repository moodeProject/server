package com.moode.server.repository;

import com.moode.server.domain.WorkLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkLogRepository extends JpaRepository<WorkLog, Long> {

    /** 같은 날 이미 체크인했는지 확인용. 하루에 둘 이상이면 비정상이라 409 로 막는다. */
    Optional<WorkLog> findByWorkerIdAndWorkDate(Long workerId, LocalDate workDate);

    /** 체크아웃 대상. 가장 최근 기록이 아직 열려있는지(checkOutAt null) 서비스에서 판단한다. */
    Optional<WorkLog> findTop1ByWorkerIdOrderByCheckInAtDesc(Long workerId);

    /** 월별 이력 조회. 최신순으로 내려준다. */
    List<WorkLog> findByWorkerIdAndWorkDateBetweenOrderByWorkDateDesc(
            Long workerId, LocalDate from, LocalDate to);
}
