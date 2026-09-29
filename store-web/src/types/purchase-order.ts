export interface PurchaseOrderItemResponse {
  id: string;
  poLineNo: number;
  itemId: string | null;
  itemCode: string | null;
  itemName: string | null;
  itemDescription: string | null;
  orderedQty: number;
  receivedQty: number;
  remainingQty: number;
  unitRate: number;
  taxAmount: number;
  deliveryDueDate: string | null;
  projectId: string | null;
  projectCodeSnapshot: string | null;
  projectNameSnapshot: string | null;
}

export interface PurchaseOrderResponse {
  id: string;
  sourceSystem: string;
  sourcePoId: string | null;
  poNumber: string;
  poDate: string | null;
  procurementMode: string | null;
  gemOrderNumber: string | null;
  contractNumber: string | null;
  vendorId: string | null;
  vendorCodeSnapshot: string | null;
  vendorNameSnapshot: string | null;
  currencyCode: string;
  totalAmount: number | null;
  status: string;
  rawSnapshot: string | null;
  createdAt: string;
  updatedAt: string;
  items: PurchaseOrderItemResponse[];
}

export interface PurchaseOrderSummaryResponse {
  id: string;
  sourceSystem: string;
  poNumber: string;
  poDate: string | null;
  procurementMode: string | null;
  gemOrderNumber: string | null;
  vendorNameSnapshot: string | null;
  currencyCode: string;
  totalAmount: number | null;
  status: string;
  itemCount: number;
  createdAt: string;
}

export interface CreatePurchaseOrderItemRequest {
  poLineNo: number;
  itemId?: string | null;
  itemDescription?: string | null;
  orderedQty: number;
  unitRate?: number;
  taxAmount?: number;
  deliveryDueDate?: string | null;
  projectId?: string | null;
  projectCodeSnapshot?: string | null;
  projectNameSnapshot?: string | null;
}

export interface CreatePurchaseOrderRequest {
  sourceSystem?: string;
  sourcePoId?: string | null;
  poNumber: string;
  poDate?: string | null;
  procurementMode?: string;
  gemOrderNumber?: string;
  contractNumber?: string;
  vendorId?: string | null;
  vendorCodeSnapshot?: string;
  vendorNameSnapshot?: string;
  currencyCode?: string;
  totalAmount?: number;
  rawSnapshot?: string;
  items: CreatePurchaseOrderItemRequest[];
}
