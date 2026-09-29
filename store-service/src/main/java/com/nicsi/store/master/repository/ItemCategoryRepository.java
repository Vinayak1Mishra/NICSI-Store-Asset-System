package com.nicsi.store.master.repository;

import com.nicsi.store.master.domain.ItemCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemCategoryRepository extends JpaRepository<ItemCategory, UUID> {
    Page<ItemCategory> findByActiveTrue(Pageable pageable);
    Page<ItemCategory> findByCategoryCodeContainingIgnoreCaseOrCategoryNameContainingIgnoreCase(String code, String name, Pageable pageable);
    Optional<ItemCategory> findByCategoryCodeIgnoreCase(String code);
    boolean existsByCategoryCodeIgnoreCase(String code);
}
