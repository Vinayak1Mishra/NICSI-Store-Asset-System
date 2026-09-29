-- =============================================================================
-- NICSI Store & Asset Management System
-- Dev Sample Masters Seed Data (Idempotent)
-- =============================================================================

BEGIN;

-- 1. Store Sites
INSERT INTO store.store_site (
    id, store_code, store_name, address, store_type, active
) VALUES
('11111111-0000-0000-0000-000000000001', 'STORE-HQ-GEN', 'HQ General Store', 'NICSI Headquarters, 6th Floor, Hall No. 2/3, NBCC Tower, Bhikaji Cama Place, New Delhi', 'GENERAL', true),
('11111111-0000-0000-0000-000000000002', 'STORE-IT-MAIN', 'IT Store', 'NICSI IT Centre, Ground Floor, CGO Complex, Lodhi Road, New Delhi', 'IT', true),
('11111111-0000-0000-0000-000000000003', 'STORE-LNDC-DR', 'LNDC Store', 'National Data Centre, Shastri Park, Delhi', 'GENERAL', true)
ON CONFLICT (store_code) DO NOTHING;

-- 2. Storage Locations (ROOM > RACK > SHELF/BIN Hierarchy)
-- Store 1: HQ General Store
INSERT INTO store.storage_location (id, store_id, parent_location_id, location_code, location_name, location_type, active)
VALUES
('22222222-0001-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001', NULL, 'HQ-ROOM-101', 'Main Inventory Room 101', 'ROOM', true)
ON CONFLICT (store_id, location_code) DO NOTHING;

INSERT INTO store.storage_location (id, store_id, parent_location_id, location_code, location_name, location_type, active)
VALUES
('22222222-0001-0000-0000-000000000002', '11111111-0000-0000-0000-000000000001', '22222222-0001-0000-0000-000000000001', 'HQ-RACK-A', 'Storage Rack A', 'RACK', true)
ON CONFLICT (store_id, location_code) DO NOTHING;

INSERT INTO store.storage_location (id, store_id, parent_location_id, location_code, location_name, location_type, active)
VALUES
('22222222-0001-0000-0000-000000000003', '11111111-0000-0000-0000-000000000001', '22222222-0001-0000-0000-000000000002', 'HQ-BIN-A1', 'Stationery Bin A1', 'BIN', true)
ON CONFLICT (store_id, location_code) DO NOTHING;

-- Store 2: IT Store
INSERT INTO store.storage_location (id, store_id, parent_location_id, location_code, location_name, location_type, active)
VALUES
('22222222-0002-0000-0000-000000000001', '11111111-0000-0000-0000-000000000002', NULL, 'IT-ROOM-201', 'Hardware Depot Room 201', 'ROOM', true)
ON CONFLICT (store_id, location_code) DO NOTHING;

INSERT INTO store.storage_location (id, store_id, parent_location_id, location_code, location_name, location_type, active)
VALUES
('22222222-0002-0000-0000-000000000002', '11111111-0000-0000-0000-000000000002', '22222222-0002-0000-0000-000000000001', 'IT-RACK-01', 'Server & Laptop Rack 01', 'RACK', true)
ON CONFLICT (store_id, location_code) DO NOTHING;

INSERT INTO store.storage_location (id, store_id, parent_location_id, location_code, location_name, location_type, active)
VALUES
('22222222-0002-0000-0000-000000000003', '11111111-0000-0000-0000-000000000002', '22222222-0002-0000-0000-000000000002', 'IT-SHELF-01A', 'Laptops Staging Shelf 01A', 'SHELF', true)
ON CONFLICT (store_id, location_code) DO NOTHING;

-- Store 3: LNDC Store
INSERT INTO store.storage_location (id, store_id, parent_location_id, location_code, location_name, location_type, active)
VALUES
('22222222-0003-0000-0000-000000000001', '11111111-0000-0000-0000-000000000003', NULL, 'LNDC-ROOM-01', 'Data Centre Staging Area', 'ROOM', true)
ON CONFLICT (store_id, location_code) DO NOTHING;

INSERT INTO store.storage_location (id, store_id, parent_location_id, location_code, location_name, location_type, active)
VALUES
('22222222-0003-0000-0000-000000000002', '11111111-0000-0000-0000-000000000003', '22222222-0003-0000-0000-000000000001', 'LNDC-RACK-N1', 'Network Equipment Rack N1', 'RACK', true)
ON CONFLICT (store_id, location_code) DO NOTHING;

