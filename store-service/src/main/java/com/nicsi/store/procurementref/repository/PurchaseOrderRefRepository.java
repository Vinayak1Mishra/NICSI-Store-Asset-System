package com.nicsi.store.procurementref.repository;

import com.nicsi.store.procurementref.domain.PurchaseOrderRef;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRefRepository extends JpaRepository<PurchaseOrderRef, UUID> {

    Optional<PurchaseOrderRef> findByPoNumber(String poNumber);

    Optional<PurchaseOrderRef> findBySourceSystemAndPoNumber(String sourceSystem, String poNumber);

    boolean existsByPoNumber(String poNumber);

    @Query("SELECT po FROM PurchaseOrderRef po WHERE " +
           "(:search IS NULL OR LOWER(po.poNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(po.vendorNameSnapshot) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           " OR LOWER(po.gemOrderNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:status IS NULL OR po.status = :status) " +
           "ORDER BY po.createdAt DESC")
    Page<PurchaseOrderRef> search(
            @Param("search") String search,
            @Param("status") String status,
            Pageable pageable
    );
}
