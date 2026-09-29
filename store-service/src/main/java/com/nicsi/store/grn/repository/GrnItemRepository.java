package com.nicsi.store.grn.repository;

import com.nicsi.store.grn.domain.GrnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GrnItemRepository extends JpaRepository<GrnItem, UUID> {

    List<GrnItem> findByGrnId(UUID grnId);

    List<GrnItem> findByPurchaseOrderItemRefId(UUID poItemRefId);
}
