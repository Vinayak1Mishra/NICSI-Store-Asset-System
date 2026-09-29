package com.nicsi.store.master.validation;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.repository.StorageLocationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class HierarchyCycleValidator {

    public void validate(UUID locationId, UUID parentLocationId, UUID storeId, StorageLocationRepository repo) {
        if (parentLocationId == null) {
            return;
        }

        if (parentLocationId.equals(locationId)) {
            throw new BusinessException("SELF_REFERENCING_PARENT", "A storage location cannot be its own parent", HttpStatus.BAD_REQUEST);
        }

        StorageLocation parent = repo.findById(parentLocationId)
                .orElseThrow(() -> new BusinessException("PARENT_NOT_FOUND", "Parent location not found", HttpStatus.NOT_FOUND));

        if (!parent.getStore().getId().equals(storeId)) {
            throw new BusinessException("CROSS_STORE_PARENT", "Parent location must belong to the same store", HttpStatus.BAD_REQUEST);
        }

        if (locationId != null) {
            UUID currentParentId = parent.getId();
            int depth = 0;
            while (currentParentId != null) {
                if (depth > 20) {
                    throw new BusinessException("MAX_HIERARCHY_DEPTH_EXCEEDED", "Maximum hierarchy depth of 20 exceeded", HttpStatus.BAD_REQUEST);
                }
                if (currentParentId.equals(locationId)) {
                    throw new BusinessException("CIRCULAR_HIERARCHY", "Circular hierarchy detected", HttpStatus.BAD_REQUEST);
                }

                StorageLocation currentParent = repo.findById(currentParentId).orElse(null);
                if (currentParent == null || currentParent.getParentLocation() == null) {
                    break;
                }
                currentParentId = currentParent.getParentLocation().getId();
                depth++;
            }
        }
    }
}
