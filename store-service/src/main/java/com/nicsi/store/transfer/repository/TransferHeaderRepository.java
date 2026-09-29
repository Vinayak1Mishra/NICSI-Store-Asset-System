package com.nicsi.store.transfer.repository;

import com.nicsi.store.transfer.domain.TransferHeader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface TransferHeaderRepository extends JpaRepository<TransferHeader, UUID>,
        JpaSpecificationExecutor<TransferHeader> {}
