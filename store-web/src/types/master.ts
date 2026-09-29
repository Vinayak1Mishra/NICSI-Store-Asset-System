export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface UomResponse {
  id: string;
  uomCode: string;
  uomName: string;
  uomType: string;
  decimalAllowed: boolean;
  decimalScale: number;
  description: string | null;
  active: boolean;
  createdAt: string;
  createdBy: string | null;
  updatedAt: string;
  updatedBy: string | null;
  version: number;
}

export interface ItemCategoryResponse {
  id: string;
  categoryCode: string;
  categoryName: string;
  description: string | null;
  sortOrder: number;
  active: boolean;
  createdAt: string;
  createdBy: string | null;
  updatedAt: string;
  updatedBy: string | null;
  version: number;
}

export interface ItemSubcategoryResponse {
  id: string;
  categoryId: string;
  categoryCode: string;
  subcategoryCode: string;
  subcategoryName: string;
  description: string | null;
  sortOrder: number;
  active: boolean;
  createdAt: string;
  createdBy: string | null;
  updatedAt: string;
  updatedBy: string | null;
  version: number;
}

export interface ItemResponse {
  id: string;
  itemCode: string;
  itemName: string;
  categoryId: string;
  categoryCode: string;
  categoryName: string;
  subcategoryId: string | null;
  subcategoryCode: string | null;
  subcategoryName: string | null;
  baseUomId: string;
  uomCode: string;
  uomName: string;
  itemType: string;
  trackingType: string;
  shortDescription: string | null;
  specification: string | null;
  manufacturerDefault: string | null;
  modelDefault: string | null;
  hsnSacCode: string | null;
  standardRate: number | null;
  usefulLifeMonths: number | null;
  warrantyMonths: number | null;
  returnable: boolean;
  warrantyApplicable: boolean;
  expiryTracking: boolean;
  assetRequired: boolean;
  active: boolean;
  createdAt: string;
  createdBy: string | null;
  updatedAt: string;
  updatedBy: string | null;
  version: number;
}

export interface StoreSiteResponse {
  id: string;
  storeCode: string;
  storeName: string;
  officeLocationId: string | null;
  officeCodeSnapshot: string | null;
  officeNameSnapshot: string | null;
  address: string | null;
  storeType: string;
  active: boolean;
  createdAt: string;
  createdBy: string | null;
  updatedAt: string;
  updatedBy: string | null;
  version: number;
}

export interface StorageLocationResponse {
  id: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  parentLocationId: string | null;
  parentLocationCode: string | null;
  parentLocationName: string | null;
  locationCode: string;
  locationName: string;
  locationType: string;
  barcodeValue: string | null;
  active: boolean;
  createdAt: string;
  createdBy: string | null;
  updatedAt: string;
  updatedBy: string | null;
  version: number;
}

export interface LocationTreeNode {
  id: string;
  locationCode: string;
  locationName: string;
  locationType: string;
  barcodeValue: string | null;
  active: boolean;
  children: LocationTreeNode[];
}

export interface ItemStorePolicyResponse {
  id: string;
  itemId: string;
  itemCode: string;
  itemName: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  minStockQty: number;
  maxStockQty: number | null;
  reorderLevelQty: number;
  reorderQty: number;
  allowNegativeStock: boolean;
  valuationMethod: string;
  defaultLocationId: string | null;
  active: boolean;
  createdAt: string;
  createdBy: string | null;
  updatedAt: string;
  updatedBy: string | null;
  version: number;
}
