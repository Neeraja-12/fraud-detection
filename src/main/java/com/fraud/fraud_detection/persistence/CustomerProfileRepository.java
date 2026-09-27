package com.fraud.fraud_detection.persistence;

import com.fraud.fraud_detection.model.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for customer profiles.
 * No implementation needed — Spring generates it at startup.
 */
@Repository
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, String> {
}