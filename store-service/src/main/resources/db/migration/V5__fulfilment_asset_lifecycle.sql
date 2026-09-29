CREATE TABLE store.issue_header (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), issue_no varchar(60) NOT NULL UNIQUE,
 issue_date date NOT NULL DEFAULT current_date, requisition_id uuid REFERENCES store.requisition(id),
 store_id uuid NOT NULL REFERENCES store.store_site(id),
 issued_to_type varchar(20) NOT NULL CHECK(issued_to_type IN ('EMPLOYEE','DEPARTMENT','PROJECT','LOCATION','OTHER')),
 issued_to_user_id uuid, issued_to_name_snapshot varchar(200),
 department_id uuid, department_name_snapshot varchar(200), project_id uuid, project_name_snapshot varchar(250),
 purpose text,
 status varchar(30) NOT NULL DEFAULT 'DRAFT'
  CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','POSTED','PARTIALLY_ACKNOWLEDGED','ACKNOWLEDGED','REJECTED','CANCELLED')),
 issued_by_user_id uuid, acknowledged_at timestamptz, acknowledged_by uuid,
 acknowledgement_status varchar(30),
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0
);

CREATE TABLE store.issue_item (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 issue_id uuid NOT NULL REFERENCES store.issue_header(id) ON DELETE CASCADE, line_no int NOT NULL,
 requisition_item_id uuid REFERENCES store.requisition_item(id),
 reservation_id uuid REFERENCES store.stock_reservation(id),
 item_id uuid NOT NULL REFERENCES store.item(id),
 location_id uuid NOT NULL REFERENCES store.storage_location(id), lot_id uuid REFERENCES store.inventory_lot(id),
 issue_qty numeric(18,3) NOT NULL CHECK(issue_qty>0),
 unit_cost numeric(18,4) NOT NULL DEFAULT 0 CHECK(unit_cost>=0), remarks text,
 UNIQUE(issue_id,line_no)
);

CREATE TABLE store.asset (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), asset_code varchar(80) NOT NULL UNIQUE,
 item_id uuid NOT NULL REFERENCES store.item(id), grn_item_id uuid REFERENCES store.grn_item(id),
 issue_item_id uuid REFERENCES store.issue_item(id),
 serial_number varchar(150), manufacturer varchar(150), model_number varchar(150), configuration jsonb,
 purchase_date date, purchase_cost numeric(18,2) CHECK(purchase_cost IS NULL OR purchase_cost>=0),
 po_number_snapshot varchar(100), invoice_number_snapshot varchar(100),
 store_id uuid NOT NULL REFERENCES store.store_site(id),
 location_id uuid NOT NULL REFERENCES store.storage_location(id),
 current_custodian_user_id uuid, current_department_id uuid, current_project_id uuid,
 asset_status varchar(30) NOT NULL DEFAULT 'AVAILABLE'
   CHECK(asset_status IN ('AVAILABLE','RESERVED','ISSUED','IN_TRANSFER','IN_REPAIR','QUARANTINE','LOST','CONDEMNED','DISPOSED','RETIRED')),
 condition_status varchar(30) NOT NULL DEFAULT 'GOOD'
   CHECK(condition_status IN ('NEW','GOOD','WORKING','FAIR','DAMAGED','REPAIR_REQUIRED','UNSERVICEABLE','SCRAP')),
 qr_code_value varchar(180) NOT NULL UNIQUE, barcode_value varchar(180),
 warranty_start_date date, warranty_end_date date, capitalization_ref varchar(100), remarks text,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_asset_item_serial ON store.asset(item_id,serial_number) WHERE serial_number IS NOT NULL;
CREATE INDEX ix_asset_custodian ON store.asset(current_custodian_user_id);
CREATE INDEX ix_asset_project ON store.asset(current_project_id);
CREATE INDEX ix_asset_status ON store.asset(asset_status,condition_status);

