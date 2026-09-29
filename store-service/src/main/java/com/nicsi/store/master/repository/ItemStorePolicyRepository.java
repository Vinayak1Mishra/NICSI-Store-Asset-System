package com.nicsi.store.master.repository;

import com.nicsi.store.master.domain.ItemStorePolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ItemStorePolicyRepository extends JpaRepository<ItemStorePolicy, UUID> {
    @Query("SELECT p FROM ItemStorePolicy p WHERE " +
           "(:itemId IS NULL OR p.item.id = :itemId) " +
           "AND (:storeId IS NULL OR p.store.id = :storeId) " +
           "AND (:active IS NULL OR p.active = :active)")
    Page<ItemStorePolicy> search(@Param("itemId") UUID itemId, @Param("storeId") UUID storeId, @Param("active") Boolean active, Pageable pageable);

    boolean existsByItemIdAndStoreId(UUID itemId, UUID storeId);

    java.util.Optional<ItemStorePolicy> findByItemIdAndStoreId(UUID itemId, UUID storeId);
}
