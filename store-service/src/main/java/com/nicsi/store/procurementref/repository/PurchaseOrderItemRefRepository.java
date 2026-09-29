package com.nicsi.store.procurementref.repository;

import com.nicsi.store.procurementref.domain.PurchaseOrderItemRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderItemRefRepository extends JpaRepository<PurchaseOrderItemRef, UUID> {

    List<PurchaseOrderItemRef> findByPurchaseOrderRefId(UUID poRefId);

    Optional<PurchaseOrderItemRef> findByPurchaseOrderRefIdAndPoLineNo(UUID poRefId, Integer poLineNo);
}