INSERT INTO store.storage_location (id, store_id, parent_location_id, location_code, location_name, location_type, active)
VALUES
('22222222-0003-0000-0000-000000000003', '11111111-0000-0000-0000-000000000003', '22222222-0003-0000-0000-000000000002', 'LNDC-BIN-N1A', 'Cabling & Transceivers Bin N1A', 'BIN', true)
ON CONFLICT (store_id, location_code) DO NOTHING;

-- 3. Categories
INSERT INTO store.item_category (id, category_code, category_name, description, sort_order, active)
VALUES
('33333333-0000-0000-0000-000000000001', 'CAT-IT-HW', 'IT Hardware', 'Computer systems, laptops, workstations, printers', 1, true),
('33333333-0000-0000-0000-000000000002', 'CAT-NET', 'Networking', 'Switches, routers, firewalls, structured cabling', 2, true),
('33333333-0000-0000-0000-000000000003', 'CAT-STAT', 'Stationery', 'Office stationery, paper, writing instruments, office supplies', 3, true),
('33333333-0000-0000-0000-000000000004', 'CAT-SW', 'Software', 'Operating systems, office suites, enterprise database software', 4, true)
ON CONFLICT (category_code) DO NOTHING;

-- 4. Subcategories (2 per category)
INSERT INTO store.item_subcategory (id, category_id, subcategory_code, subcategory_name, description, sort_order, active)
VALUES
('44444444-0001-0000-0000-000000000001', '33333333-0000-0000-0000-000000000001', 'SUBCAT-LAPTOP', 'Laptops & Notebooks', 'Standard and high-performance notebooks', 1, true),
('44444444-0001-0000-0000-000000000002', '33333333-0000-0000-0000-000000000001', 'SUBCAT-DESKTOP', 'Desktops & Workstations', 'Mini PCs, tower workstations and peripherals', 2, true),
('44444444-0002-0000-0000-000000000001', '33333333-0000-0000-0000-000000000002', 'SUBCAT-SWITCH', 'Switches & Routers', 'Managed Layer 2/3 network switches', 1, true),
('44444444-0002-0000-0000-000000000002', '33333333-0000-0000-0000-000000000002', 'SUBCAT-CABLE', 'Structured Cabling', 'Ethernet patch cords and connectivity hardware', 2, true),
('44444444-0003-0000-0000-000000000001', '33333333-0000-0000-0000-000000000003', 'SUBCAT-PAPER', 'Paper & Printing', 'A4/A3 copier paper reams', 1, true),
('44444444-0003-0000-0000-000000000002', '33333333-0000-0000-0000-000000000003', 'SUBCAT-OFFICE', 'Desk Supplies', 'Pens, folders, staplers and general desk materials', 2, true),
('44444444-0004-0000-0000-000000000001', '33333333-0000-0000-0000-000000000004', 'SUBCAT-OS', 'Operating Systems', 'Client and Server Operating Systems', 1, true),
('44444444-0004-0000-0000-000000000002', '33333333-0000-0000-0000-000000000004', 'SUBCAT-PROD', 'Office Productivity', 'Productivity suites and databases', 2, true)
ON CONFLICT (category_id, subcategory_code) DO NOTHING;

-- 5. Items (12 items)
INSERT INTO store.item (
    id, item_code, item_name, category_id, subcategory_id, base_uom_id,
    item_type, tracking_type, short_description, manufacturer_default, model_default,
    hsn_sac_code, standard_rate, useful_life_months, warranty_months,
    returnable, warranty_applicable, expiry_tracking, asset_required, active
) VALUES
-- Consumables (QUANTITY tracking, asset_required=false, returnable=false)
('55555555-0000-0000-0000-000000000001', 'ITEM-STAT-A4', 'A4 Copier Paper 75 GSM (500 Sheets)',
 '33333333-0000-0000-0000-000000000003', '44444444-0003-0000-0000-000000000001', (SELECT id FROM store.uom WHERE uom_code = 'REAM'),
 'CONSUMABLE', 'QUANTITY', 'Premium multi-purpose A4 printing paper ream', 'JK Paper', 'Copier Plus 75GSM',
 '4802', 250.00, 0, 0, false, false, false, false, true),

