package com.nicsi.store.inventory.repository;

import com.nicsi.store.inventory.domain.StockTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransaction, UUID> {

    Optional<StockTransaction> findByIdempotencyKey(String idempotencyKey);

    List<StockTransaction> findAllByIdempotencyKeyStartingWith(String prefix);

    List<StockTransaction> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);

    Optional<StockTransaction> findByTransactionNo(String transactionNo);

    @Query("SELECT st FROM StockTransaction st " +
           "JOIN st.item i " +
           "WHERE (:itemId IS NULL OR st.item.id = :itemId) " +
           "AND (:storeId IS NULL OR st.store.id = :storeId) " +
           "AND (:locationId IS NULL OR st.location.id = :locationId) " +
           "AND (:transactionType IS NULL OR st.transactionType = :transactionType) " +
           // Deliberately NOT ":fromDate IS NULL OR ...". When a null parameter is bound into an
           // IS NULL predicate, Hibernate sends it with an unspecified JDBC type and PostgreSQL
           // receives bytea, so it fails with "cannot cast type bytea to timestamp with time zone"
           // (and "could not determine data type of parameter" before any cast is added). A HQL
           // CAST only fixes the target type, not the type arriving, so it cannot rescue this.
           // Both bounds are therefore required to be non-null; StockQueryService substitutes
           // sentinels when the caller omits them, which is why this stays a plain range.
           "AND st.transactionTime >= :fromDate " +
           "AND st.transactionTime <= :toDate " +
           "AND (:search IS NULL OR LOWER(st.transactionNo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(st.referenceNo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(i.itemCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(i.itemName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "ORDER BY st.transactionTime DESC, st.transactionNo DESC")
    Page<StockTransaction> search(
            @Param("itemId") UUID itemId,
            @Param("storeId") UUID storeId,
            @Param("locationId") UUID locationId,
            @Param("transactionType") String transactionType,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT COALESCE(SUM(st.quantityIn), 0) - COALESCE(SUM(st.quantityOut), 0) FROM StockTransaction st " +
           "WHERE st.item.id = :itemId AND st.store.id = :storeId AND st.location.id = :locationId " +
           "AND ((:lotId IS NULL AND st.lot IS NULL) OR (st.lot.id = :lotId))")
    BigDecimal calculateNetBalance(
            @Param("itemId") UUID itemId,
            @Param("storeId") UUID storeId,
            @Param("locationId") UUID locationId,
            @Param("lotId") UUID lotId
    );
}
