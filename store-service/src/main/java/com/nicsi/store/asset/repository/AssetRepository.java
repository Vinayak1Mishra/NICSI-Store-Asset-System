package com.nicsi.store.asset.repository;

import com.nicsi.store.asset.domain.Asset;
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
public interface AssetRepository extends JpaRepository<Asset, UUID> {

    Optional<Asset> findByAssetCode(String assetCode);

    Optional<Asset> findByQrCodeValue(String qrCodeValue);

    List<Asset> findByGrnItemId(UUID grnItemId);

    long countByItemIdAndStoreIdAndAssetStatus(UUID itemId, UUID storeId, String assetStatus);

    long countByItemIdAndStoreIdAndLocationIdAndAssetStatus(UUID itemId, UUID storeId, UUID locationId, String assetStatus);

    @Query("SELECT a FROM Asset a " +
           "JOIN a.item i " +
           "WHERE (:storeId IS NULL OR a.store.id = :storeId) " +
           "AND (:itemId IS NULL OR a.item.id = :itemId) " +
           "AND (:assetStatus IS NULL OR a.assetStatus = :assetStatus) " +
           "AND (:conditionStatus IS NULL OR a.conditionStatus = :conditionStatus) " +
           "AND (:search IS NULL OR LOWER(a.assetCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(i.itemCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(i.itemName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(a.qrCodeValue) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "ORDER BY a.createdAt DESC")
    Page<Asset> search(
            @Param("storeId") UUID storeId,
            @Param("itemId") UUID itemId,
            @Param("assetStatus") String assetStatus,
            @Param("conditionStatus") String conditionStatus,
            @Param("search") String search,
            Pageable pageable
    );
}
