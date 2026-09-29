export interface WorkflowStepResponse {
  id: string;
  stepNo: number;
  stepCode: string;
  stepName: string;
  approverType: string;
  approverRoleCode: string;
  slaHours: number | null;
  allowReject: boolean;
  allowReturn: boolean;
}

export interface WorkflowActionResponse {
  id: string;
  stepNo: number;
  actionType: string;
  actorUserId: string;
  actorRoleCode: string | null;
  comments: string | null;
  actionTime: string;
  metadata: string | null;
}

export interface WorkflowInstanceResponse {
  id: string;
  workflowDefinitionId: string;
  workflowCode: string;
  workflowName: string;
  entityType: string;
  entityId: string;
  status: string;
  currentStepNo: number | null;
  startedAt: string;
  completedAt: string | null;
  startedBy: string;
  steps: WorkflowStepResponse[];
  actions: WorkflowActionResponse[];
}

export interface RequisitionLineResponse {
  id: string;
  lineNo: number;
  itemId: string;
  itemCode: string;
  itemName: string;
  uomCode: string | null;
  requestedQty: number;
  approvedQty: number | null;
  issuedQty: number;
  estimatedUnitRate: number | null;
  specification: string | null;
  justification: string | null;
  preferredMakeModel: string | null;
  lineStatus: string;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string | null;
  version: number;
}

export interface RequisitionResponse {
  id: string;
  requisitionNo: string;
  requisitionDate: string;
  requesterUserId: string;
  requesterNameSnapshot: string | null;
  departmentId: string | null;
  departmentCodeSnapshot: string | null;
  departmentNameSnapshot: string | null;
  divisionId: string | null;
  divisionNameSnapshot: string | null;
  projectId: string | null;
  projectCodeSnapshot: string | null;
  projectNameSnapshot: string | null;
  purpose: string | null;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  requiredByDate: string | null;
  status: 'DRAFT' | 'SUBMITTED' | 'UNDER_APPROVAL' | 'APPROVED' | 'REJECTED' | 'STORE_VERIFIED' | 'PARTIALLY_ISSUED' | 'ISSUED' | 'CANCELLED' | 'CLOSED';
  totalEstimatedAmount: number;
  submittedAt: string | null;
  closedAt: string | null;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string | null;
  version: number;
  items: RequisitionLineResponse[];
  workflow: WorkflowInstanceResponse | null;
}

export interface RequisitionLineRequest {
  itemId: string;
  requestedQty: number;
  estimatedUnitRate?: number | null;
  specification?: string;
  justification?: string;
  preferredMakeModel?: string;
}

export interface RequisitionCreateRequest {
  departmentId?: string | null;
  departmentCodeSnapshot?: string;
  departmentNameSnapshot?: string;
  divisionId?: string | null;
  divisionNameSnapshot?: string;
  projectId?: string | null;
  projectCodeSnapshot?: string;
  projectNameSnapshot?: string;
  purpose?: string;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  requiredByDate?: string | null;
  items: RequisitionLineRequest[];
}

export interface RequisitionUpdateRequest extends RequisitionCreateRequest {
  version: number;
}

export interface RequisitionLineDecision {
  lineNo: number;
  approvedQty: number;
  remarks?: string;
}

export interface RequisitionDecisionRequest {
  action: 'APPROVE' | 'REJECT' | 'RETURN';
  comments?: string;
  lineDecisions?: RequisitionLineDecision[];
}
