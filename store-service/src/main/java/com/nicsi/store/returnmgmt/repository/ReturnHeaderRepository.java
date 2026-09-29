package com.nicsi.store.returnmgmt.repository;

import com.nicsi.store.returnmgmt.domain.ReturnHeader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReturnHeaderRepository extends JpaRepository<ReturnHeader, UUID>,
        JpaSpecificationExecutor<ReturnHeader> {

    Optional<ReturnHeader> findByReturnNo(String returnNo);
}
