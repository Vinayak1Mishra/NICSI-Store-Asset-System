package com.nicsi.store.license.repository;

import com.nicsi.store.license.domain.SoftwareLicenseAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SoftwareLicenseAllocationRepository extends JpaRepository<SoftwareLicenseAllocation, UUID> {

    List<SoftwareLicenseAllocation> findBySoftwareLicenseId(UUID softwareLicenseId);

    List<SoftwareLicenseAllocation> findByUserIdAndStatus(UUID userId, String status);

    List<SoftwareLicenseAllocation> findByAssetIdAndStatus(UUID assetId, String status);
}
