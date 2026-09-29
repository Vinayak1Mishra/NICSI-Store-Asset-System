package com.nicsi.store.common.security;

/**
 * Constants class defining all permission strings used in the application.
 * These strings are used for role-based access control (RBAC).
 */
public final class Permissions {
    private Permissions() {}
    
    // Dashboard
    public static final String STORE_DASHBOARD_VIEW = "STORE_DASHBOARD_VIEW";
    
    // Item
    public static final String ITEM_VIEW = "ITEM_VIEW";
    public static final String ITEM_CREATE = "ITEM_CREATE";
    public static final String ITEM_UPDATE = "ITEM_UPDATE";
    
    // Requisition
    public static final String REQUISITION_CREATE = "REQUISITION_CREATE";
    public static final String REQUISITION_VIEW = "REQUISITION_VIEW";
    public static final String REQUISITION_APPROVE = "REQUISITION_APPROVE";
    
    // GRN
    public static final String GRN_CREATE = "GRN_CREATE";
    public static final String GRN_APPROVE = "GRN_APPROVE";
    public static final String GRN_POST = "GRN_POST";
    
    // Inspection
    public static final String INSPECTION_CREATE = "INSPECTION_CREATE";
    public static final String INSPECTION_APPROVE = "INSPECTION_APPROVE";
    
    // Stock
    public static final String STOCK_VIEW = "STOCK_VIEW";
    public static final String STOCK_ADJUST = "STOCK_ADJUST";
    public static final String STOCK_TRANSFER = "STOCK_TRANSFER";
    
    // Issue
    public static final String ISSUE_CREATE = "ISSUE_CREATE";
    public static final String ISSUE_APPROVE = "ISSUE_APPROVE";
    public static final String ISSUE_POST = "ISSUE_POST";

    // Return
    public static final String RETURN_CREATE = "RETURN_CREATE";
    public static final String RETURN_RECEIVE = "RETURN_RECEIVE";
    public static final String RETURN_APPROVE = "RETURN_APPROVE";
    public static final String RETURN_POST = "RETURN_POST";
    public static final String RETURN_VIEW = "RETURN_VIEW";

    // Transfer
    public static final String TRANSFER_CREATE = "TRANSFER_CREATE";
    public static final String TRANSFER_APPROVE = "TRANSFER_APPROVE";
    public static final String TRANSFER_DISPATCH = "TRANSFER_DISPATCH";
    public static final String TRANSFER_RECEIVE = "TRANSFER_RECEIVE";
    
    // Asset
    public static final String ASSET_VIEW = "ASSET_VIEW";
    public static final String ASSET_ASSIGN = "ASSET_ASSIGN";
    public static final String ASSET_TRANSFER = "ASSET_TRANSFER";
    
    // Repair
    public static final String REPAIR_MANAGE = "REPAIR_MANAGE";
    
    // Verification
    public static final String VERIFICATION_CREATE = "VERIFICATION_CREATE";
    public static final String VERIFICATION_APPROVE = "VERIFICATION_APPROVE";
    
    // Condemnation
    public static final String CONDEMNATION_APPROVE = "CONDEMNATION_APPROVE";
    
    // Disposal
    public static final String DISPOSAL_APPROVE = "DISPOSAL_APPROVE";
    public static final String DISPOSAL_POST = "DISPOSAL_POST";
    
    // Reports
    public static final String REPORT_VIEW = "REPORT_VIEW";
    public static final String REPORT_EXPORT = "REPORT_EXPORT";
    
    // Audit
    public static final String AUDIT_VIEW = "AUDIT_VIEW";
    
    // Admin
    public static final String STORE_ADMIN = "STORE_ADMIN";
}
