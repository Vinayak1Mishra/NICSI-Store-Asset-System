CREATE TABLE store.inventory_lot (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 item_id uuid NOT NULL REFERENCES store.item(id), store_id uuid NOT NULL REFERENCES store.store_site(id),
 lot_number varchar(120) NOT NULL, manufacture_date date, expiry_date date,
 grn_item_id uuid REFERENCES store.grn_item(id),
 status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','QUARANTINE','EXPIRED','CLOSED')),
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(item_id,store_id,lot_number)
);

CREATE TABLE store.stock_balance (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 item_id uuid NOT NULL REFERENCES store.item(id), store_id uuid NOT NULL REFERENCES store.store_site(id),
 location_id uuid NOT NULL REFERENCES store.storage_location(id), lot_id uuid REFERENCES store.inventory_lot(id),
 on_hand_qty numeric(18,3) NOT NULL DEFAULT 0 CHECK(on_hand_qty>=0),
 reserved_qty numeric(18,3) NOT NULL DEFAULT 0 CHECK(reserved_qty>=0),
 available_qty numeric(18,3) GENERATED ALWAYS AS (on_hand_qty-reserved_qty) STORED,
 avg_unit_cost numeric(18,4) NOT NULL DEFAULT 0 CHECK(avg_unit_cost>=0),
 inventory_value numeric(20,2) GENERATED ALWAYS AS (round(on_hand_qty*avg_unit_cost,2)) STORED,
 updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0,
 CHECK(reserved_qty<=on_hand_qty)
);
CREATE UNIQUE INDEX uq_stock_balance_dims ON store.stock_balance(
 item_id,store_id,location_id,COALESCE(lot_id,'00000000-0000-0000-0000-000000000000'::uuid)
);

CREATE TABLE store.stock_reservation (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), reservation_no varchar(60) NOT NULL UNIQUE,
 requisition_item_id uuid NOT NULL REFERENCES store.requisition_item(id),
 item_id uuid NOT NULL REFERENCES store.item(id), store_id uuid NOT NULL REFERENCES store.store_site(id),
 location_id uuid REFERENCES store.storage_location(id), lot_id uuid REFERENCES store.inventory_lot(id),
 reserved_qty numeric(18,3) NOT NULL CHECK(reserved_qty>0),
 consumed_qty numeric(18,3) NOT NULL DEFAULT 0 CHECK(consumed_qty>=0),
 status varchar(20) NOT NULL DEFAULT 'ACTIVE'
   CHECK(status IN ('ACTIVE','PARTIALLY_CONSUMED','CONSUMED','RELEASED','EXPIRED')),
 expires_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 released_at timestamptz, released_by uuid, CHECK(consumed_qty<=reserved_qty)
);

CREATE TABLE store.stock_transaction (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), transaction_no varchar(70) NOT NULL UNIQUE,
 movement_group_id uuid NOT NULL DEFAULT gen_random_uuid(),
 transaction_type varchar(30) NOT NULL
   CHECK(transaction_type IN ('OPENING','RECEIPT','ISSUE','RETURN','TRANSFER_IN','TRANSFER_OUT',
                              'ADJUSTMENT_IN','ADJUSTMENT_OUT','DISPOSAL','REVERSAL')),
 transaction_time timestamptz NOT NULL DEFAULT now(),
 item_id uuid NOT NULL REFERENCES store.item(id), store_id uuid NOT NULL REFERENCES store.store_site(id),
 location_id uuid NOT NULL REFERENCES store.storage_location(id), lot_id uuid REFERENCES store.inventory_lot(id),
 quantity_in numeric(18,3) NOT NULL DEFAULT 0 CHECK(quantity_in>=0),
 quantity_out numeric(18,3) NOT NULL DEFAULT 0 CHECK(quantity_out>=0),
 unit_cost numeric(18,4) NOT NULL DEFAULT 0 CHECK(unit_cost>=0),
 total_cost numeric(20,2) NOT NULL DEFAULT 0 CHECK(total_cost>=0),
 reference_type varchar(30) NOT NULL, reference_id uuid NOT NULL, reference_no varchar(100),
 project_id uuid, department_id uuid, custodian_user_id uuid,
 reversal_of_txn_id uuid REFERENCES store.stock_transaction(id),
 idempotency_key varchar(150) UNIQUE, remarks text,
 posted_by uuid NOT NULL, posted_at timestamptz NOT NULL DEFAULT now(),
 CHECK((quantity_in>0 AND quantity_out=0) OR (quantity_out>0 AND quantity_in=0))
);
CREATE INDEX ix_stock_txn_item_time ON store.stock_transaction(item_id,transaction_time DESC);
CREATE INDEX ix_stock_txn_store_time ON store.stock_transaction(store_id,transaction_time DESC);
CREATE INDEX ix_stock_txn_ref ON store.stock_transaction(reference_type,reference_id);
CREATE TRIGGER trg_stock_txn_immutable BEFORE UPDATE OR DELETE ON store.stock_transaction
FOR EACH ROW EXECUTE FUNCTION store.prevent_update_delete();
