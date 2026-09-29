export interface CreateIssueItemRequest {
  lineNo: number;
  itemId: string;
  requisitionItemId?: string | null;
  reservationId?: string | null;
  locationId: string;
  lotId?: string | null;
  issueQty: number;
  remarks?: string;
}

export interface CreateIssueRequest {
  issueDate?: string;
  storeId: string;
  requisitionId?: string | null;
  issuedToType: 'EMPLOYEE' | 'DEPARTMENT' | 'PROJECT' | 'LOCATION' | 'OTHER';
  issuedToUserId?: string | null;
  issuedToNameSnapshot?: string;
  departmentId?: string | null;
  departmentNameSnapshot?: string;
  projectId?: string | null;
  projectNameSnapshot?: string;
  purpose?: string;
  items: CreateIssueItemRequest[];
}

export interface LineAssetRequest {
  issueItemId: string;
  assetIds: string[];
}

export interface PostIssueRequest {
  lineAssets?: LineAssetRequest[];
  remarks?: string;
}

export interface AcknowledgeIssueRequest {
  acknowledgementStatus: 'ACCEPTED' | 'PARTIAL' | 'REJECTED';
  remarks?: string;
}

export interface IssueItemResponse {
  id: string;
  lineNo: number;
  itemId: string;
  itemCode: string;
  itemName: string;
  locationId: string;
  locationCode: string;
  lotId?: string | null;
  lotNumber?: string | null;
  issueQty: number;
  unitCost: number;
  remarks?: string;
}

export interface IssueResponse {
  id: string;
  issueNo: string;
  issueDate: string;
  requisitionId?: string | null;
  storeId: string;
  storeCode?: string;
  storeName?: string;
  issuedToType: 'EMPLOYEE' | 'DEPARTMENT' | 'PROJECT' | 'LOCATION' | 'OTHER';
  issuedToUserId?: string | null;
  issuedToNameSnapshot?: string;
  departmentId?: string | null;
  departmentNameSnapshot?: string;
  projectId?: string | null;
  projectNameSnapshot?: string;
  purpose?: string;
  status: string;
  issuedByUserId?: string;
  acknowledgedAt?: string | null;
  acknowledgementStatus?: string | null;
  createdAt: string;
  version: number;
  items?: IssueItemResponse[];
}

export interface IssueSummaryResponse {
  id: string;
  issueNo: string;
  issueDate: string;
  storeId: string;
  storeCode: string;
  issuedToType: string;
  issuedToNameSnapshot?: string;
  status: string;
  lineCount: number;
  createdAt: string;
}

export interface IssuePostResult {
  issueId: string;
  issueNo: string;
  status: string;
  totalPostedLines: number;
  totalAssetsAssigned: number;
  postedAt: string;
}
