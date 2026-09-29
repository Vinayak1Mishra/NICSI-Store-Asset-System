package com.nicsi.store.master.validation;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.master.repository.StorageLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class LocationHierarchyValidatorTest {

    private HierarchyCycleValidator validator;
    private StorageLocationRepository repo;

    private UUID storeId;
    private StoreSite store;

    @BeforeEach
    void setUp() {
        validator = new HierarchyCycleValidator();
        repo = Mockito.mock(StorageLocationRepository.class);

        storeId = UUID.randomUUID();
        store = new StoreSite();
        store.setId(storeId);
    }

    @Test
    @DisplayName("Null parent location passes validation")
    void testNullParentPasses() {
        assertThatCode(() -> validator.validate(UUID.randomUUID(), null, storeId, repo))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Self-referencing parent throws SELF_REFERENCING_PARENT")
    void testSelfParentThrows() {
        UUID locId = UUID.randomUUID();
        assertThatThrownBy(() -> validator.validate(locId, locId, storeId, repo))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("SELF_REFERENCING_PARENT"));
    }

    @Test
    @DisplayName("Parent from different store throws CROSS_STORE_PARENT")
    void testCrossStoreParentThrows() {
        UUID locId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        StoreSite otherStore = new StoreSite();
        otherStore.setId(UUID.randomUUID());

        StorageLocation parentLoc = new StorageLocation();
        parentLoc.setId(parentId);
        parentLoc.setStore(otherStore);

        when(repo.findById(parentId)).thenReturn(Optional.of(parentLoc));

        assertThatThrownBy(() -> validator.validate(locId, parentId, storeId, repo))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("CROSS_STORE_PARENT"));
    }

    @Test
    @DisplayName("Circular hierarchy A -> B -> C -> A throws CIRCULAR_HIERARCHY")
    void testCircularHierarchyThrows() {
        UUID locA = UUID.randomUUID();
        UUID locB = UUID.randomUUID();
        UUID locC = UUID.randomUUID();

        StorageLocation locationC = new StorageLocation();
        locationC.setId(locC);
        locationC.setStore(store);

        StorageLocation locationB = new StorageLocation();
        locationB.setId(locB);
        locationB.setStore(store);
        locationB.setParentLocation(locationC);

        // When locC points to locA, and we try to set locA's parent to locB
        StorageLocation locationA = new StorageLocation();
        locationA.setId(locA);
        locationA.setStore(store);
        locationC.setParentLocation(locationA);

        when(repo.findById(locB)).thenReturn(Optional.of(locationB));
        when(repo.findById(locC)).thenReturn(Optional.of(locationC));
        when(repo.findById(locA)).thenReturn(Optional.of(locationA));

        // Validating setting locB as parent of locA
        assertThatThrownBy(() -> validator.validate(locA, locB, storeId, repo))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("CIRCULAR_HIERARCHY"));
    }
}
