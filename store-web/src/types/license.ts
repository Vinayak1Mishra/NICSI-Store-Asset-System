export interface SoftwareLicenseAllocationResponse {
  id: string;
  softwareLicenseId: string;
  allocationType: 'USER' | 'DEVICE' | 'SERVER' | 'PROJECT';
  userId?: string | null;
  assetId?: string | null;
  assetCode?: string | null;
  serverIdentifier?: string | null;
  quantity: number;
  allocatedAt: string;
  allocatedBy: string;
  releasedAt?: string | null;
  status: 'ACTIVE' | 'RELEASED' | 'REVOKED';
}

export interface SoftwareLicenseResponse {
  id: string;
  itemId: string;
  itemCode: string;
  itemName: string;
  licenseCode: string;
  vendorId?: string | null;
  licenseType: string;
  entitlementQty: number;
  allocatedQty: number;
  availableQty: number;
  licenseKeySecretRef?: string | null;
  purchaseDate?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  poNumberSnapshot?: string | null;
  status: string;
  createdAt: string;
  version: number;
  allocations?: SoftwareLicenseAllocationResponse[];
}

export interface SoftwareLicenseSummaryResponse {
  id: string;
  itemId: string;
  itemCode: string;
  itemName: string;
  licenseCode: string;
  licenseType: string;
  entitlementQty: number;
  allocatedQty: number;
  availableQty: number;
  startDate?: string | null;
  endDate?: string | null;
  status: string;
}

export interface CreateSoftwareLicenseRequest {
  itemId: string;
  licenseCode: string;
  vendorId?: string | null;
  licenseType: string;
  entitlementQty: number;
  licenseKeySecretRef?: string | null;
  purchaseDate?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  poNumberSnapshot?: string | null;
}

export interface AllocateSoftwareLicenseRequest {
  allocationType: 'USER' | 'DEVICE' | 'SERVER' | 'PROJECT';
  userId?: string | null;
  assetId?: string | null;
  serverIdentifier?: string | null;
  quantity: number;
  remarks?: string;
}
