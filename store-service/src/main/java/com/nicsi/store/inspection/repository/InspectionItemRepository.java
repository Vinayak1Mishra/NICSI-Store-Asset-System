package com.nicsi.store.inspection.repository;

import com.nicsi.store.inspection.domain.InspectionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InspectionItemRepository extends JpaRepository<InspectionItem, UUID> {

    List<InspectionItem> findByInspectionId(UUID inspectionId);

    Optional<InspectionItem> findByInspectionIdAndGrnItemId(UUID inspectionId, UUID grnItemId);
}
