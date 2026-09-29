package com.nicsi.store.asset.repository;

import com.nicsi.store.asset.domain.Disposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisposalRepository extends JpaRepository<Disposal, UUID>, JpaSpecificationExecutor<Disposal> {
    Optional<Disposal> findByDisposalNo(String disposalNo);
}