CREATE TABLE store.asset_assignment (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 asset_id uuid NOT NULL REFERENCES store.asset(id),
 assignment_type varchar(20) NOT NULL CHECK(assignment_type IN ('EMPLOYEE','DEPARTMENT','PROJECT','LOCATION')),
 assignee_user_id uuid, assignee_name_snapshot varchar(200), department_id uuid, project_id uuid,
 location_id uuid REFERENCES store.storage_location(id), issue_id uuid REFERENCES store.issue_header(id),
 assigned_from timestamptz NOT NULL DEFAULT now(), assigned_until timestamptz,
 status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','RETURNED','TRANSFERRED','CANCELLED')),
 acknowledgement_at timestamptz, created_by uuid NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE store.return_header (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), return_no varchar(60) NOT NULL UNIQUE,
 return_date date NOT NULL DEFAULT current_date, returned_by_user_id uuid, department_id uuid, project_id uuid,
 store_id uuid NOT NULL REFERENCES store.store_site(id), received_by_user_id uuid,
 status varchar(30) NOT NULL DEFAULT 'DRAFT'
  CHECK(status IN ('DRAFT','SUBMITTED','RECEIVED','INSPECTED','POSTED','REJECTED','CANCELLED')),
 remarks text, created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0
);

CREATE TABLE store.return_item (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 return_id uuid NOT NULL REFERENCES store.return_header(id) ON DELETE CASCADE, line_no int NOT NULL,
 item_id uuid NOT NULL REFERENCES store.item(id), asset_id uuid REFERENCES store.asset(id),
 return_qty numeric(18,3) NOT NULL CHECK(return_qty>0),
 return_location_id uuid NOT NULL REFERENCES store.storage_location(id),
 condition_status varchar(30), disposition varchar(30)
  CHECK(disposition IS NULL OR disposition IN ('RESTOCK','REPAIR','QUARANTINE','CONDEMNATION','SCRAP')),
 remarks text, UNIQUE(return_id,line_no)
);

CREATE TABLE store.transfer_header (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), transfer_no varchar(60) NOT NULL UNIQUE,
 transfer_date date NOT NULL DEFAULT current_date,
 source_store_id uuid NOT NULL REFERENCES store.store_site(id),
 destination_store_id uuid NOT NULL REFERENCES store.store_site(id),
 requested_by_user_id uuid NOT NULL, approved_by_user_id uuid,
 status varchar(30) NOT NULL DEFAULT 'DRAFT'
  CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','DISPATCHED','IN_TRANSIT','PARTIALLY_RECEIVED','RECEIVED','REJECTED','CANCELLED')),
 dispatch_date date, receive_date date, remarks text,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0,
 CHECK(source_store_id<>destination_store_id)
);

CREATE TABLE store.transfer_item (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 transfer_id uuid NOT NULL REFERENCES store.transfer_header(id) ON DELETE CASCADE, line_no int NOT NULL,
 item_id uuid NOT NULL REFERENCES store.item(id), asset_id uuid REFERENCES store.asset(id),
 lot_id uuid REFERENCES store.inventory_lot(id),
 source_location_id uuid NOT NULL REFERENCES store.storage_location(id),
 destination_location_id uuid NOT NULL REFERENCES store.storage_location(id),
 transfer_qty numeric(18,3) NOT NULL CHECK(transfer_qty>0),
 received_qty numeric(18,3) NOT NULL DEFAULT 0 CHECK(received_qty>=0),
 remarks text, UNIQUE(transfer_id,line_no), CHECK(received_qty<=transfer_qty)
);

CREATE TABLE store.stock_adjustment (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), adjustment_no varchar(60) NOT NULL UNIQUE,
 adjustment_date date NOT NULL DEFAULT current_date, store_id uuid NOT NULL REFERENCES store.store_site(id),
 reason_code varchar(30) NOT NULL CHECK(reason_code IN ('PHYSICAL_VARIANCE','DATA_CORRECTION','DAMAGE','EXPIRY','FOUND_STOCK','OPENING_BALANCE_CORRECTION','OTHER')),
 reason_detail text NOT NULL,
 status varchar(30) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','POSTED','CANCELLED')),
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 approved_at timestamptz, approved_by uuid, posted_at timestamptz, posted_by uuid
);

CREATE TABLE store.stock_adjustment_item (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 adjustment_id uuid NOT NULL REFERENCES store.stock_adjustment(id) ON DELETE CASCADE, line_no int NOT NULL,
 item_id uuid NOT NULL REFERENCES store.item(id), location_id uuid NOT NULL REFERENCES store.storage_location(id),
 lot_id uuid REFERENCES store.inventory_lot(id), direction varchar(3) NOT NULL CHECK(direction IN ('IN','OUT')),
 quantity numeric(18,3) NOT NULL CHECK(quantity>0),
 unit_cost numeric(18,4) NOT NULL DEFAULT 0 CHECK(unit_cost>=0),
 asset_id uuid REFERENCES store.asset(id), remarks text, UNIQUE(adjustment_id,line_no)
);

