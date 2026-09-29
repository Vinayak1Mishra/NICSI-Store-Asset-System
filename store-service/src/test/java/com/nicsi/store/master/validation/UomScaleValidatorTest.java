package com.nicsi.store.master.validation;

import com.nicsi.store.master.dto.UomDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UomScaleValidatorTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid scale between 0 and 6 has no constraint violations")
    void testValidDecimalScale() {
        UomDto.CreateRequest req = new UomDto.CreateRequest(
                "KG", "Kilogram", "WEIGHT", true, 3, "Kilogram unit"
        );
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    @DisplayName("Decimal scale less than 0 violates @Min(0)")
    void testScaleNegativeThrows() {
        UomDto.CreateRequest req = new UomDto.CreateRequest(
                "KG", "Kilogram", "WEIGHT", true, -1, "Kilogram unit"
        );
        var violations = validator.validate(req);
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).contains("0");
    }

    @Test
    @DisplayName("Decimal scale greater than 6 violates @Max(6)")
    void testScaleGreaterThan6Throws() {
        UomDto.CreateRequest req = new UomDto.CreateRequest(
                "KG", "Kilogram", "WEIGHT", true, 7, "Kilogram unit"
        );
        var violations = validator.validate(req);
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).contains("6");
    }
}