('55555555-0000-0000-0000-000000000002', 'ITEM-STAT-PEN', 'Executive Ballpoint Pens (Pack of 10)',
 '33333333-0000-0000-0000-000000000003', '44444444-0003-0000-0000-000000000002', (SELECT id FROM store.uom WHERE uom_code = 'BOX'),
 'CONSUMABLE', 'QUANTITY', 'Blue 0.7mm smooth writing ballpoint pens pack', 'Reynolds', '045 Fine Carbide',
 '9608', 120.00, 0, 0, false, false, false, false, true),

('55555555-0000-0000-0000-000000000003', 'ITEM-NET-CAT6-2M', 'Cat6 Molded Patch Cord 2 Meter (Blue)',
 '33333333-0000-0000-0000-000000000002', '44444444-0002-0000-0000-000000000002', (SELECT id FROM store.uom WHERE uom_code = 'NOS'),
 'CONSUMABLE', 'QUANTITY', 'Gigabit high-speed RJ45 network patch cable', 'D-Link', 'NCB-C6UBLUR-2',
 '8544', 150.00, 12, 12, false, true, false, false, true),

('55555555-0000-0000-0000-000000000004', 'ITEM-NET-RJ45', 'Modular RJ45 Cat6 Connectors (100 Pcs/Pack)',
 '33333333-0000-0000-0000-000000000002', '44444444-0002-0000-0000-000000000002', (SELECT id FROM store.uom WHERE uom_code = 'PACK'),
 'CONSUMABLE', 'QUANTITY', 'Gold plated 8P8C Cat6 pass-through crimp plugs', 'Schneider', 'Actassi RJ45',
 '8536', 650.00, 24, 0, false, false, false, false, true),

-- Serialised Non-Consumable Assets (SERIAL tracking, asset_required=true, returnable=true, warranty_applicable=true)
('55555555-0000-0000-0000-000000000005', 'ITEM-HW-DELL-LAT', 'Dell Latitude 5440 Core i7 16GB 512GB SSD',
 '33333333-0000-0000-0000-000000000001', '44444444-0001-0000-0000-000000000001', (SELECT id FROM store.uom WHERE uom_code = 'NOS'),
 'NON_CONSUMABLE', 'SERIAL', '14-inch commercial laptop for engineers & officers', 'Dell', 'Latitude 5440',
 '8471', 78000.00, 36, 36, true, true, false, true, true),

('55555555-0000-0000-0000-000000000006', 'ITEM-HW-HP-ELITE', 'HP EliteDesk 800 G9 Mini PC i5 16GB',
 '33333333-0000-0000-0000-000000000001', '44444444-0001-0000-0000-000000000002', (SELECT id FROM store.uom WHERE uom_code = 'NOS'),
 'NON_CONSUMABLE', 'SERIAL', 'Compact desktop mini workstation with vPro', 'HP', 'EliteDesk 800 G9',
 '8471', 58000.00, 48, 36, true, true, false, true, true),

('55555555-0000-0000-0000-000000000007', 'ITEM-NET-CISCO-9200', 'Cisco Catalyst 9200L 24-Port Gigabit Switch',
 '33333333-0000-0000-0000-000000000002', '44444444-0002-0000-0000-000000000001', (SELECT id FROM store.uom WHERE uom_code = 'NOS'),
 'NON_CONSUMABLE', 'SERIAL', 'Enterprise Layer 3 managed gigabit ethernet switch', 'Cisco Systems', 'C9200L-24T-4G',
 '8517', 125000.00, 60, 36, true, true, false, true, true),

('55555555-0000-0000-0000-000000000008', 'ITEM-HW-CANON-MFP', 'Canon imageRUNNER 2625i Multi-Function Network Printer',
 '33333333-0000-0000-0000-000000000001', '44444444-0001-0000-0000-000000000002', (SELECT id FROM store.uom WHERE uom_code = 'NOS'),
 'NON_CONSUMABLE', 'SERIAL', 'A3 Monochrome laser MFP with duplex networking', 'Canon', 'iR 2625i',
 '8443', 95000.00, 60, 12, true, true, false, true, true),

