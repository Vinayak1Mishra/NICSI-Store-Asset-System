package com.nicsi.store.license.repository;

import com.nicsi.store.license.domain.SoftwareLicense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SoftwareLicenseRepository extends JpaRepository<SoftwareLicense, UUID> {

    Optional<SoftwareLicense> findByLicenseCodeIgnoreCase(String licenseCode);

    @Query("SELECT sl FROM SoftwareLicense sl WHERE " +
           "(:status IS NULL OR sl.status = :status) AND " +
           "(:licenseType IS NULL OR sl.licenseType = :licenseType) AND " +
           "(:search IS NULL OR LOWER(sl.licenseCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(sl.item.itemName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<SoftwareLicense> search(
            @Param("status") String status,
            @Param("licenseType") String licenseType,
            @Param("search") String search,
            Pageable pageable
    );
}
