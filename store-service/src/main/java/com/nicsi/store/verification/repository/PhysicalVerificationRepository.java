package com.nicsi.store.verification.repository;

import com.nicsi.store.verification.domain.PhysicalVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PhysicalVerificationRepository extends JpaRepository<PhysicalVerification, UUID>,
        JpaSpecificationExecutor<PhysicalVerification> {}
