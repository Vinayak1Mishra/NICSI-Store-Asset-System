export interface GrnItemResponse {
  id: string;
  lineNo: number;
  poItemRefId: string | null;
  poLineNo: number | null;
  itemId: string;
  itemCode: string;
  itemName: string;
  uomCode: string | null;
  receivedQty: number;
  acceptedQty: number;
  rejectedQty: number;
  unitRate: number;
  receivingLocationId: string;
  receivingLocationCode: string;
  receivingLocationName: string;
  batchLotNo: string | null;
  manufactureDate: string | null;
  expiryDate: string | null;
  remarks: string | null;
}

export interface GrnResponse {
  id: string;
  grnNo: string;
  grnDate: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  poRefId: string | null;
  poNumber: string | null;
  vendorId: string | null;
  vendorNameSnapshot: string | null;
  invoiceNumber: string | null;
  invoiceDate: string | null;
  challanNumber: string | null;
  challanDate: string | null;
  receivedByUserId: string;
  status: string;
  remarks: string | null;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string | null;
  approvedAt: string | null;
  approvedBy: string | null;
  version: number;
  items: GrnItemResponse[];
}

export interface GrnSummaryResponse {
  id: string;
  grnNo: string;
  grnDate: string;
  storeId: string;
  storeName: string;
  poRefId: string | null;
  poNumber: string | null;
  vendorNameSnapshot: string | null;
  invoiceNumber: string | null;
  challanNumber: string | null;
  status: string;
  itemCount: number;
  totalReceivedQty: number;
  createdAt: string;
}

export interface CreateGrnItemRequest {
  poItemRefId?: string | null;
  itemId: string;
  receivedQty: number;
  unitRate?: number;
  receivingLocationId: string;
  batchLotNo?: string | null;
  manufactureDate?: string | null;
  expiryDate?: string | null;
  remarks?: string | null;
}

export interface CreateGrnRequest {
  grnDate?: string;
  storeId: string;
  poRefId?: string | null;
  vendorId?: string | null;
  vendorNameSnapshot?: string;
  invoiceNumber?: string;
  invoiceDate?: string | null;
  challanNumber?: string;
  challanDate?: string | null;
  remarks?: string;
  items: CreateGrnItemRequest[];
}

export interface UpdateGrnRequest extends CreateGrnRequest {
  version: number;
}

export interface LineSerialRequest {
  grnItemId: string;
  serialNumbers: string[];
}

export interface GrnPostRequest {
  lineSerials?: LineSerialRequest[];
  remarks?: string;
}

export interface PostedLineResponse {
  grnItemId: string;
  itemId: string;
  itemCode: string;
  itemName: string;
  acceptedQty: number;
  unitRate: number;
  receivingLocationCode: string;
  transactionNo: string;
  assetsCreated: number;
}

export interface GrnPostResponse {
  grnId: string;
  grnNo: string;
  postedAt: string;
  postedBy: string;
  totalPostedLines: number;
  totalAssetsCreated: number;
  postedLines: PostedLineResponse[];
  generatedAssetIds: string[];
}

