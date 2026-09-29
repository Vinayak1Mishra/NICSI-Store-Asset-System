package com.nicsi.store.common.rules;

import com.nicsi.store.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MakerCheckerGuard {

    /**
     * Asserts that the maker and checker are different users.
     * Throws BusinessException if they are the same or if either is null.
     */
    public void assertDifferentUser(UUID makerId, UUID checkerId, MakerCheckerOperation operation) {
        validate(makerId, checkerId, operation);
    }

    /**
     * Static validation method.
     */
    public static void validate(UUID makerId, UUID checkerId, MakerCheckerOperation operation) {
        if (makerId == null || checkerId == null) {
            throw new BusinessException(
                "MAKER_CHECKER_INVALID",
                "Both maker and checker must be identified for " + operation,
                HttpStatus.BAD_REQUEST
            );
        }
        if (makerId.equals(checkerId)) {
            throw new BusinessException(
                "MAKER_CHECKER_VIOLATION",
                "Maker and checker must be different users for " + operation + ". The same user cannot both create and approve this transaction.",
                HttpStatus.FORBIDDEN
            );
        }
    }
}
