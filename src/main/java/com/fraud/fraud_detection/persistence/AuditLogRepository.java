package com.fraud.fraud_detection.persistence;

import com.fraud.fraud_detection.model.AuditLog;
import com.fraud.fraud_detection.model.Decision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByEventTimestampDesc(Pageable pageable);

    Page<AuditLog> findByDecisionOrderByEventTimestampDesc(Decision decision, Pageable pageable);

    long countByDecision(Decision decision);
}