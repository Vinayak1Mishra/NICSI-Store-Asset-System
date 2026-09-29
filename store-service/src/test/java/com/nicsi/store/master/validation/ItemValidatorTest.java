package com.nicsi.store.master.validation;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.master.dto.ItemDto;
import com.nicsi.store.master.service.ItemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemValidatorTest {

    private void invokeValidation(Boolean assetRequired, String trackingType) throws Throwable {
        ItemService service = new ItemService(null, null, null, null, null, null);
        Method method = ItemService.class.getDeclaredMethod("validateBusinessRules", Boolean.class, String.class);
        method.setAccessible(true);
        try {
            method.invoke(service, assetRequired, trackingType);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUANTITY", "LOT", "LICENSE"})
    @DisplayName("asset_required=true with non-SERIAL tracking type must throw BusinessException")
    void testAssetRequiredNonSerialThrows(String trackingType) {
        assertThatThrownBy(() -> invokeValidation(true, trackingType))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SERIAL")
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("INVALID_TRACKING_TYPE"));
    }

    @Test
    @DisplayName("asset_required=true with SERIAL tracking type succeeds")
    void testAssetRequiredSerialSucceeds() {
        assertThatCode(() -> invokeValidation(true, "SERIAL"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUANTITY", "LOT", "LICENSE", "SERIAL"})
    @DisplayName("asset_required=false with any tracking type succeeds")
    void testAssetNotRequiredAnyTrackingTypeSucceeds(String trackingType) {
        assertThatCode(() -> invokeValidation(false, trackingType))
                .doesNotThrowAnyException();
    }
}
