package com.nicsi.store.inventory.repository;

import com.nicsi.store.inventory.domain.StockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {

    Optional<StockAdjustment> findByAdjustmentNo(String adjustmentNo);

    @Query("SELECT sa FROM StockAdjustment sa " +
           "WHERE (:storeId IS NULL OR sa.store.id = :storeId) " +
           "AND (:status IS NULL OR sa.status = :status) " +
           "AND (:reasonCode IS NULL OR sa.reasonCode = :reasonCode) " +
           "ORDER BY sa.createdAt DESC")
    Page<StockAdjustment> search(
            @Param("storeId") UUID storeId,
            @Param("status") String status,
            @Param("reasonCode") String reasonCode,
            Pageable pageable
    );
}
