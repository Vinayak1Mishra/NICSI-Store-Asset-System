package com.nicsi.store.grn.validation;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.grn.dto.GrnDto;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.master.domain.Uom;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.master.repository.StorageLocationRepository;
import com.nicsi.store.master.repository.StoreSiteRepository;
import com.nicsi.store.procurementref.domain.PurchaseOrderItemRef;
import com.nicsi.store.procurementref.domain.PurchaseOrderRef;
import com.nicsi.store.procurementref.repository.PurchaseOrderItemRefRepository;
import com.nicsi.store.procurementref.repository.PurchaseOrderRefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrnValidatorTest {

    @Mock
    private StoreSiteRepository storeSiteRepository;
    @Mock
    private StorageLocationRepository storageLocationRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private PurchaseOrderRefRepository purchaseOrderRefRepository;
    @Mock
    private PurchaseOrderItemRefRepository purchaseOrderItemRefRepository;

    private GrnValidator validator;

    @BeforeEach
    void setUp() {
        validator = new GrnValidator(
                storeSiteRepository,
                storageLocationRepository,
                itemRepository,
                purchaseOrderRefRepository,
                purchaseOrderItemRefRepository
        );
    }

    @Test
    @DisplayName("Should throw when store is missing or inactive")
    void shouldThrowWhenStoreMissingOrInactive() {
        UUID storeId = UUID.randomUUID();
        when(storeSiteRepository.findById(storeId)).thenReturn(Optional.empty());

        GrnDto.CreateRequest req = new GrnDto.CreateRequest(
                LocalDate.now(), storeId, null, null, null,
                "INV-1", LocalDate.now(), "CH-1", LocalDate.now(), null,
                List.of(new GrnDto.CreateItemRequest(null, UUID.randomUUID(), BigDecimal.ONE, BigDecimal.TEN, UUID.randomUUID(), null, null, null, null))
        );

        assertThatThrownBy(() -> validator.validateCreate(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Store site not found");
    }

    @Test
    @DisplayName("Should throw when location does not belong to the target store")
    void shouldThrowWhenLocationStoreMismatch() {
        UUID storeId1 = UUID.randomUUID();
        UUID storeId2 = UUID.randomUUID();

        StoreSite store1 = new StoreSite();
        ReflectionTestUtils.setField(store1, "id", storeId1);
        store1.setStoreCode("STORE-1");
        store1.setActive(true);

        StoreSite store2 = new StoreSite();
        ReflectionTestUtils.setField(store2, "id", storeId2);
        store2.setStoreCode("STORE-2");

        UUID locId = UUID.randomUUID();
        StorageLocation loc = new StorageLocation();
        ReflectionTestUtils.setField(loc, "id", locId);
        loc.setLocationCode("LOC-B");
        loc.setStore(store2);
        loc.setActive(true);

        when(storeSiteRepository.findById(storeId1)).thenReturn(Optional.of(store1));
        when(storageLocationRepository.findById(locId)).thenReturn(Optional.of(loc));

        GrnDto.CreateRequest req = new GrnDto.CreateRequest(
                LocalDate.now(), storeId1, null, null, null,
                "INV-1", LocalDate.now(), "CH-1", LocalDate.now(), null,
                List.of(new GrnDto.CreateItemRequest(null, UUID.randomUUID(), BigDecimal.ONE, BigDecimal.TEN, locId, null, null, null, null))
        );

        assertThatThrownBy(() -> validator.validateCreate(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong to store");
    }

    @Test
    @DisplayName("Should throw when decimal value is given to non-decimal UOM")
    void shouldThrowWhenDecimalNotAllowed() {
        UUID storeId = UUID.randomUUID();
        StoreSite store = new StoreSite();
        ReflectionTestUtils.setField(store, "id", storeId);
        store.setStoreCode("STORE-1");
        store.setActive(true);

        UUID locId = UUID.randomUUID();
        StorageLocation loc = new StorageLocation();
        ReflectionTestUtils.setField(loc, "id", locId);
        loc.setStore(store);
        loc.setActive(true);

        Uom uom = new Uom();
        uom.setUomCode("NOS");
        uom.setDecimalAllowed(false);

        UUID itemId = UUID.randomUUID();
        Item item = new Item();
        ReflectionTestUtils.setField(item, "id", itemId);
        item.setItemCode("ITEM-1");
        item.setBaseUom(uom);
        item.setActive(true);

        when(storeSiteRepository.findById(storeId)).thenReturn(Optional.of(store));
        when(storageLocationRepository.findById(locId)).thenReturn(Optional.of(loc));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        GrnDto.CreateRequest req = new GrnDto.CreateRequest(
                LocalDate.now(), storeId, null, null, null,
                "INV-1", LocalDate.now(), "CH-1", LocalDate.now(), null,
                List.of(new GrnDto.CreateItemRequest(null, itemId, new BigDecimal("2.5"), BigDecimal.TEN, locId, null, null, null, null))
        );

        assertThatThrownBy(() -> validator.validateCreate(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Decimal values not allowed");
    }

    @Test
    @DisplayName("Should throw when expiry date is missing for item with expiry tracking")
    void shouldThrowWhenExpiryMissingForTrackedItem() {
        UUID storeId = UUID.randomUUID();
        StoreSite store = new StoreSite();
        ReflectionTestUtils.setField(store, "id", storeId);
        store.setActive(true);

        UUID locId = UUID.randomUUID();
        StorageLocation loc = new StorageLocation();
        ReflectionTestUtils.setField(loc, "id", locId);
        loc.setStore(store);
        loc.setActive(true);

        Uom uom = new Uom();
        uom.setUomCode("NOS");
        uom.setDecimalAllowed(false);

        UUID itemId = UUID.randomUUID();
        Item item = new Item();
        ReflectionTestUtils.setField(item, "id", itemId);
        item.setItemCode("MED-1");
        item.setBaseUom(uom);
        item.setExpiryTracking(true);
        item.setActive(true);

        when(storeSiteRepository.findById(storeId)).thenReturn(Optional.of(store));
        when(storageLocationRepository.findById(locId)).thenReturn(Optional.of(loc));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        GrnDto.CreateRequest req = new GrnDto.CreateRequest(
                LocalDate.now(), storeId, null, null, null,
                "INV-1", LocalDate.now(), "CH-1", LocalDate.now(), null,
                List.of(new GrnDto.CreateItemRequest(null, itemId, new BigDecimal("5"), BigDecimal.TEN, locId, null, null, null, null))
        );

        assertThatThrownBy(() -> validator.validateCreate(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Expiry date is mandatory");
    }

    @Test
    @DisplayName("Should throw when received quantity exceeds PO ordered quantity")
    void shouldThrowWhenPoQtyExceeded() {
        UUID storeId = UUID.randomUUID();
        StoreSite store = new StoreSite();
        ReflectionTestUtils.setField(store, "id", storeId);
        store.setActive(true);

        UUID locId = UUID.randomUUID();
        StorageLocation loc = new StorageLocation();
        ReflectionTestUtils.setField(loc, "id", locId);
        loc.setStore(store);
        loc.setActive(true);

        UUID poId = UUID.randomUUID();
        PurchaseOrderRef po = new PurchaseOrderRef();
        ReflectionTestUtils.setField(po, "id", poId);
        po.setPoNumber("PO-2026-001");

        UUID poItemId = UUID.randomUUID();
        PurchaseOrderItemRef poItem = new PurchaseOrderItemRef();
        ReflectionTestUtils.setField(poItem, "id", poItemId);
        poItem.setPurchaseOrderRef(po);
        poItem.setPoLineNo(1);
        poItem.setOrderedQty(new BigDecimal("10"));
        poItem.setReceivedQty(new BigDecimal("8"));

        Uom uom = new Uom();
        uom.setUomCode("NOS");
        uom.setDecimalAllowed(false);

        UUID itemId = UUID.randomUUID();
        Item item = new Item();
        ReflectionTestUtils.setField(item, "id", itemId);
        item.setItemCode("ITEM-1");
        item.setBaseUom(uom);
        item.setActive(true);
        poItem.setItem(item);

        when(storeSiteRepository.findById(storeId)).thenReturn(Optional.of(store));
        when(purchaseOrderRefRepository.findById(poId)).thenReturn(Optional.of(po));
        when(storageLocationRepository.findById(locId)).thenReturn(Optional.of(loc));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(purchaseOrderItemRefRepository.findById(poItemId)).thenReturn(Optional.of(poItem));

        // Attempt to receive 5 when 8 already received and ordered is 10 (8 + 5 = 13 > 10)
        GrnDto.CreateRequest req = new GrnDto.CreateRequest(
                LocalDate.now(), storeId, poId, null, null,
                "INV-1", LocalDate.now(), "CH-1", LocalDate.now(), null,
                List.of(new GrnDto.CreateItemRequest(poItemId, itemId, new BigDecimal("5"), BigDecimal.TEN, locId, null, null, null, null))
        );

        assertThatThrownBy(() -> validator.validateCreate(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds ordered quantity");
    }
}
