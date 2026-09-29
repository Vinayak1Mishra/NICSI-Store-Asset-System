package com.nicsi.store.master.validation;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.master.repository.ItemSubcategoryRepository;
import com.nicsi.store.master.repository.StorageLocationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MasterInUseValidator {

    private final ItemRepository itemRepository;
    private final ItemSubcategoryRepository itemSubcategoryRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final JdbcTemplate jdbcTemplate;

    public MasterInUseValidator(
            ItemRepository itemRepository,
            ItemSubcategoryRepository itemSubcategoryRepository,
            StorageLocationRepository storageLocationRepository,
            JdbcTemplate jdbcTemplate) {
        this.itemRepository = itemRepository;
        this.itemSubcategoryRepository = itemSubcategoryRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void validateUomDeactivation(UUID uomId) {
        if (itemRepository.existsByBaseUomIdAndActiveTrue(uomId)) {
            throw new BusinessException("UOM_IN_USE", "Cannot deactivate: UOM is in use by active items", HttpStatus.CONFLICT);
        }
    }

    public void validateCategoryDeactivation(UUID categoryId) {
        if (itemSubcategoryRepository.existsByCategoryIdAndActiveTrue(categoryId)) {
            throw new BusinessException("CATEGORY_IN_USE", "Cannot deactivate: Category has active subcategories", HttpStatus.CONFLICT);
        }
        if (itemRepository.existsByCategoryIdAndActiveTrue(categoryId)) {
            throw new BusinessException("CATEGORY_IN_USE", "Cannot deactivate: Category is in use by active items", HttpStatus.CONFLICT);
        }
    }

    public void validateSubcategoryDeactivation(UUID subcategoryId) {
        if (itemRepository.existsBySubcategoryIdAndActiveTrue(subcategoryId)) {
            throw new BusinessException("SUBCATEGORY_IN_USE", "Cannot deactivate: Subcategory is in use by active items", HttpStatus.CONFLICT);
        }
    }

    public void validateItemDeactivation(UUID itemId) {
        BigDecimal stock = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(on_hand_qty), 0) FROM store.stock_balance WHERE item_id = ?",
                BigDecimal.class, itemId);
        if (stock != null && stock.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("ITEM_HAS_POSITIVE_STOCK", "Cannot deactivate: Item has positive stock balance", HttpStatus.CONFLICT);
        }
    }

    public void validateStoreDeactivation(UUID storeId) {
        if (storageLocationRepository.existsByStoreIdAndActiveTrue(storeId)) {
            throw new BusinessException("STORE_HAS_LOCATIONS", "Cannot deactivate: Store has active locations", HttpStatus.CONFLICT);
        }
        BigDecimal stock = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(on_hand_qty), 0) FROM store.stock_balance WHERE store_id = ?",
                BigDecimal.class, storeId);
        if (stock != null && stock.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("STORE_HAS_POSITIVE_STOCK", "Cannot deactivate: Store has positive stock balance", HttpStatus.CONFLICT);
        }
    }

    public void validateLocationDeactivation(UUID locationId) {
        if (storageLocationRepository.existsByParentLocationIdAndActiveTrue(locationId)) {
            throw new BusinessException("LOCATION_IN_USE", "Cannot deactivate: Location is in use as a parent location", HttpStatus.CONFLICT);
        }
        BigDecimal stock = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(on_hand_qty), 0) FROM store.stock_balance WHERE location_id = ?",
                BigDecimal.class, locationId);
        if (stock != null && stock.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("LOCATION_HAS_POSITIVE_STOCK", "Cannot deactivate: Location has positive stock balance", HttpStatus.CONFLICT);
        }
    }
}
