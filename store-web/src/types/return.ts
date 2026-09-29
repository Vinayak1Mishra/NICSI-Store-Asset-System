export type ReturnStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'RECEIVED'
  | 'INSPECTED'
  | 'POSTED'
  | 'REJECTED'
  | 'CANCELLED';

export type ConditionStatus =
  | 'NEW'
  | 'GOOD'
  | 'WORKING'
  | 'FAIR'
  | 'DAMAGED'
  | 'REPAIR_REQUIRED'
  | 'UNSERVICEABLE'
  | 'SCRAP';

export type Disposition =
  | 'RESTOCK'
  | 'REPAIR'
  | 'QUARANTINE'
  | 'CONDEMNATION'
  | 'SCRAP';

export interface ReturnSummaryResponse {
  id: string;
  returnNo: string;
  returnDate: string;
  storeId: string;
  storeName: string;
  returnedByUserId?: string;
  departmentId?: string;
  projectId?: string;
  status: ReturnStatus;
  itemCount: number;
  createdAt: string;
}

export interface ReturnItemResponse {
  id: string;
  lineNo: number;
  itemId: string;
  itemCode: string;
  itemName: string;
  assetId?: string;
  assetCode?: string;
  returnQty: number;
  returnLocationId: string;
  locationCode: string;
  conditionStatus?: ConditionStatus;
  disposition?: Disposition;
  remarks?: string;
}

export interface ReturnResponse {
  id: string;
  returnNo: string;
  returnDate: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  returnedByUserId?: string;
  departmentId?: string;
  projectId?: string;
  receivedByUserId?: string;
  status: ReturnStatus;
  remarks?: string;
  items: ReturnItemResponse[];
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  version: number;
}

export interface CreateReturnItemRequest {
  itemId: string;
  assetId?: string;
  returnQty: number;
  returnLocationId: string;
  conditionStatus?: ConditionStatus;
  remarks?: string;
}

export interface CreateReturnRequest {
  storeId: string;
  returnDate?: string;
  returnedByUserId?: string;
  departmentId?: string;
  projectId?: string;
  remarks?: string;
  items: CreateReturnItemRequest[];
}

export interface ReceiveReturnLineRequest {
  lineId: string;
  conditionStatus: ConditionStatus;
  disposition: Disposition;
  returnLocationId?: string;
  remarks?: string;
}

export interface ReceiveReturnRequest {
  receivedByUserId?: string;
  lines: ReceiveReturnLineRequest[];
}

export interface PostReturnRequest {
  remarks?: string;
}
