package com.nicsi.store.issue.repository;

import com.nicsi.store.issue.domain.IssueHeader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IssueHeaderRepository extends JpaRepository<IssueHeader, UUID> {

    Optional<IssueHeader> findByIssueNo(String issueNo);

    @Query("""
            SELECT i FROM IssueHeader i
            WHERE (:storeId IS NULL OR i.store.id = :storeId)
              AND (:status IS NULL OR i.status = :status)
              AND (:issuedToUserId IS NULL OR i.issuedToUserId = :issuedToUserId)
            ORDER BY i.issueDate DESC, i.createdAt DESC
            """)
    Page<IssueHeader> search(
            @Param("storeId") UUID storeId,
            @Param("status") String status,
            @Param("issuedToUserId") UUID issuedToUserId,
            Pageable pageable
    );
}
