package com.nicsi.store.warranty.repository;

import com.nicsi.store.warranty.domain.SupportContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface SupportContractRepository extends JpaRepository<SupportContract, UUID>,
        JpaSpecificationExecutor<SupportContract> {}
