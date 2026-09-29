export interface AssetSummaryResponse {
  id: string;
  assetCode: string;
  qrCodeValue: string;
  serialNumber?: string;
  itemId: string;
  itemCode: string;
  itemName: string;
  storeId: string;
  storeCode: string;
  locationId: string;
  locationCode: string;
  assetStatus: string;
  conditionStatus: string;
  purchaseCost?: number;
  purchaseDate?: string;
  currentCustodianName?: string;
}

export interface AssetResponse {
  id: string;
  assetCode: string;
  qrCodeValue: string;
  serialNumber?: string;
  itemId: string;
  itemCode: string;
  itemName: string;
  categoryCode?: string;
  categoryName?: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  locationId: string;
  locationCode: string;
  locationName: string;
  grnItemId?: string;
  invoiceNumberSnapshot?: string;
  poNumberSnapshot?: string;
  manufacturer?: string;
  modelNumber?: string;
  configuration?: string;
  purchaseDate?: string;
  purchaseCost?: number;
  warrantyStartDate?: string;
  warrantyEndDate?: string;
  capitalizationRef?: string;
  assetStatus: string;
  conditionStatus: string;
  remarks?: string;
  version: number;
}

export interface AssetAssignRequest {
  assignmentType: 'EMPLOYEE' | 'DEPARTMENT' | 'PROJECT' | 'LOCATION';
  assigneeUserId?: string | null;
  assigneeNameSnapshot?: string;
  departmentId?: string | null;
  projectId?: string | null;
  locationId?: string | null;
  remarks?: string;
}

export interface AssetTransferRequest {
  toStoreId?: string | null;
  toLocationId?: string | null;
  assignmentType?: 'EMPLOYEE' | 'DEPARTMENT' | 'PROJECT' | 'LOCATION';
  toCustodianUserId?: string | null;
  toCustodianNameSnapshot?: string;
  toDepartmentId?: string | null;
  toProjectId?: string | null;
  remarks?: string;
}

export interface AssetReturnRequest {
  returnStoreId?: string | null;
  returnLocationId?: string | null;
  conditionStatus?: string;
  disposition?: 'RESTOCK' | 'REPAIR' | 'CONDEMNATION' | 'SCRAP';
  remarks?: string;
}

export interface AssetRepairRequest {
  action: 'SEND_TO_REPAIR' | 'RETURN_FROM_REPAIR';
  vendorId?: string | null;
  vendorNameSnapshot?: string;
  complaintDetail?: string;
  warrantyClaim?: boolean;
  repairCost?: number;
  partsReplaced?: string;
  finalCondition?: string;
  diagnosis?: string;
  remarks?: string;
}

export interface AssetDisposeRequest {
  disposalMethod: 'AUCTION' | 'E_WASTE' | 'SCRAP' | 'RETURN_TO_OEM' | 'TRANSFER' | 'OTHER';
  purchaserVendorId?: string | null;
  purchaserNameSnapshot?: string;
  saleAmount?: number;
  certificateNumber?: string;
  remarks?: string;
}
