CREATE TABLE store.requisition (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), requisition_no varchar(60) NOT NULL UNIQUE,
 requisition_date date NOT NULL DEFAULT current_date,
 requester_user_id uuid NOT NULL, requester_name_snapshot varchar(200),
 department_id uuid, department_code_snapshot varchar(50), department_name_snapshot varchar(200),
 division_id uuid, division_name_snapshot varchar(200),
 project_id uuid, project_code_snapshot varchar(60), project_name_snapshot varchar(250),
 purpose text, priority varchar(15) NOT NULL DEFAULT 'NORMAL'
   CHECK(priority IN ('LOW','NORMAL','HIGH','URGENT')),
 required_by_date date,
 status varchar(30) NOT NULL DEFAULT 'DRAFT'
   CHECK(status IN ('DRAFT','SUBMITTED','UNDER_APPROVAL','APPROVED','REJECTED','STORE_VERIFIED',
                    'PARTIALLY_ISSUED','ISSUED','CANCELLED','CLOSED')),
 total_estimated_amount numeric(18,2) NOT NULL DEFAULT 0 CHECK(total_estimated_amount>=0),
 submitted_at timestamptz, closed_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0
);

CREATE TABLE store.requisition_item (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 requisition_id uuid NOT NULL REFERENCES store.requisition(id) ON DELETE CASCADE,
 line_no int NOT NULL CHECK(line_no>0), item_id uuid NOT NULL REFERENCES store.item(id),
 requested_qty numeric(18,3) NOT NULL CHECK(requested_qty>0),
 approved_qty numeric(18,3) CHECK(approved_qty IS NULL OR approved_qty>=0),
 issued_qty numeric(18,3) NOT NULL DEFAULT 0 CHECK(issued_qty>=0),
 estimated_unit_rate numeric(18,2) CHECK(estimated_unit_rate IS NULL OR estimated_unit_rate>=0),
 specification text, justification text, preferred_make_model varchar(300),
 line_status varchar(30) NOT NULL DEFAULT 'DRAFT'
   CHECK(line_status IN ('DRAFT','APPROVED','REJECTED','RESERVED','PARTIALLY_ISSUED','ISSUED','CANCELLED')),
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0,
 UNIQUE(requisition_id,line_no)
);
CREATE INDEX ix_req_status_date ON store.requisition(status,requisition_date DESC);
CREATE INDEX ix_req_requester ON store.requisition(requester_user_id,requisition_date DESC);

CREATE TABLE store.purchase_order_ref (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 source_system varchar(30) NOT NULL DEFAULT 'NICSI_ERP', source_po_id uuid,
 po_number varchar(80) NOT NULL, po_date date, procurement_mode varchar(30),
 gem_order_number varchar(100), contract_number varchar(100),
 vendor_id uuid, vendor_code_snapshot varchar(60), vendor_name_snapshot varchar(250),
 currency_code char(3) NOT NULL DEFAULT 'INR', total_amount numeric(18,2),
 status varchar(30) NOT NULL DEFAULT 'OPEN', raw_snapshot jsonb,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(source_system,po_number)
);

CREATE TABLE store.purchase_order_item_ref (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 po_ref_id uuid NOT NULL REFERENCES store.purchase_order_ref(id) ON DELETE CASCADE,
 po_line_no int NOT NULL, item_id uuid REFERENCES store.item(id), item_description text,
 ordered_qty numeric(18,3) NOT NULL CHECK(ordered_qty>0),
 received_qty numeric(18,3) NOT NULL DEFAULT 0 CHECK(received_qty>=0),
 unit_rate numeric(18,2) NOT NULL DEFAULT 0 CHECK(unit_rate>=0),
 tax_amount numeric(18,2) NOT NULL DEFAULT 0 CHECK(tax_amount>=0),
 delivery_due_date date, project_id uuid, project_code_snapshot varchar(60), project_name_snapshot varchar(250),
 UNIQUE(po_ref_id,po_line_no)
);

CREATE TABLE store.grn (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), grn_no varchar(60) NOT NULL UNIQUE,
 grn_date date NOT NULL DEFAULT current_date, store_id uuid NOT NULL REFERENCES store.store_site(id),
 po_ref_id uuid REFERENCES store.purchase_order_ref(id), vendor_id uuid, vendor_name_snapshot varchar(250),
 invoice_number varchar(100), invoice_date date, challan_number varchar(100), challan_date date,
 received_by_user_id uuid NOT NULL,
 status varchar(30) NOT NULL DEFAULT 'DRAFT'
   CHECK(status IN ('DRAFT','SUBMITTED','UNDER_INSPECTION','PARTIALLY_ACCEPTED','ACCEPTED','REJECTED','POSTED','CANCELLED')),
 remarks text,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid,
 approved_at timestamptz, approved_by uuid, version bigint NOT NULL DEFAULT 0
);

CREATE TABLE store.grn_item (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 grn_id uuid NOT NULL REFERENCES store.grn(id) ON DELETE CASCADE, line_no int NOT NULL,
 po_item_ref_id uuid REFERENCES store.purchase_order_item_ref(id),
 item_id uuid NOT NULL REFERENCES store.item(id),
 received_qty numeric(18,3) NOT NULL CHECK(received_qty>0),
 accepted_qty numeric(18,3) NOT NULL DEFAULT 0 CHECK(accepted_qty>=0),
 rejected_qty numeric(18,3) NOT NULL DEFAULT 0 CHECK(rejected_qty>=0),
 unit_rate numeric(18,2) NOT NULL DEFAULT 0 CHECK(unit_rate>=0),
 receiving_location_id uuid NOT NULL REFERENCES store.storage_location(id),
 batch_lot_no varchar(120), manufacture_date date, expiry_date date, remarks text,
 UNIQUE(grn_id,line_no),
 CHECK(accepted_qty+rejected_qty<=received_qty)
);

CREATE TABLE store.inspection (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), inspection_no varchar(60) NOT NULL UNIQUE,
 grn_id uuid NOT NULL REFERENCES store.grn(id),
 inspection_date date NOT NULL DEFAULT current_date, inspected_by_user_id uuid NOT NULL,
 status varchar(30) NOT NULL DEFAULT 'DRAFT'
   CHECK(status IN ('DRAFT','IN_PROGRESS','ACCEPTED','PARTIALLY_ACCEPTED','REJECTED','QUARANTINE','CLOSED')),
 overall_remarks text, approved_by uuid, approved_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0
);

CREATE TABLE store.inspection_item (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 inspection_id uuid NOT NULL REFERENCES store.inspection(id) ON DELETE CASCADE,
 grn_item_id uuid NOT NULL REFERENCES store.grn_item(id), item_id uuid NOT NULL REFERENCES store.item(id),
 inspected_qty numeric(18,3) NOT NULL CHECK(inspected_qty>0),
 accepted_qty numeric(18,3) NOT NULL DEFAULT 0 CHECK(accepted_qty>=0),
 rejected_qty numeric(18,3) NOT NULL DEFAULT 0 CHECK(rejected_qty>=0),
 quarantine_qty numeric(18,3) NOT NULL DEFAULT 0 CHECK(quarantine_qty>=0),
 specification_match boolean, physical_condition varchar(20), warranty_verified boolean,
 accessory_verified boolean, technical_result jsonb, remarks text,
 CHECK(accepted_qty+rejected_qty+quarantine_qty<=inspected_qty)
);
