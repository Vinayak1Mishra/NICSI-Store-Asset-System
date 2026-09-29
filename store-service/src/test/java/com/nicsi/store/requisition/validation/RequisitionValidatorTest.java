package com.nicsi.store.requisition.validation;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.Uom;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.requisition.dto.RequisitionDto;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequisitionValidatorTest {

    @Mock
    private ItemRepository itemRepository;

    private RequisitionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RequisitionValidator(itemRepository);
    }

    @Test
    @DisplayName("Should throw when items list is empty")
    void shouldThrowWhenEmptyItems() {
        RequisitionDto.CreateRequest req = new RequisitionDto.CreateRequest(
                UUID.randomUUID(), "DEP", "Department",
                null, null, null, null, null,
                "Office supplies", "NORMAL", LocalDate.now().plusDays(5),
                List.of()
        );

        assertThatThrownBy(() -> validator.validateCreate(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one item line");
    }

    @Test
    @DisplayName("Should throw when duplicate items are in lines")
    void shouldThrowWhenDuplicateItems() {
        UUID itemId = UUID.randomUUID();
        RequisitionDto.CreateRequest req = new RequisitionDto.CreateRequest(
                UUID.randomUUID(), "DEP", "Department",
                null, null, null, null, null,
                "Office supplies", "NORMAL", LocalDate.now().plusDays(5),
                List.of(
                        new RequisitionDto.LineRequest(itemId, BigDecimal.valueOf(5), null, null, null, null),
                        new RequisitionDto.LineRequest(itemId, BigDecimal.valueOf(10), null, null, null, null)
                )
        );

        assertThatThrownBy(() -> validator.validateCreate(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Duplicate item found");
    }

    @Test
    @DisplayName("Should throw when required-by date is in the past")
    void shouldThrowWhenRequiredDateInPast() {
        UUID itemId = UUID.randomUUID();
        RequisitionDto.CreateRequest req = new RequisitionDto.CreateRequest(
                UUID.randomUUID(), "DEP", "Department",
                null, null, null, null, null,
                "Office supplies", "NORMAL", LocalDate.now().minusDays(1),
                List.of(
                        new RequisitionDto.LineRequest(itemId, BigDecimal.valueOf(5), null, null, null, null)
                )
        );

        assertThatThrownBy(() -> validator.validateCreate(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Required-by date cannot be in the past");
    }

    @Test
    @DisplayName("Should throw when item is inactive")
    void shouldThrowWhenItemInactive() {
        UUID itemId = UUID.randomUUID();
        Item item = new Item();
        ReflectionTestUtils.setField(item, "id", itemId);
        ReflectionTestUtils.setField(item, "itemCode", "ITEM-001");
        ReflectionTestUtils.setField(item, "itemName", "Test Inactive Item");
        ReflectionTestUtils.setField(item, "active", false);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        RequisitionDto.CreateRequest req = new RequisitionDto.CreateRequest(
                UUID.randomUUID(), "DEP", "Department",
                null, null, null, null, null,
                "Office supplies", "NORMAL", LocalDate.now().plusDays(5),
                List.of(
                        new RequisitionDto.LineRequest(itemId, BigDecimal.valueOf(5), null, null, null, null)
                )
        );

        assertThatThrownBy(() -> validator.validateCreate(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is inactive and cannot be requisitioned");
    }

    @Test
    @DisplayName("Should throw when UOM does not allow decimals but decimal quantity is requested")
    void shouldThrowWhenDecimalNotAllowed() {
        Uom uom = new Uom();
        ReflectionTestUtils.setField(uom, "uomCode", "NOS");
        ReflectionTestUtils.setField(uom, "decimalAllowed", false);
        ReflectionTestUtils.setField(uom, "decimalScale", (short) 0);

        assertThatThrownBy(() -> validator.validateQuantityAgainstUom(new BigDecimal("2.5"), uom, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Decimal quantities are not allowed for UOM 'NOS'");
    }

    @Test
    @DisplayName("Should throw when UOM decimal scale is exceeded")
    void shouldThrowWhenDecimalScaleExceeded() {
        Uom uom = new Uom();
        ReflectionTestUtils.setField(uom, "uomCode", "MTR");
        ReflectionTestUtils.setField(uom, "decimalAllowed", true);
        ReflectionTestUtils.setField(uom, "decimalScale", (short) 2);

        assertThatThrownBy(() -> validator.validateQuantityAgainstUom(new BigDecimal("2.555"), uom, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds allowed scale of 2");
    }
}
