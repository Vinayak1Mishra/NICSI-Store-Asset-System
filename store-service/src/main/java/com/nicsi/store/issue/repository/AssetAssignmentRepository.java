package com.nicsi.store.issue.repository;

import com.nicsi.store.issue.domain.AssetAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, UUID> {

    /**
     * Find the current active assignment for an asset.
     * An asset should have at most one ACTIVE assignment at any time.
     */
    Optional<AssetAssignment> findFirstByAssetIdAndStatus(UUID assetId, String status);

    List<AssetAssignment> findByIssueId(UUID issueId);

    @Query("SELECT a FROM AssetAssignment a WHERE a.assigneeUserId = :userId AND a.status = 'ACTIVE'")
    List<AssetAssignment> findActiveByAssigneeUserId(@Param("userId") UUID userId);

    List<AssetAssignment> findByAssetIdOrderByAssignedFromDesc(UUID assetId);
}
