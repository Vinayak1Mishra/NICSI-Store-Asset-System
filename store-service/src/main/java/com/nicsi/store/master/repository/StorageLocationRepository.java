package com.nicsi.store.master.repository;

import com.nicsi.store.master.domain.StorageLocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StorageLocationRepository extends JpaRepository<StorageLocation, UUID> {
    Page<StorageLocation> findByStoreIdAndActiveTrue(UUID storeId, Pageable pageable);

    @Query("SELECT sl FROM StorageLocation sl WHERE " +
           "(:storeId IS NULL OR sl.store.id = :storeId) " +
           "AND (:parentLocationId IS NULL OR sl.parentLocation.id = :parentLocationId) " +
           "AND (:locationType IS NULL OR sl.locationType = :locationType) " +
           "AND (:active IS NULL OR sl.active = :active)")
    Page<StorageLocation> search(@Param("storeId") UUID storeId, @Param("parentLocationId") UUID parentLocationId, @Param("locationType") String locationType, @Param("active") Boolean active, Pageable pageable);

    List<StorageLocation> findByStoreId(UUID storeId);
    boolean existsByStoreIdAndLocationCodeIgnoreCase(UUID storeId, String code);
    boolean existsByParentLocationIdAndActiveTrue(UUID parentLocationId);
    boolean existsByStoreIdAndActiveTrue(UUID storeId);
}
