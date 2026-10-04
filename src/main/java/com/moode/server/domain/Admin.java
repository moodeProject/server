package com.moode.server.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 관리자 계정. ERD 의 manager_account 를 가리킨다.
 *
 * <p>가입 직후 status=PENDING 이고, 다른 관리자가 승인(APPROVED)해야 로그인이 가능하다.
 * approvedBy 는 manager_account.admin_id 를 가리키지만, Zone/HazardEvent 의 관리자 참조와
 * 같은 이유로 연관관계 대신 스칼라로 둔다. 데모에서는 auth.admin.auto-approve-signup 설정으로
 * 가입 즉시 APPROVED 처리할 수 있어(이 경우 approvedBy 는 null로 남는다) 승인자가 아무도 없는
 * "최초 관리자" 문제를 별도로 다루지 않는다.
 */
@Entity
@Table(
        name = "manager_account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_manager_account_login_id", columnNames = "login_id"),
                @UniqueConstraint(name = "uk_manager_account_email", columnNames = "email")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    private Long id;

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 50)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private AdminStatus status = AdminStatus.PENDING;

    // manager_account.admin_id. 자동승인이면 null로 남는다.
    private Long approvedBy;

    private LocalDateTime approvedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isApproved() {
        return this.status == AdminStatus.APPROVED;
    }

    /** 데모용 자동승인. approvedBy 는 승인한 사람이 없으므로 null로 둔다. */
    public void autoApprove() {
        this.status = AdminStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
    }
}
