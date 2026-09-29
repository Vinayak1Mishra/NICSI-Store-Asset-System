package com.nicsi.store.issue.repository;

import com.nicsi.store.issue.domain.IssueItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IssueItemRepository extends JpaRepository<IssueItem, UUID> {

    List<IssueItem> findByIssueId(UUID issueId);
}