CREATE TABLE store.repair_ticket (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), repair_no varchar(60) NOT NULL UNIQUE,
 asset_id uuid NOT NULL REFERENCES store.asset(id), complaint_date date NOT NULL DEFAULT current_date,
 complaint_detail text NOT NULL, warranty_claim boolean NOT NULL DEFAULT false,
 vendor_id uuid, vendor_name_snapshot varchar(250), dispatch_challan_no varchar(100),
 sent_date date, expected_return_date date, received_date date, diagnosis text, repair_action text,
 parts_replaced text, repair_cost numeric(18,2) NOT NULL DEFAULT 0 CHECK(repair_cost>=0),
 status varchar(30) NOT NULL DEFAULT 'OPEN'
  CHECK(status IN ('OPEN','APPROVED','SENT_TO_VENDOR','UNDER_REPAIR','RECEIVED','TESTING','COMPLETED','UNREPAIRABLE','CANCELLED')),
 final_condition varchar(30),
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0
);

CREATE TABLE store.support_contract (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 contract_type varchar(20) NOT NULL CHECK(contract_type IN ('WARRANTY','AMC','CMC','SUPPORT','SUBSCRIPTION')),
 contract_number varchar(100), vendor_id uuid, vendor_name_snapshot varchar(250),
 item_id uuid REFERENCES store.item(id), asset_id uuid REFERENCES store.asset(id),
 start_date date NOT NULL, end_date date NOT NULL, coverage_detail text, sla_detail text,
 amount numeric(18,2) CHECK(amount IS NULL OR amount>=0),
 renewal_reminder_days int NOT NULL DEFAULT 30 CHECK(renewal_reminder_days>=0),
 status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','EXPIRED','RENEWED','CANCELLED')),
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid,
 CHECK(end_date>=start_date)
);

CREATE TABLE store.software_license (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), item_id uuid NOT NULL REFERENCES store.item(id),
 license_code varchar(80) NOT NULL UNIQUE, vendor_id uuid,
 license_type varchar(30) NOT NULL CHECK(license_type IN ('USER','DEVICE','SERVER','CONCURRENT','SUBSCRIPTION','PERPETUAL','SITE')),
 entitlement_qty numeric(18,3) NOT NULL CHECK(entitlement_qty>0),
 allocated_qty numeric(18,3) NOT NULL DEFAULT 0 CHECK(allocated_qty>=0),
 available_qty numeric(18,3) GENERATED ALWAYS AS (entitlement_qty-allocated_qty) STORED,
 license_key_secret_ref varchar(300), purchase_date date, start_date date, end_date date,
 po_number_snapshot varchar(100), status varchar(20) NOT NULL DEFAULT 'ACTIVE'
  CHECK(status IN ('ACTIVE','EXPIRED','SUSPENDED','CANCELLED')),
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0,
 CHECK(allocated_qty<=entitlement_qty)
);

CREATE TABLE store.software_license_allocation (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 software_license_id uuid NOT NULL REFERENCES store.software_license(id),
 allocation_type varchar(20) NOT NULL CHECK(allocation_type IN ('USER','DEVICE','SERVER','PROJECT')),
 user_id uuid, asset_id uuid REFERENCES store.asset(id), server_identifier varchar(200),
 quantity numeric(18,3) NOT NULL DEFAULT 1 CHECK(quantity>0),
 allocated_at timestamptz NOT NULL DEFAULT now(), allocated_by uuid NOT NULL,
 released_at timestamptz, released_by uuid,
 status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','RELEASED','REVOKED'))
);

