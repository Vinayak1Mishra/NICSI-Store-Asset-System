package com.nicsi.store.condemnation.repository;

import com.nicsi.store.condemnation.domain.Condemnation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CondemnationRepository extends JpaRepository<Condemnation, UUID>, JpaSpecificationExecutor<Condemnation> {
    Optional<Condemnation> findByCondemnationNo(String condemnationNo);
}
