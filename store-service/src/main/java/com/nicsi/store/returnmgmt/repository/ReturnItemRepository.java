package com.nicsi.store.returnmgmt.repository;

import com.nicsi.store.returnmgmt.domain.ReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReturnItemRepository extends JpaRepository<ReturnItem, UUID> {
    List<ReturnItem> findByReturnHeaderId(UUID returnId);
}
