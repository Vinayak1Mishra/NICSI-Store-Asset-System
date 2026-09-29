package com.nicsi.store.inventory.repository;

import com.nicsi.store.inventory.domain.StockReservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    Optional<StockReservation> findByReservationNo(String reservationNo);

    List<StockReservation> findByRequisitionItemId(UUID requisitionItemId);

    List<StockReservation> findByStatus(String status);

    @Query("SELECT r FROM StockReservation r " +
           "WHERE (:storeId IS NULL OR r.store.id = :storeId) " +
           "AND (:itemId IS NULL OR r.item.id = :itemId) " +
           "AND (:status IS NULL OR r.status = :status) " +
           "ORDER BY r.createdAt DESC")
    Page<StockReservation> search(
            @Param("storeId") UUID storeId,
            @Param("itemId") UUID itemId,
            @Param("status") String status,
            Pageable pageable
    );
}
