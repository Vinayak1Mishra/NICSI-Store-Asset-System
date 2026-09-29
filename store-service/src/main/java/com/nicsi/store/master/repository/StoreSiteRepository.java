package com.nicsi.store.master.repository;

import com.nicsi.store.master.domain.StoreSite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreSiteRepository extends JpaRepository<StoreSite, UUID> {
    Page<StoreSite> findByActiveTrue(Pageable pageable);

    @Query("SELECT s FROM StoreSite s WHERE " +
           "(:search IS NULL OR LOWER(s.storeCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(s.storeName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:storeType IS NULL OR s.storeType = :storeType) " +
           "AND (:active IS NULL OR s.active = :active)")
    Page<StoreSite> search(@Param("search") String search, @Param("storeType") String storeType, @Param("active") Boolean active, Pageable pageable);

    Optional<StoreSite> findByStoreCodeIgnoreCase(String code);
    boolean existsByStoreCodeIgnoreCase(String code);
}
