package com.nicsi.store.master.repository;

import com.nicsi.store.master.domain.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {
    Page<Item> findByActiveTrue(Pageable pageable);

    @Query("SELECT i FROM Item i WHERE " +
           "(:search IS NULL OR LOWER(i.itemCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(i.itemName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:categoryId IS NULL OR i.category.id = :categoryId) " +
           "AND (:itemType IS NULL OR i.itemType = :itemType) " +
           "AND (:trackingType IS NULL OR i.trackingType = :trackingType) " +
           "AND (:active IS NULL OR i.active = :active)")
    Page<Item> search(@Param("search") String search, @Param("categoryId") UUID categoryId, @Param("itemType") String itemType, @Param("trackingType") String trackingType, @Param("active") Boolean active, Pageable pageable);

    Optional<Item> findByItemCodeIgnoreCase(String code);
    boolean existsByItemCodeIgnoreCase(String code);
    boolean existsByBaseUomIdAndActiveTrue(UUID uomId);
    boolean existsByCategoryIdAndActiveTrue(UUID categoryId);
    boolean existsBySubcategoryIdAndActiveTrue(UUID subcategoryId);
}