-- Software Licences (LICENSE tracking, asset_required=false, returnable=false)
('55555555-0000-0000-0000-000000000009', 'ITEM-SW-WIN11PRO', 'Microsoft Windows 11 Pro 64-bit Electronic Licence',
 '33333333-0000-0000-0000-000000000004', '44444444-0004-0000-0000-000000000001', (SELECT id FROM store.uom WHERE uom_code = 'LICENSE'),
 'SOFTWARE', 'LICENSE', 'Windows 11 Professional edition OEM digital licence', 'Microsoft', 'Windows 11 Pro',
 '9973', 12500.00, 60, 0, false, false, false, false, true),

('55555555-0000-0000-0000-000000000010', 'ITEM-SW-M365', 'Microsoft 365 Business Standard Annual Subscription',
 '33333333-0000-0000-0000-000000000004', '44444444-0004-0000-0000-000000000002', (SELECT id FROM store.uom WHERE uom_code = 'LICENSE'),
 'SOFTWARE', 'LICENSE', 'Cloud productivity suite with Word, Excel, Teams, Exchange', 'Microsoft', 'M365 Business Std',
 '9973', 9800.00, 12, 0, false, false, false, false, true),

('55555555-0000-0000-0000-000000000011', 'ITEM-SW-RHEL-SRV', 'Red Hat Enterprise Linux Server Standard (1-2 Sockets)',
 '33333333-0000-0000-0000-000000000004', '44444444-0004-0000-0000-000000000001', (SELECT id FROM store.uom WHERE uom_code = 'LICENSE'),
 'SOFTWARE', 'LICENSE', 'Enterprise Linux server operating system annual subscription', 'Red Hat', 'RHEL Server Std',
 '9973', 42000.00, 12, 0, false, false, false, false, true),

('55555555-0000-0000-0000-000000000012', 'ITEM-SW-ORACLE-DB', 'Oracle Database Standard Edition 2 Named User Plus',
 '33333333-0000-0000-0000-000000000004', '44444444-0004-0000-0000-000000000002', (SELECT id FROM store.uom WHERE uom_code = 'LICENSE'),
 'SOFTWARE', 'LICENSE', 'Relational database management server user licence', 'Oracle Corporation', 'SE2 Named User',
 '9973', 28000.00, 36, 0, false, false, false, false, true)
ON CONFLICT (item_code) DO NOTHING;

-- 6. Item Store Policies
INSERT INTO store.item_store_policy (
    id, item_id, store_id, min_stock_qty, max_stock_qty, reorder_level_qty, reorder_qty,
    allow_negative_stock, valuation_method, default_location_id, active
) VALUES
-- HQ General Store Policies
('66666666-0001-0000-0000-000000000001', '55555555-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001',
 20.000, 200.000, 50.000, 100.000, false, 'WEIGHTED_AVG', '22222222-0001-0000-0000-000000000003', true),

('66666666-0001-0000-0000-000000000002', '55555555-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000001',
 10.000, 100.000, 25.000, 50.000, false, 'WEIGHTED_AVG', '22222222-0001-0000-0000-000000000003', true),

-- IT Store Policies
('66666666-0002-0000-0000-000000000001', '55555555-0000-0000-0000-000000000005', '11111111-0000-0000-0000-000000000002',
 5.000, 50.000, 10.000, 20.000, false, 'WEIGHTED_AVG', '22222222-0002-0000-0000-000000000003', true),

('66666666-0002-0000-0000-000000000002', '55555555-0000-0000-0000-000000000006', '11111111-0000-0000-0000-000000000002',
 3.000, 30.000, 5.000, 15.000, false, 'WEIGHTED_AVG', '22222222-0002-0000-0000-000000000003', true),

('66666666-0002-0000-0000-000000000003', '55555555-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000002',
 20.000, 200.000, 40.000, 100.000, false, 'WEIGHTED_AVG', '22222222-0002-0000-0000-000000000002', true),

('66666666-0002-0000-0000-000000000004', '55555555-0000-0000-0000-000000000004', '11111111-0000-0000-0000-000000000002',
 10.000, 50.000, 15.000, 25.000, false, 'WEIGHTED_AVG', '22222222-0002-0000-0000-000000000002', true),

-- LNDC Store Policies
('66666666-0003-0000-0000-000000000001', '55555555-0000-0000-0000-000000000007', '11111111-0000-0000-0000-000000000003',
 2.000, 20.000, 4.000, 10.000, false, 'WEIGHTED_AVG', '22222222-0003-0000-0000-000000000002', true),

