package com.nicsi.store.inventory.repository;

import com.nicsi.store.inventory.domain.InventoryLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryLotRepository extends JpaRepository<InventoryLot, UUID> {
    Optional<InventoryLot> findByItemIdAndStoreIdAndLotNumber(UUID itemId, UUID storeId, String lotNumber);
}
