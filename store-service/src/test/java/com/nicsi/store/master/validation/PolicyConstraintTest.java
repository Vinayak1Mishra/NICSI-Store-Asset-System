package com.nicsi.store.master.validation;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.master.service.ItemStorePolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyConstraintTest {

    private void invokeValidateMinMax(BigDecimal min, BigDecimal max) throws Throwable {
        ItemStorePolicyService service = new ItemStorePolicyService(null, null, null, null, null);
        Method method = ItemStorePolicyService.class.getDeclaredMethod("validateMinMax", BigDecimal.class, BigDecimal.class);
        method.setAccessible(true);
        try {
            method.invoke(service, min, max);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    @DisplayName("maxStockQty less than minStockQty throws INVALID_MIN_MAX")
    void testMaxLessThanMinThrows() {
        assertThatThrownBy(() -> invokeValidateMinMax(new BigDecimal("100"), new BigDecimal("50")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("INVALID_MIN_MAX"));
    }

    @Test
    @DisplayName("maxStockQty greater than minStockQty succeeds")
    void testMaxGreaterThanMinSucceeds() {
        assertThatCode(() -> invokeValidateMinMax(new BigDecimal("50"), new BigDecimal("100")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("maxStockQty equal to minStockQty succeeds")
    void testMaxEqualToMinSucceeds() {
        assertThatCode(() -> invokeValidateMinMax(new BigDecimal("50"), new BigDecimal("50")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null maxStockQty succeeds")
    void testNullMaxSucceeds() {
        assertThatCode(() -> invokeValidateMinMax(new BigDecimal("50"), null))
                .doesNotThrowAnyException();
    }
}
