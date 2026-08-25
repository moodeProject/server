package com.moode.server.repository;

import com.moode.server.domain.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 목록 조회 필터. 모든 조건이 선택값이라 null 이면 그 조건을 붙이지 않는다.
 */
public final class HazardEventSpecifications {

    private HazardEventSpecifications() {
    }

    public static Specification<HazardEvent> filter(HazardCategory category,
                                                    HazardSeverity severity,
                                                    HazardStatus status,
                                                    Long workerId,
                                                    LocalDateTime from,
                                                    LocalDateTime to) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (category != null) {
                predicates.add(builder.equal(root.get("category"), category));
            }
            if (severity != null) {
                predicates.add(builder.equal(root.get("severity"), severity));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (workerId != null) {
                predicates.add(builder.equal(root.get("worker").get("id"), workerId));
            }
            // 기간은 occurredAt 기준. 목록 정렬 기준과 같은 컬럼이라 인덱스를 탄다.
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("occurredAt"), to));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
