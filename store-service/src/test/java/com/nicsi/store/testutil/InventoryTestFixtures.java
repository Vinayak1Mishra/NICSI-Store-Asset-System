package com.nicsi.store.testutil;

import com.nicsi.store.inventory.domain.StockTransaction;
import com.nicsi.store.inventory.service.InventoryPostingRequest;
import com.nicsi.store.inventory.service.InventoryPostingService;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.ItemCategory;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.master.domain.Uom;
import com.nicsi.store.master.repository.ItemCategoryRepository;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.master.repository.StorageLocationRepository;
import com.nicsi.store.master.repository.StoreSiteRepository;
import com.nicsi.store.master.repository.UomRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Option A test isolation for the Phase 4 concurrency / reconciliation tests.
 *
 * These tests must NOT wipe tables, so every test creates its own uniquely-keyed master data
 * (item code, store code, location code suffixed with a random tag) and scopes every query to
 * the returned ids. Nothing here collides with another test class or with another test method,
 * so the tests can run in any order and repeatedly against a shared database.
 *
 * Deliberately does not touch audit.event, store.uom or any workflow.* table.
 */
@Component
public class InventoryTestFixtures {

    public static final String UOM_CODE = "NOS";

    private final StoreSiteRepository storeSiteRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final ItemCategoryRepository itemCategoryRepository;
    private final ItemRepository itemRepository;
    private final UomRepository uomRepository;
    private final InventoryPostingService postingService;

    public InventoryTestFixtures(
            StoreSiteRepository storeSiteRepository,
            StorageLocationRepository storageLocationRepository,
            ItemCategoryRepository itemCategoryRepository,
            ItemRepository itemRepository,
            UomRepository uomRepository,
            InventoryPostingService postingService
    ) {
        this.storeSiteRepository = storeSiteRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.itemCategoryRepository = itemCategoryRepository;
        this.itemRepository = itemRepository;
        this.uomRepository = uomRepository;
        this.postingService = postingService;
    }

    /** All master data for one isolated test scope. */
    public record Ctx(Item item, StoreSite store, StorageLocation location, Uom uom, String tag) {}

    /** Random short tag used to make every generated code unique across tests and runs. */
    public static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Quantity-tracked consumable: exercises weighted average, no lot, no asset. */
    @Transactional
    public Ctx newConsumable(String label) {
        return create(label, "CONSUMABLE", "QUANTITY", false, false);
    }

    /** Lot-tracked consumable: exercises lot creation on receipt. */
    @Transactional
    public Ctx newLotTracked(String label) {
        return create(label, "CONSUMABLE", "LOT", false, true);
    }

    /** Serialised asset item: exercises asset generation and the serialised reconciliation check. */
    @Transactional
    public Ctx newSerialised(String label) {
        return create(label, "NON_CONSUMABLE", "SERIAL", true, false);
    }

    private Ctx create(String label, String itemType, String trackingType, boolean assetRequired, boolean expiryTracking) {
        String t = tag();

        StoreSite store = new StoreSite();
        store.setStoreCode("ST-" + t);
        store.setStoreName("Store " + label);
        store.setStoreType("GENERAL");
        store = storeSiteRepository.save(store);

        StorageLocation location = new StorageLocation();
        location.setStore(store);
        location.setLocationCode("LOC-" + t);
        location.setLocationName("Location " + label);
        location.setLocationType("RACK");
        location = storageLocationRepository.save(location);

        Uom uom = uomRepository.findByUomCodeIgnoreCase(UOM_CODE).orElseThrow();

        ItemCategory category = new ItemCategory();
        category.setCategoryCode("C-" + t);
        category.setCategoryName("Category " + label);
        category = itemCategoryRepository.save(category);

        Item item = new Item();
        item.setItemCode("I-" + t);
        item.setItemName(label + " " + t);
        item.setCategory(category);
        item.setBaseUom(uom);
        item.setItemType(itemType);
        item.setTrackingType(trackingType);
        item.setAssetRequired(assetRequired);
        item.setExpiryTracking(expiryTracking);
        item.setStandardRate(new BigDecimal("100.00"));
        item = itemRepository.save(item);

        return new Ctx(item, store, location, uom, t);
    }

    /**
     * Posts an opening/receipt quantity in through the single inventory writer, as admin.
     * @return the reference id stamped on the ledger row, for scoping assertions.
     */
    @Transactional
    public UUID receive(Ctx ctx, String qty, String unitCost, UUID postedBy) {
        StockTransaction txn = postingService.post(InventoryPostingRequest.builder()
                .transactionType("RECEIPT")
                .item(ctx.item())
                .store(ctx.store())
                .location(ctx.location())
                .quantityIn(new BigDecimal(qty))
                .unitCost(new BigDecimal(unitCost))
                .referenceType("TEST")
                .referenceId(UUID.randomUUID())
                .postedBy(postedBy)
                .build());
        return txn.getReferenceId();
    }
}
