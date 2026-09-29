package com.nicsi.store.asset.repository;

import com.nicsi.store.asset.domain.RepairTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepairTicketRepository extends JpaRepository<RepairTicket, UUID>, JpaSpecificationExecutor<RepairTicket> {
    List<RepairTicket> findByAssetIdOrderByComplaintDateDesc(UUID assetId);
    Optional<RepairTicket> findFirstByAssetIdAndStatusIn(UUID assetId, List<String> statuses);
    Optional<RepairTicket> findByRepairNo(String repairNo);
}
