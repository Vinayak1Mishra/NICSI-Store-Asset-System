export interface InspectionItemResponse {
  id: string;
  grnItemId: string;
  itemId: string;
  itemCode: string;
  itemName: string;
  uomCode: string | null;
  inspectedQty: number;
  acceptedQty: number;
  rejectedQty: number;
  quarantineQty: number;
  specificationMatch: boolean | null;
  physicalCondition: string | null;
  warrantyVerified: boolean | null;
  accessoryVerified: boolean | null;
  technicalResult: string | null;
  remarks: string | null;
}

export interface InspectionResponse {
  id: string;
  inspectionNo: string;
  grnId: string;
  grnNo: string;
  inspectionDate: string;
  inspectedByUserId: string;
  inspectedByUserName: string | null;
  status: string;
  overallRemarks: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string | null;
  version: number;
  items: InspectionItemResponse[];
}

export interface InspectionSummaryResponse {
  id: string;
  inspectionNo: string;
  grnId: string;
  grnNo: string;
  inspectionDate: string;
  inspectedByUserId: string;
  status: string;
  overallRemarks: string | null;
  itemCount: number;
  createdAt: string;
}

export interface DecideInspectionItemRequest {
  inspectionItemId: string;
  acceptedQty: number;
  rejectedQty: number;
  quarantineQty: number;
  specificationMatch?: boolean | null;
  physicalCondition?: string | null;
  warrantyVerified?: boolean | null;
  accessoryVerified?: boolean | null;
  technicalResult?: string | null;
  remarks?: string | null;
}

export interface DecideInspectionRequest {
  overallRemarks?: string;
  items: DecideInspectionItemRequest[];
  version: number;
}
