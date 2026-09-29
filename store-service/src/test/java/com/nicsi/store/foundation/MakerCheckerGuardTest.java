package com.nicsi.store.foundation;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.rules.MakerCheckerGuard;
import com.nicsi.store.common.rules.MakerCheckerOperation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MakerCheckerGuardTest {

    @Test
    void testSameUserThrows() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> MakerCheckerGuard.validate(userId, userId, MakerCheckerOperation.OPENING_BALANCE))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo("MAKER_CHECKER_VIOLATION");
                });
    }

    @Test
    void testDifferentUsersPasses() {
        UUID makerId = UUID.randomUUID();
        UUID checkerId = UUID.randomUUID();
        assertThatCode(() -> MakerCheckerGuard.validate(makerId, checkerId, MakerCheckerOperation.OPENING_BALANCE))
                .doesNotThrowAnyException();
    }

    @Test
    void testNullMakerThrows() {
        UUID checkerId = UUID.randomUUID();
        assertThatThrownBy(() -> MakerCheckerGuard.validate(null, checkerId, MakerCheckerOperation.OPENING_BALANCE))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo("MAKER_CHECKER_INVALID");
                });
    }

    @Test
    void testNullCheckerThrows() {
        UUID makerId = UUID.randomUUID();
        assertThatThrownBy(() -> MakerCheckerGuard.validate(makerId, null, MakerCheckerOperation.OPENING_BALANCE))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo("MAKER_CHECKER_INVALID");
                });
    }

    @Test
    void testAllOperations() {
        UUID makerId = UUID.randomUUID();
        for (MakerCheckerOperation op : MakerCheckerOperation.values()) {
            assertThatThrownBy(() -> MakerCheckerGuard.validate(makerId, makerId, op))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getErrorCode()).isEqualTo("MAKER_CHECKER_VIOLATION");
                    });
        }
    }
}
