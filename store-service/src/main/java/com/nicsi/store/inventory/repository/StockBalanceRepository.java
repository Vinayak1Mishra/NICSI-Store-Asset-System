package com.nicsi.store.inventory.repository;

import com.nicsi.store.inventory.domain.StockBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockBalanceRepository extends JpaRepository<StockBalance, UUID> {

    @Query("SELECT sb FROM StockBalance sb WHERE sb.item.id = :itemId AND sb.store.id = :storeId " +
           "AND sb.location.id = :locationId AND " +
           "((:lotId IS NULL AND sb.lot IS NULL) OR (sb.lot.id = :lotId))")
    Optional<StockBalance> findByDimensions(
            @Param("itemId") UUID itemId,
            @Param("storeId") UUID storeId,
            @Param("locationId") UUID locationId,
            @Param("lotId") UUID lotId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sb FROM StockBalance sb WHERE sb.item.id = :itemId AND sb.store.id = :storeId " +
           "AND sb.location.id = :locationId AND " +
           "((:lotId IS NULL AND sb.lot IS NULL) OR (sb.lot.id = :lotId))")
    Optional<StockBalance> findByDimensionsForUpdate(
            @Param("itemId") UUID itemId,
            @Param("storeId") UUID storeId,
            @Param("locationId") UUID locationId,
            @Param("lotId") UUID lotId
    );

    @Query("SELECT sb FROM StockBalance sb " +
           "JOIN sb.item i " +
           "WHERE (:storeId IS NULL OR sb.store.id = :storeId) " +
           "AND (:categoryId IS NULL OR i.category.id = :categoryId) " +
           "AND (:search IS NULL OR LOWER(i.itemCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(i.itemName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(sb.location.locationCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "ORDER BY i.itemName ASC, sb.store.storeName ASC")
    Page<StockBalance> search(
            @Param("storeId") UUID storeId,
            @Param("categoryId") UUID categoryId,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT sb FROM StockBalance sb WHERE sb.store.id = :storeId")
    List<StockBalance> findByStoreId(@Param("storeId") UUID storeId);

    @Query("SELECT sb FROM StockBalance sb " +
           "JOIN sb.item i " +
           "WHERE sb.store.id = :storeId AND sb.item.id = :itemId")
    List<StockBalance> findByStoreIdAndItemId(@Param("storeId") UUID storeId, @Param("itemId") UUID itemId);

    @Query("SELECT sb FROM StockBalance sb " +
           "JOIN sb.item i " +
           "WHERE (sb.onHandQty - sb.reservedQty) <= (" +
           "  SELECT COALESCE(p.reorderLevelQty, 0) FROM ItemStorePolicy p " +
           "  WHERE p.item.id = sb.item.id AND p.store.id = sb.store.id" +
           ")")
    Page<StockBalance> findLowStock(Pageable pageable);

    @Query("SELECT sb FROM StockBalance sb ORDER BY sb.item.id, sb.store.id, sb.location.id")
    List<StockBalance> findAllForReconciliation();
}