('66666666-0003-0000-0000-000000000002', '55555555-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000003',
 30.000, 300.000, 60.000, 150.000, false, 'WEIGHTED_AVG', '22222222-0003-0000-0000-000000000003', true)
ON CONFLICT (item_id, store_id) DO NOTHING;

-- 7. Purchase Order References & Lines (Status OPEN, MockMasterData vendor IDs)
-- PO 1: Dell Technologies - Laptops & Structured Cabling
INSERT INTO store.purchase_order_ref (
    id, source_system, po_number, po_date, procurement_mode,
    gem_order_number, contract_number, vendor_id, vendor_code_snapshot, vendor_name_snapshot,
    currency_code, total_amount, status
) VALUES (
    '77777777-0000-0000-0000-000000000001', 'NICSI_ERP', 'PO-NICSI-2026-001', CURRENT_DATE - INTERVAL '15 days', 'GEM',
    'GEMC-5116877-2026-01', 'NICSI/PROC/2026/014', '9a878946-15bf-3c7d-af6a-92a2ffddef08', 'VEN001', 'Vendor 1 (Dell India Services)',
    'INR', 928350.00, 'OPEN'
) ON CONFLICT (source_system, po_number) DO NOTHING;

INSERT INTO store.purchase_order_item_ref (
    id, po_ref_id, po_line_no, item_id, item_description,
    ordered_qty, received_qty, unit_rate, tax_amount, delivery_due_date,
    project_code_snapshot, project_name_snapshot
) VALUES
('88888888-0001-0000-0000-000000000001', '77777777-0000-0000-0000-000000000001', 1,
 '55555555-0000-0000-0000-000000000005', 'Dell Latitude 5440 Core i7 16GB 512GB SSD',
 10.000, 0.000, 78000.00, 140400.00, CURRENT_DATE + INTERVAL '15 days', 'PRJ001', 'Project 1'),

('88888888-0001-0000-0000-000000000002', '77777777-0000-0000-0000-000000000001', 2,
 '55555555-0000-0000-0000-000000000003', 'Cat6 Molded Patch Cord 2 Meter (Blue)',
 50.000, 0.000, 150.000, 1350.00, CURRENT_DATE + INTERVAL '15 days', 'PRJ001', 'Project 1')
ON CONFLICT (po_ref_id, po_line_no) DO NOTHING;

-- PO 2: HP Enterprise Solutions - Mini PCs & Paper Reams
INSERT INTO store.purchase_order_ref (
    id, source_system, po_number, po_date, procurement_mode,
    gem_order_number, contract_number, vendor_id, vendor_code_snapshot, vendor_name_snapshot,
    currency_code, total_amount, status
) VALUES (
    '77777777-0000-0000-0000-000000000002', 'NICSI_ERP', 'PO-NICSI-2026-002', CURRENT_DATE - INTERVAL '10 days', 'GEM',
    'GEMC-5116877-2026-02', 'NICSI/PROC/2026/015', '89c933ce-1dd7-34a8-b346-d6c297f1c597', 'VEN002', 'Vendor 2 (HP Enterprise India)',
    'INR', 553920.00, 'OPEN'
) ON CONFLICT (source_system, po_number) DO NOTHING;

INSERT INTO store.purchase_order_item_ref (
    id, po_ref_id, po_line_no, item_id, item_description,
    ordered_qty, received_qty, unit_rate, tax_amount, delivery_due_date,
    project_code_snapshot, project_name_snapshot
) VALUES
('88888888-0002-0000-0000-000000000001', '77777777-0000-0000-0000-000000000002', 1,
 '55555555-0000-0000-0000-000000000006', 'HP EliteDesk 800 G9 Mini PC i5 16GB',
 8.000, 0.000, 58000.00, 83520.00, CURRENT_DATE + INTERVAL '20 days', 'PRJ002', 'Project 2'),

('88888888-0002-0000-0000-000000000002', '77777777-0000-0000-0000-000000000002', 2,
 '55555555-0000-0000-0000-000000000001', 'A4 Copier Paper 75 GSM (500 Sheets)',
 100.000, 0.000, 250.000, 4500.00, CURRENT_DATE + INTERVAL '10 days', 'PRJ002', 'Project 2')
ON CONFLICT (po_ref_id, po_line_no) DO NOTHING;

COMMIT;
