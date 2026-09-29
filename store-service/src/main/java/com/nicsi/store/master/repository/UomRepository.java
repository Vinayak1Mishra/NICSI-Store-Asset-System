package com.nicsi.store.master.repository;

import com.nicsi.store.master.domain.Uom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UomRepository extends JpaRepository<Uom, UUID> {
    Page<Uom> findByActiveTrue(Pageable pageable);
    Page<Uom> findByUomCodeContainingIgnoreCaseOrUomNameContainingIgnoreCase(String code, String name, Pageable pageable);
    Optional<Uom> findByUomCodeIgnoreCase(String code);
    boolean existsByUomCodeIgnoreCase(String code);
}
