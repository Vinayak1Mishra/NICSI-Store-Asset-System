package com.nicsi.store.master.repository;

import com.nicsi.store.master.domain.ItemSubcategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ItemSubcategoryRepository extends JpaRepository<ItemSubcategory, UUID> {
    Page<ItemSubcategory> findByCategoryId(UUID categoryId, Pageable pageable);
    Page<ItemSubcategory> findByCategoryIdAndActiveTrue(UUID categoryId, Pageable pageable);
    boolean existsByCategoryIdAndSubcategoryCodeIgnoreCase(UUID categoryId, String code);
    boolean existsByCategoryIdAndActiveTrue(UUID categoryId);
    List<ItemSubcategory> findByCategoryIdAndActiveTrue(UUID categoryId);
}
