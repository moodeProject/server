package com.moode.server.dto.response;

import com.moode.server.domain.Worker;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WorkerSummaryResponse {

    private Long id;
    private String name;
    private String employeeNo;

    /** 미배정 헬멧의 이벤트면 null 이 될 수 있다. */
    public static WorkerSummaryResponse from(Worker worker) {
        if (worker == null) {
            return null;
        }
        return WorkerSummaryResponse.builder()
                .id(worker.getId())
                .name(worker.getName())
                .employeeNo(worker.getEmployeeNo())
                .build();
    }
}
