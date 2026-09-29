'use client';

import { usePathname } from 'next/navigation';
import { ComingSoon } from '@/components/common/coming-soon';

interface ModuleConfig {
  title: string;
  description: string;
  phase: string;
  tables: string[];
  features: string[];
}

const MODULE_CONFIGS: Record<string, ModuleConfig> = {
  requisitions: {
    title: 'Material Requisitions',
    description: 'Self-service request submission for consumables, assets, and project materials.',
    phase: 'Phase 2 (Requisitions & Approvals)',
    tables: ['store.requisition', 'store.requisition_item', 'workflow.instance', 'workflow.action'],
    features: [
      'Configurable multi-tier approval workflow (HOD, Project Head, Store Officer)',
      'Estimated rate calculation and purpose justification validation',
      'Stock reservation upon approval to prevent over-allocation',
      'Maker-checker guard preventing self-approval by requester',
    ],
  },
  approvals: {
    title: 'Pending Workflow Approvals',
    description: 'Worklist for HODs, Project Leads, and Competent Authorities to review and decide pending requisitions.',
    phase: 'Phase 2 (Requisitions & Approvals)',
    tables: ['workflow.instance', 'workflow.action', 'store.requisition'],
    features: [
      'Approve, Reject, or Return with mandatory audit comments',
      'SLA escalation tracking and delegation management',
      'Role-based authority limits verification',
      'Real-time status transitions into store.requisition',
    ],
  },
  purchase: {
    title: 'Purchase Orders & Contract References',
    description: 'PO, GeM, and procurement contract integration repository for receipt matching.',
    phase: 'Phase 3 (Procurement Receipt & Inspection)',
    tables: ['store.purchase_order_ref', 'store.purchase_order_item_ref'],
    features: [
      'Reference sync for GeM orders and tender contract lines',
      'Ordered quantity tracking against cumulative receipt quantities',
      'Vendor snapshot capture (GST, name, MSME status)',
      'Delivery due date and project milestone monitoring',
    ],
  },
  grn: {
    title: 'Goods Receipt Notes (GRN)',
    description: 'Inward gate entry, delivery challan verification, and delivery logging.',
    phase: 'Phase 3 (Procurement Receipt & Inspection)',
    tables: ['store.grn', 'store.grn_item', 'store.document_sequence'],
    features: [
      'Auto-numbering format: GRN/YYYY-YY/000001 (rollback safe)',
      'Invoice number, challan date, and vendor delivery validation',
      'Batch/lot assignment and initial receiving location allocation',
      'Pre-condition verification before triggering inspection workflow',
    ],
  },
  inspection: {
    title: 'Receipt Quality Inspection',
    description: 'Technical and physical acceptance, rejection, and quarantine sorting.',
    phase: 'Phase 3 (Procurement Receipt & Inspection)',
    tables: ['store.inspection', 'store.inspection_item', 'store.grn'],
    features: [
      'Quantity balance check: accepted + rejected + quarantine = inspected',
      'Specification compliance verification and physical condition grading',
      'Quarantine inventory isolation for damaged or disputed materials',
      'Maker-checker approval before inventory posting',
    ],
  },
  stock: {
    title: 'Current Stock Position',
    description: 'Multi-dimensional real-time stock balance across stores, rooms, racks, and lots.',
    phase: 'Phase 4 (Lots, Stock Balance & Ledger)',
    tables: ['store.stock_balance', 'store.inventory_lot'],
    features: [
      'Generated columns: available_qty = on_hand_qty - reserved_qty',
      'Weighted average inventory valuation: inventory_value = on_hand_qty * avg_unit_cost',
      'Unique dimension constraint on item, store, location, and lot',
      'Low-stock threshold alerts based on item-store policies',
    ],
  },
  ledger: {
    title: 'Immutable Stock Transaction Ledger',
    description: 'Complete double-entry stock movement register with cryptographic audit proof.',
    phase: 'Phase 4 (Lots, Stock Balance & Ledger)',
    tables: ['store.stock_transaction'],
    features: [
      'Database trigger trg_stock_txn_immutable blocking all UPDATE and DELETE operations',
      'Check constraint: exactly one of quantity_in > 0 or quantity_out > 0',
      'Idempotency-Key support preventing double postings',
      'Movement group ID linking transfer-in and transfer-out pairs',
    ],
  },
  adjustment: {
    title: 'Stock Adjustment & Reconciliation',
    description: 'Physical count corrections and inventory write-ins/write-offs.',
    phase: 'Phase 4 (Lots, Stock Balance & Ledger)',
    tables: ['store.stock_adjustment', 'store.stock_adjustment_item', 'store.stock_transaction'],
    features: [
      'Strict Maker-Checker separation: maker cannot approve their own adjustment',
      'Mandatory reason code selection (Physical Variance, Damage, Expiry)',
      'Generates corresponding ADJUSTMENT_IN / ADJUSTMENT_OUT transactions',
      'Full audit event logging with before-and-after balances',
    ],
  },
  'stock-transfer': {
    title: 'Inter-Store Stock Transfer',
    description: 'Two-step dispatch and receipt transfer between controlled store facilities.',
    phase: 'Phase 4 (Lots, Stock Balance & Ledger)',
    tables: ['store.transfer_header', 'store.transfer_item', 'store.stock_transaction'],
    features: [
      'SQL CHECK constraint: source_store_id != destination_store_id',
      'In-transit inventory custody tracking until recipient acknowledges',
      'Received quantity validation: received_qty <= transfer_qty',
      'Movement group ID linking TRANSFER_OUT and TRANSFER_IN entries',
    ],
  },
  issues: {
    title: 'Material Issue Register',
    description: 'Issue approved consumables and capital assets to employees, departments, and projects.',
    phase: 'Phase 5 (Issue, Asset Lifecycle & Returns)',
    tables: ['store.issue_header', 'store.issue_item', 'store.stock_reservation'],
    features: [
      'Requisition link validation ensuring issued_qty <= approved_qty',
      'Automatic stock reservation release and stock ledger decrement',
      'Generates asset assignment record when serial items are issued',
      'Digital recipient signature & handover verification workflow',
    ],
  },
  returns: {
    title: 'Item & Asset Returns',
    description: 'Process returned materials from separated employees, closed projects, or excess issue.',
    phase: 'Phase 5 (Issue, Asset Lifecycle & Returns)',
    tables: ['store.return_header', 'store.return_item', 'store.asset_assignment'],
    features: [
      'Condition assessment (GOOD, FAIR, DAMAGED, UNSERVICEABLE)',
      'Disposition routing: RESTOCK, REPAIR, QUARANTINE, CONDEMNATION, SCRAP',
      'Automatic custody assignment termination upon return acceptance',
      'Restock generates RETURN stock transaction into receiving location',
    ],
  },
  acknowledgements: {
    title: 'Digital Custody Acknowledgements',
    description: 'Employee acceptance workflow for assigned IT laptops, assets, and project materials.',
    phase: 'Phase 5 (Issue, Asset Lifecycle & Returns)',
    tables: ['store.issue_header', 'store.asset_assignment'],
    features: [
      'Digital OTP / Token confirmation of received assets',
      'Captures acknowledgement timestamp and user agent metadata',
      'Status transitions: PARTIALLY_ACKNOWLEDGED -> ACKNOWLEDGED',
      'Reminder notification schedule for pending acknowledgements',
    ],
  },
  assets: {
    title: 'Master Asset Register',
    description: 'Complete lifecycle digital identity for capitalized hardware, machinery, and equipment.',
    phase: 'Phase 5 (Issue, Asset Lifecycle & Returns)',
    tables: ['store.asset', 'store.asset_assignment'],
    features: [
      'Unique asset code and QR code generation per serialized item',
      'Unique constraint on (item_id, serial_number) to prevent duplicates',
      'Real-time status tracking: AVAILABLE, ISSUED, IN_REPAIR, CONDEMNED, DISPOSED',
      'Capitalization link with purchase order and invoice snapshots',
    ],
  },
  'employee-assets': {
    title: 'Employee Asset Custody',
    description: 'Employee-wise asset inventory, current custody list, and separation clearance check.',
    phase: 'Phase 5 (Issue, Asset Lifecycle & Returns)',
    tables: ['store.asset', 'store.asset_assignment'],
    features: [
      'Instant search by employee ID or name to view all issued equipment',
      'Active vs historical assignment timeline view',
      'No-Dues / Separation clearance workflow integration',
      'Hand-over custody transfer to another employee',
    ],
  },
  'project-assets': {
    title: 'Project & Facility Assets',
    description: 'Assets dedicated to specific government client projects and cost centres.',
    phase: 'Phase 5 (Issue, Asset Lifecycle & Returns)',
    tables: ['store.asset', 'store.asset_assignment'],
    features: [
      'Filter and aggregate assets by project code or client department',
      'Project completion demobilization and store return tracking',
      'Dedicated project depreciation and utilization tracking',
      'Physical location mapping within project data centers',
    ],
  },
  'asset-transfer': {
    title: 'Asset Custody Transfer',
    description: 'Controlled transfer of asset custody between employees, projects, or locations.',
    phase: 'Phase 5 (Issue, Asset Lifecycle & Returns)',
    tables: ['store.asset_assignment', 'store.asset'],
    features: [
      'Hand-over protocol requiring confirmation by both custodians',
      'Approval by designated Store Officer or Project Head',
      'Closes prior active assignment and creates new active assignment atomically',
      'Full custody chain audit log preserved in audit.event',
    ],
  },
  repair: {
    title: 'Repair & Maintenance Register',
    description: 'Fault reporting, OEM warranty ticket tracking, vendor dispatch, and testing.',
    phase: 'Phase 5 (Issue, Asset Lifecycle & Returns)',
    tables: ['store.repair_ticket', 'store.asset'],
    features: [
      'Auto-generation of dispatch delivery challan for vendor service center',
      'Warranty claim validation based on item warranty months',
      'Testing and return inspection before returning asset to AVAILABLE status',
      'Unrepairable items routed automatically to Condemnation proposal',
    ],
  },
  warranty: {
    title: 'Warranty & AMC Contracts',
    description: 'Hardware warranties, annual maintenance contracts (AMC), and SLA tracking.',
    phase: 'Phase 6 (Warranty, AMC & Software Licences)',
    tables: ['store.support_contract', 'store.asset'],
    features: [
      'Upcoming expiration notifications based on renewal_reminder_days',
      'Check constraint: end_date >= start_date',
      'Vendor SLA tracking and coverage level details',
      'Renewal quotation and extension workflow',
    ],
  },
  licences: {
    title: 'Software Licences & Entitlements',
    description: 'Enterprise software license pool, allocation tracking, and compliance audit.',
    phase: 'Phase 6 (Warranty, AMC & Software Licences)',
    tables: ['store.software_license', 'store.software_license_allocation'],
    features: [
      'Generated available quantity: available_qty = entitlement_qty - allocated_qty',
      'SQL CHECK constraint: allocated_qty <= entitlement_qty (over-allocation blocked at DB)',
      'Allocation types: USER, DEVICE, SERVER, PROJECT',
      'License key vault encryption and expiry alerts',
    ],
  },
  verification: {
    title: 'Physical Verification & Reconciliation',
    description: 'Annual, periodic, and surprise physical counts with book-to-physical variance resolution.',
    phase: 'Phase 7 (Physical Verification & Reconciliation)',
    tables: ['store.physical_verification', 'store.physical_verification_item'],
    features: [
      'Inventory snapshot freeze at verification cycle initiation',
      'Mobile/barcode scan matching against book balance',
      'Variance categories: FOUND, MISSING, EXCESS, DAMAGED, TRANSFERRED',
      'Committee report generation and reconciliation approval workflow',
    ],
  },
  disposal: {
    title: 'Condemnation & Disposal Register',
    description: 'Survey committee review, condemnation certificate, and disposal by auction/e-waste.',
    phase: 'Phase 8 (Condemnation, Disposal & Reports)',
    tables: ['store.condemnation', 'store.condemnation_item', 'store.disposal', 'store.disposal_item'],
    features: [
      'Two-stage governance: Condemnation recommendation -> Disposal execution',
      'Disposal methods: AUCTION, E_WASTE, SCRAP, RETURN_TO_OEM, TRANSFER',
      'Maker-checker approval: Competent Authority approval required',
      'Terminal asset state: DISPOSED / RETIRED assets blocked from all future issues',
    ],
  },
  reports: {
    title: 'Operational & Management Reports',
    description: 'Statutory registers, stock valuation, non-moving analysis, and audit reports.',
    phase: 'Phase 8 (Condemnation, Disposal & Reports)',
    tables: ['store.stock_transaction', 'store.stock_balance', 'store.asset'],
    features: [
      'Stock Ledger / Bin Card export (PDF / CSV)',
      'Weighted average inventory valuation by store site',
      'Non-moving and slow-moving items aging analysis (>180 days)',
      'Asset register Form GFR-22 / GFR-23 compliant outputs',
    ],
  },
  audit: {
    title: 'Audit Trail & Event Log',
    description: 'Searchable immutable activity log tracking every create, update, and approval.',
    phase: 'Foundation (Phase 0/1 Active)',
    tables: ['audit.event'],
    features: [
      'Database trigger trg_audit_immutable blocking any modification to audit records',
      'Actor identification (user ID, username, role, IP address, user agent)',
      'Complete JSON before-and-after snapshots (old_value and new_value)',
      'Correlation ID tracing across HTTP request and database transaction',
    ],
  },
};

export default function CatchAllComingSoon() {
  const pathname = usePathname();
  const segments = pathname.replace(/^\//, '').split('/');
  const routeKey = segments[0] || 'requisitions';

  const config = MODULE_CONFIGS[routeKey] || {
    title: routeKey.replace(/-/g, ' ').toUpperCase(),
    description: 'Module under roadmap planning.',
    phase: 'Phase 2–8',
    tables: ['store.*'],
    features: ['Scheduled in upcoming implementation phase.'],
  };

  return (
    <ComingSoon
      title={config.title}
      description={config.description}
      phase={config.phase}
      tables={config.tables}
      features={config.features}
    />
  );
}
