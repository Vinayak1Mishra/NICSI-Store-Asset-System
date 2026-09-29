export interface BalanceResponse {
  id: string;
  itemId: string;
  itemCode: string;
  itemName: string;
  uomCode: string;
  categoryId?: string;
  categoryName?: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  locationId: string;
  locationCode: string;
  locationName: string;
  lotId?: string;
  lotNumber?: string;
  onHandQty: number;
  reservedQty: number;
  availableQty: number;
  avgUnitCost: number;
  inventoryValue: number;
  isLowStock: boolean;
  updatedAt: string;
}

export interface LedgerResponse {
  id: string;
  transactionNo: string;
  transactionTime: string;
  transactionType: string;
  itemId: string;
  itemCode: string;
  itemName: string;
  uomCode: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  locationId: string;
  locationCode: string;
  locationName: string;
  lotNumber?: string;
  quantityIn?: number;
  quantityOut?: number;
  unitCost: number;
  totalCost: number;
  referenceType?: string;
  referenceNo?: string;
  remarks?: string;
  postedBy?: string;
  postedAt?: string;
}

export interface LowStockResponse {
  id?: string;
  itemId: string;
  itemCode: string;
  itemName: string;
  uomCode: string;
  categoryName: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  totalOnHand: number;
  minStockLevel: number;
  reorderLevel: number;
  deficitQty: number;
  suggestedReorderQty: number;
}

export interface LedgerMismatchDiscrepancy {
  itemId: string;
  itemCode: string;
  itemName: string;
  storeId: string;
  storeCode: string;
  locationId: string;
  locationCode: string;
  lotId?: string;
  lotNumber?: string;
  ledgerBalance: number;
  storedBalance: number;
  variance: number;
}

export interface AssetMismatchDiscrepancy {
  itemId: string;
  itemCode: string;
  itemName: string;
  storeId: string;
  storeCode: string;
  locationId: string;
  locationCode: string;
  availableStockBalance: number;
  availableAssetCount: number;
  variance: number;
}

export interface ReconciliationResponse {
  reconciled: boolean;
  totalLedgerMismatchCount: number;
  totalAssetMismatchCount: number;
  ledgerBalanceDiscrepancies: LedgerMismatchDiscrepancy[];
  assetCountDiscrepancies: AssetMismatchDiscrepancy[];
  checkedAt: string;
}