CREATE TABLE store.physical_verification (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), verification_no varchar(60) NOT NULL UNIQUE,
 verification_name varchar(200) NOT NULL, store_id uuid NOT NULL REFERENCES store.store_site(id),
 verification_type varchar(20) NOT NULL CHECK(verification_type IN ('ANNUAL','PERIODIC','SURPRISE','HANDOVER')),
 snapshot_time timestamptz, start_date date NOT NULL, end_date date, committee_reference varchar(150),
 status varchar(30) NOT NULL DEFAULT 'DRAFT'
  CHECK(status IN ('DRAFT','PLANNED','IN_PROGRESS','RECONCILIATION','SUBMITTED','APPROVED','CLOSED','CANCELLED')),
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL, approved_at timestamptz, approved_by uuid
);

CREATE TABLE store.physical_verification_item (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 verification_id uuid NOT NULL REFERENCES store.physical_verification(id) ON DELETE CASCADE,
 item_id uuid NOT NULL REFERENCES store.item(id), asset_id uuid REFERENCES store.asset(id),
 location_id uuid NOT NULL REFERENCES store.storage_location(id), lot_id uuid REFERENCES store.inventory_lot(id),
 book_qty numeric(18,3) NOT NULL DEFAULT 0, physical_qty numeric(18,3), variance_qty numeric(18,3),
 result_status varchar(30) CHECK(result_status IS NULL OR result_status IN ('FOUND','MISSING','EXCESS','DAMAGED','TRANSFERRED','UNDER_REPAIR','NOT_ACCESSIBLE')),
 scanned_at timestamptz, scanned_by uuid, condition_status varchar(30), remarks text
);

CREATE TABLE store.condemnation (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), condemnation_no varchar(60) NOT NULL UNIQUE,
 proposal_date date NOT NULL DEFAULT current_date, committee_reference varchar(150),
 technical_reason text NOT NULL,
 status varchar(30) NOT NULL DEFAULT 'DRAFT'
  CHECK(status IN ('DRAFT','SUBMITTED','TECHNICALLY_RECOMMENDED','APPROVED','REJECTED','CANCELLED','CLOSED')),
 approved_by uuid, approved_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL
);

CREATE TABLE store.condemnation_item (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 condemnation_id uuid NOT NULL REFERENCES store.condemnation(id) ON DELETE CASCADE,
 asset_id uuid NOT NULL REFERENCES store.asset(id), assessed_condition varchar(30),
 residual_value numeric(18,2) CHECK(residual_value IS NULL OR residual_value>=0),
 recommended_method varchar(30)
  CHECK(recommended_method IS NULL OR recommended_method IN ('AUCTION','E_WASTE','SCRAP','RETURN_TO_OEM','TRANSFER','OTHER')),
 remarks text, UNIQUE(condemnation_id,asset_id)
);

CREATE TABLE store.disposal (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), disposal_no varchar(60) NOT NULL UNIQUE,
 disposal_date date, disposal_method varchar(30) NOT NULL
  CHECK(disposal_method IN ('AUCTION','E_WASTE','SCRAP','RETURN_TO_OEM','TRANSFER','OTHER')),
 purchaser_vendor_id uuid, purchaser_name_snapshot varchar(250),
 sale_amount numeric(18,2) NOT NULL DEFAULT 0 CHECK(sale_amount>=0),
 certificate_number varchar(120),
 status varchar(30) NOT NULL DEFAULT 'DRAFT'
  CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','COMPLETED','POSTED','REJECTED','CANCELLED')),
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 approved_at timestamptz, approved_by uuid, posted_at timestamptz, posted_by uuid
);

CREATE TABLE store.disposal_item (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 disposal_id uuid NOT NULL REFERENCES store.disposal(id) ON DELETE CASCADE,
 asset_id uuid NOT NULL REFERENCES store.asset(id),
 realized_value numeric(18,2) NOT NULL DEFAULT 0 CHECK(realized_value>=0),
 remarks text, UNIQUE(disposal_id,asset_id)
);

CREATE TABLE store.attachment (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), entity_type varchar(40) NOT NULL, entity_id uuid NOT NULL,
 document_type varchar(40) NOT NULL, file_name varchar(255) NOT NULL, mime_type varchar(120),
 object_key varchar(500) NOT NULL, checksum_sha256 char(64),
 file_size_bytes bigint CHECK(file_size_bytes IS NULL OR file_size_bytes>=0),
 uploaded_at timestamptz NOT NULL DEFAULT now(), uploaded_by uuid NOT NULL, active boolean NOT NULL DEFAULT true
);
CREATE INDEX ix_attachment_entity ON store.attachment(entity_type,entity_id);
