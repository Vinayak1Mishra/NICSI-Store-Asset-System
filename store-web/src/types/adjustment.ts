export interface StockAdjustmentItemRequest {
  itemId: string;
  locationId: string;
  lotId?: string | null;
  direction: 'IN' | 'OUT';
  quantity: number;
  unitCost: number;
  remarks?: string;
}

export interface CreateStockAdjustmentRequest {
  storeId: string;
  reasonCode: string;
  remarks?: string;
  items: StockAdjustmentItemRequest[];
}

export interface StockAdjustmentItemResponse {
  id: string;
  lineNo: number;
  itemId: string;
  itemCode: string;
  itemName: string;
  uomCode: string;
  locationId: string;
  locationCode: string;
  locationName: string;
  lotId?: string;
  lotNumber?: string;
  direction: 'IN' | 'OUT';
  quantity: number;
  unitCost: number;
  remarks?: string;
}

export interface StockAdjustmentSummaryResponse {
  id: string;
  adjustmentNo: string;
  adjustmentDate: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  reasonCode: string;
  status: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'POSTED' | 'REJECTED';
  totalLines: number;
  createdBy: string;
  approvedBy?: string;
  postedBy?: string;
  createdAt: string;
}

export interface StockAdjustmentResponse {
  id: string;
  adjustmentNo: string;
  adjustmentDate: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  reasonCode: string;
  status: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'POSTED' | 'REJECTED';
  totalLines: number;
  createdBy: string;
  approvedBy?: string;
  approvedAt?: string;
  postedBy?: string;
  postedAt?: string;
  remarks?: string;
  items: StockAdjustmentItemResponse[];
  createdAt: string;
  updatedAt: string;
  version: number;
}
