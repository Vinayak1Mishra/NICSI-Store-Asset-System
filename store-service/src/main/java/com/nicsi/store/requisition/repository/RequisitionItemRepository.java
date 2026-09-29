package com.nicsi.store.requisition.repository;

import com.nicsi.store.requisition.domain.RequisitionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequisitionItemRepository extends JpaRepository<RequisitionItem, UUID> {
    List<RequisitionItem> findByRequisitionIdOrderByLineNoAsc(UUID requisitionId);
    void deleteByRequisitionId(UUID requisitionId);
}
