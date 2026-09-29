package com.nicsi.store.dev;

import com.nicsi.store.common.security.Permissions;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Provides 11 mock users for development and testing.
 * Each user has a stable deterministic UUID and the correct role-to-permission mapping
 * as defined in the Blueprint §8 access matrix.
 *
 * Active only under profile "dev". Deletable before production.
 */
@Component
@Profile(DevProfile.DEV)
public class MockUsers {

    private final Map<String, MockUser> users = new LinkedHashMap<>();

    public MockUsers() {
        initUsers();
    }

    private void initUsers() {
        // ALL permissions set for reference
        Set<String> allPerms = Set.of(
            Permissions.STORE_DASHBOARD_VIEW,
            Permissions.ITEM_VIEW, Permissions.ITEM_CREATE, Permissions.ITEM_UPDATE,
            Permissions.REQUISITION_CREATE, Permissions.REQUISITION_VIEW, Permissions.REQUISITION_APPROVE,
            Permissions.GRN_CREATE, Permissions.GRN_APPROVE, Permissions.GRN_POST,
            Permissions.INSPECTION_CREATE, Permissions.INSPECTION_APPROVE,
            Permissions.STOCK_VIEW, Permissions.STOCK_ADJUST, Permissions.STOCK_TRANSFER,
            Permissions.ISSUE_CREATE, Permissions.ISSUE_APPROVE, Permissions.ISSUE_POST,
            Permissions.RETURN_CREATE, Permissions.RETURN_RECEIVE, Permissions.RETURN_APPROVE, Permissions.RETURN_POST, Permissions.RETURN_VIEW,
            Permissions.TRANSFER_CREATE, Permissions.TRANSFER_APPROVE, Permissions.TRANSFER_DISPATCH, Permissions.TRANSFER_RECEIVE,
            Permissions.ASSET_VIEW, Permissions.ASSET_ASSIGN, Permissions.ASSET_TRANSFER,
            Permissions.REPAIR_MANAGE,
            Permissions.VERIFICATION_CREATE, Permissions.VERIFICATION_APPROVE,
            Permissions.CONDEMNATION_APPROVE,
            Permissions.DISPOSAL_APPROVE, Permissions.DISPOSAL_POST,
            Permissions.REPORT_VIEW, Permissions.REPORT_EXPORT,
            Permissions.AUDIT_VIEW,
            Permissions.STORE_ADMIN
        );

        // 1. Admin — all permissions
        users.put("admin", new MockUser(
            uuid("admin"), "admin", "System Administrator",
            Set.of("ROLE_ADMIN"), allPerms,
            uuid("dept-it"), "IT Department"
        ));

        // 2. Store Manager — approves GRN, issues, adjustments; views everything
        users.put("store.manager", new MockUser(
            uuid("store.manager"), "store.manager", "Store Manager",
            Set.of("ROLE_STORE_MANAGER"),
            Set.of(Permissions.STORE_DASHBOARD_VIEW,
                   Permissions.ITEM_VIEW, Permissions.ITEM_CREATE, Permissions.ITEM_UPDATE,
                   Permissions.REQUISITION_VIEW, Permissions.REQUISITION_APPROVE,
                   Permissions.GRN_CREATE, Permissions.GRN_APPROVE, Permissions.GRN_POST,
                   Permissions.INSPECTION_CREATE, Permissions.INSPECTION_APPROVE,
                   Permissions.STOCK_VIEW, Permissions.STOCK_ADJUST, Permissions.STOCK_TRANSFER,
                   Permissions.ISSUE_CREATE, Permissions.ISSUE_APPROVE, Permissions.ISSUE_POST,
                   Permissions.ASSET_VIEW, Permissions.ASSET_ASSIGN, Permissions.ASSET_TRANSFER,
                   Permissions.REPAIR_MANAGE,
                   Permissions.VERIFICATION_CREATE, Permissions.VERIFICATION_APPROVE,
                   Permissions.CONDEMNATION_APPROVE,
                   Permissions.DISPOSAL_APPROVE, Permissions.DISPOSAL_POST,
                   Permissions.REPORT_VIEW, Permissions.REPORT_EXPORT,
                   Permissions.AUDIT_VIEW),
            uuid("dept-admin"), "Admin Department"
        ));

        // 3. Store Officer — operational permissions, no approval
        users.put("store.officer", new MockUser(
            uuid("store.officer"), "store.officer", "Store Officer",
            Set.of("ROLE_STORE_OFFICER"),
            Set.of(Permissions.STORE_DASHBOARD_VIEW,
                   Permissions.ITEM_VIEW, Permissions.ITEM_CREATE, Permissions.ITEM_UPDATE,
                   Permissions.REQUISITION_VIEW, Permissions.REQUISITION_APPROVE,
                   Permissions.GRN_CREATE,
                   Permissions.INSPECTION_CREATE,
                   Permissions.STOCK_VIEW, Permissions.STOCK_TRANSFER,
                   Permissions.ISSUE_CREATE,
                   Permissions.ASSET_VIEW, Permissions.ASSET_ASSIGN,
                   Permissions.VERIFICATION_CREATE,
                   Permissions.REPORT_VIEW),
            uuid("dept-admin"), "Admin Department"
        ));

        // 4. Store Operator — create GRN, issues, basic view
        users.put("store.operator", new MockUser(
            uuid("store.operator"), "store.operator", "Store Operator",
            Set.of("ROLE_STORE_OPERATOR"),
            Set.of(Permissions.STORE_DASHBOARD_VIEW,
                   Permissions.ITEM_VIEW,
                   Permissions.REQUISITION_CREATE, Permissions.REQUISITION_VIEW,
                   Permissions.GRN_CREATE,
                   Permissions.INSPECTION_CREATE,
                   Permissions.STOCK_VIEW,
                   Permissions.ISSUE_CREATE,
                   Permissions.ASSET_VIEW,
                   Permissions.VERIFICATION_CREATE,
                   Permissions.REPORT_VIEW),
            uuid("dept-admin"), "Admin Department"
        ));

        // 5. Procurement Officer
        users.put("procurement.officer", new MockUser(
            uuid("procurement.officer"), "procurement.officer", "Procurement Officer",
            Set.of("ROLE_PROCUREMENT"),
            Set.of(Permissions.STORE_DASHBOARD_VIEW,
                   Permissions.ITEM_VIEW,
                   Permissions.GRN_CREATE,
                   Permissions.STOCK_VIEW,
                   Permissions.REPORT_VIEW),
            uuid("dept-finance"), "Finance Department"
        ));

        // 6. Finance Officer
        users.put("finance.officer", new MockUser(
            uuid("finance.officer"), "finance.officer", "Finance Officer",
            Set.of("ROLE_FINANCE"),
            Set.of(Permissions.STORE_DASHBOARD_VIEW,
                   Permissions.ITEM_VIEW,
                   Permissions.STOCK_VIEW,
                   Permissions.REPORT_VIEW, Permissions.REPORT_EXPORT),
            uuid("dept-finance"), "Finance Department"
        ));

        // 7. Asset Manager
        users.put("asset.manager", new MockUser(
            uuid("asset.manager"), "asset.manager", "Asset Manager",
            Set.of("ROLE_ASSET_MANAGER"),
            Set.of(Permissions.STORE_DASHBOARD_VIEW,
                   Permissions.ITEM_VIEW,
                   Permissions.ASSET_VIEW, Permissions.ASSET_ASSIGN, Permissions.ASSET_TRANSFER,
                   Permissions.REPAIR_MANAGE,
                   Permissions.STOCK_VIEW,
                   Permissions.REPORT_VIEW, Permissions.REPORT_EXPORT),
            uuid("dept-it"), "IT Department"
        ));

        // 8. Auditor — read-only across all modules
        users.put("auditor", new MockUser(
            uuid("auditor"), "auditor", "Auditor",
            Set.of("ROLE_AUDITOR"),
            Set.of(Permissions.STORE_DASHBOARD_VIEW,
                   Permissions.ITEM_VIEW,
                   Permissions.REQUISITION_VIEW,
                   Permissions.STOCK_VIEW,
                   Permissions.ASSET_VIEW,
                   Permissions.REPORT_VIEW, Permissions.REPORT_EXPORT,
                   Permissions.AUDIT_VIEW),
            uuid("dept-finance"), "Finance Department"
        ));

        // 9. HOD — approves requisitions
        users.put("hod.dept1", new MockUser(
            uuid("hod.dept1"), "hod.dept1", "HOD IT Department",
            Set.of("ROLE_HOD"),
            Set.of(Permissions.STORE_DASHBOARD_VIEW,
                   Permissions.ITEM_VIEW,
                   Permissions.REQUISITION_CREATE, Permissions.REQUISITION_VIEW, Permissions.REQUISITION_APPROVE,
                   Permissions.STOCK_VIEW,
                   Permissions.ASSET_VIEW,
                   Permissions.REPORT_VIEW),
            uuid("dept-it"), "IT Department"
        ));

        // 10. Employee — creates requisitions, views own
        users.put("employee.john", new MockUser(
            uuid("employee.john"), "employee.john", "John Employee",
            Set.of("ROLE_EMPLOYEE"),
            Set.of(Permissions.STORE_DASHBOARD_VIEW,
                   Permissions.ITEM_VIEW,
                   Permissions.REQUISITION_CREATE, Permissions.REQUISITION_VIEW,
                   Permissions.ASSET_VIEW),
            uuid("dept-it"), "IT Department"
        ));

        // 11. Competent Authority — condemnation and disposal approvals
        users.put("competent.authority", new MockUser(
            uuid("competent.authority"), "competent.authority", "Competent Authority",
            Set.of("ROLE_COMPETENT_AUTHORITY"),
            Set.of(Permissions.STORE_DASHBOARD_VIEW,
                   Permissions.ITEM_VIEW,
                   Permissions.STOCK_VIEW,
                   Permissions.ASSET_VIEW,
                   Permissions.CONDEMNATION_APPROVE,
                   Permissions.DISPOSAL_APPROVE, Permissions.DISPOSAL_POST,
                   Permissions.REPORT_VIEW,
                   Permissions.AUDIT_VIEW),
            uuid("dept-admin"), "Admin Department"
        ));
    }

    private UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes());
    }

    public MockUser getByUsername(String username) {
        return users.get(username);
    }

    public Collection<MockUser> getAllUsers() {
        return Collections.unmodifiableCollection(users.values());
    }

    public Map<String, MockUser> getAllUsersMap() {
        return Collections.unmodifiableMap(users);
    }
}
