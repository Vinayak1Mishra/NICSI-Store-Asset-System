package com.nicsi.store.inspection.repository;

import com.nicsi.store.inspection.domain.Inspection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

    Optional<Inspection> findByInspectionNo(String inspectionNo);

    Optional<Inspection> findByGrnId(UUID grnId);

    boolean existsByInspectionNo(String inspectionNo);

    @Query("SELECT i FROM Inspection i WHERE " +
           "(:status IS NULL OR i.status = :status) " +
           "AND (:search IS NULL OR LOWER(i.inspectionNo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(i.grn.grnNo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "ORDER BY i.createdAt DESC")
    Page<Inspection> search(
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable
    );
}
