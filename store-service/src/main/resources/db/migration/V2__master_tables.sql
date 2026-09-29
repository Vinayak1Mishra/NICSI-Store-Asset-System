CREATE TABLE store.uom (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 uom_code varchar(20) NOT NULL UNIQUE,
 uom_name varchar(80) NOT NULL,
 uom_type varchar(20) NOT NULL CHECK (uom_type IN ('COUNT','LENGTH','WEIGHT','VOLUME','TIME','LICENSE','BULK','OTHER')),
 decimal_allowed boolean NOT NULL DEFAULT false,
 decimal_scale smallint NOT NULL DEFAULT 0 CHECK(decimal_scale BETWEEN 0 AND 6),
 description varchar(255), active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0
);
CREATE TRIGGER trg_uom_u BEFORE UPDATE ON store.uom FOR EACH ROW EXECUTE FUNCTION store.set_updated_at();

CREATE TABLE store.item_category (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 category_code varchar(30) NOT NULL UNIQUE, category_name varchar(120) NOT NULL,
 description varchar(500), sort_order int NOT NULL DEFAULT 0, active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0
);

CREATE TABLE store.item_subcategory (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 category_id uuid NOT NULL REFERENCES store.item_category(id),
 subcategory_code varchar(30) NOT NULL, subcategory_name varchar(120) NOT NULL,
 description varchar(500), sort_order int NOT NULL DEFAULT 0, active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0,
 UNIQUE(category_id, subcategory_code)
);

CREATE TABLE store.item (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 item_code varchar(60) NOT NULL UNIQUE, item_name varchar(200) NOT NULL,
 category_id uuid NOT NULL REFERENCES store.item_category(id),
 subcategory_id uuid REFERENCES store.item_subcategory(id),
 base_uom_id uuid NOT NULL REFERENCES store.uom(id),
 item_type varchar(30) NOT NULL CHECK(item_type IN ('CONSUMABLE','NON_CONSUMABLE','SOFTWARE','SERVICE_SUPPORT')),
 tracking_type varchar(20) NOT NULL CHECK(tracking_type IN ('QUANTITY','SERIAL','LOT','LICENSE')),
 short_description varchar(500), specification text,
 manufacturer_default varchar(150), model_default varchar(150), hsn_sac_code varchar(30),
 standard_rate numeric(18,2) CHECK(standard_rate IS NULL OR standard_rate >= 0),
 useful_life_months int CHECK(useful_life_months IS NULL OR useful_life_months >= 0),
 warranty_months int CHECK(warranty_months IS NULL OR warranty_months >= 0),
 returnable boolean NOT NULL DEFAULT false, warranty_applicable boolean NOT NULL DEFAULT false,
 expiry_tracking boolean NOT NULL DEFAULT false, asset_required boolean NOT NULL DEFAULT false,
 active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0,
 CHECK((asset_required=true AND tracking_type='SERIAL') OR asset_required=false)
);
CREATE INDEX ix_item_name ON store.item(lower(item_name));
CREATE INDEX ix_item_category ON store.item(category_id, subcategory_id);

CREATE TABLE store.store_site (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 store_code varchar(30) NOT NULL UNIQUE, store_name varchar(150) NOT NULL,
 office_location_id uuid, office_code_snapshot varchar(50), office_name_snapshot varchar(200),
 address text, store_type varchar(30) NOT NULL DEFAULT 'GENERAL'
   CHECK(store_type IN ('GENERAL','IT','CONSUMABLE','ASSET','QUARANTINE','SCRAP','OTHER')),
 active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0
);

CREATE TABLE store.storage_location (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 store_id uuid NOT NULL REFERENCES store.store_site(id),
 parent_location_id uuid REFERENCES store.storage_location(id),
 location_code varchar(50) NOT NULL, location_name varchar(150) NOT NULL,
 location_type varchar(20) NOT NULL CHECK(location_type IN ('ROOM','ZONE','RACK','SHELF','BIN','DESK','OTHER')),
 barcode_value varchar(120), active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0,
 UNIQUE(store_id, location_code)
);

CREATE TABLE store.item_store_policy (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 item_id uuid NOT NULL REFERENCES store.item(id), store_id uuid NOT NULL REFERENCES store.store_site(id),
 min_stock_qty numeric(18,3) NOT NULL DEFAULT 0 CHECK(min_stock_qty>=0),
 max_stock_qty numeric(18,3) CHECK(max_stock_qty IS NULL OR max_stock_qty>=0),
 reorder_level_qty numeric(18,3) NOT NULL DEFAULT 0 CHECK(reorder_level_qty>=0),
 reorder_qty numeric(18,3) NOT NULL DEFAULT 0 CHECK(reorder_qty>=0),
 allow_negative_stock boolean NOT NULL DEFAULT false,
 valuation_method varchar(20) NOT NULL DEFAULT 'WEIGHTED_AVG'
   CHECK(valuation_method IN ('WEIGHTED_AVG','FIFO','STANDARD_COST')),
 default_location_id uuid REFERENCES store.storage_location(id),
 active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid, version bigint NOT NULL DEFAULT 0,
 UNIQUE(item_id, store_id)
);

CREATE TABLE store.document_sequence (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 document_type varchar(30) NOT NULL, financial_year varchar(9) NOT NULL,
 prefix varchar(30) NOT NULL, last_number bigint NOT NULL DEFAULT 0,
 number_padding smallint NOT NULL DEFAULT 6 CHECK(number_padding BETWEEN 3 AND 12),
 updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(document_type, financial_year)
);
