package com.nicsi.store.grn.repository;

import com.nicsi.store.grn.domain.Grn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GrnRepository extends JpaRepository<Grn, UUID> {

    Optional<Grn> findByGrnNo(String grnNo);

    boolean existsByGrnNo(String grnNo);

    @Query("SELECT g FROM Grn g WHERE " +
           "(:storeId IS NULL OR g.store.id = :storeId) " +
           "AND (:status IS NULL OR g.status = :status) " +
           "AND (:search IS NULL OR LOWER(g.grnNo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(g.vendorNameSnapshot) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(g.challanNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(g.invoiceNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "ORDER BY g.createdAt DESC")
    Page<Grn> search(
            @Param("storeId") UUID storeId,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable
    );
}
