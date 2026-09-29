CREATE OR REPLACE VIEW store.vw_current_stock AS
SELECT
    sb.id as stock_balance_id,
    sb.item_id,
    i.item_code,
    i.item_name,
    i.item_type,
    sb.store_id,
    s.store_code,
    s.store_name,
    il.lot_number as lot_batch_number,
    sb.on_hand_qty,
    sb.reserved_qty,
    sb.available_qty,
    sb.avg_unit_cost,
    sb.inventory_value as total_value,
    i.base_uom_id
FROM store.stock_balance sb
JOIN store.item i ON sb.item_id = i.id
JOIN store.store_site s ON sb.store_id = s.id
LEFT JOIN store.inventory_lot il ON sb.lot_id = il.id;

CREATE OR REPLACE VIEW store.vw_stock_ledger AS
SELECT
    t.id as transaction_id,
    t.transaction_time as transaction_date,
    t.transaction_type,
    t.reference_no as document_reference,
    t.item_id,
    i.item_code,
    i.item_name,
    t.store_id,
    s.store_code,
    s.store_name,
    il.lot_number as lot_batch_number,
    (t.quantity_in - t.quantity_out) as quantity,
    t.unit_cost,
    t.total_cost,
    t.posted_by as created_by,
    t.posted_at as created_at
FROM store.stock_transaction t
JOIN store.item i ON t.item_id = i.id
JOIN store.store_site s ON t.store_id = s.id
LEFT JOIN store.inventory_lot il ON t.lot_id = il.id;

CREATE OR REPLACE VIEW store.vw_employee_assets AS
SELECT
    a.id as asset_id,
    a.asset_code,
    a.serial_number,
    a.item_id,
    i.item_code,
    i.item_name,
    a.asset_status,
    a.condition_status,
    aa.assignee_user_id as employee_id,
    aa.assignee_name_snapshot as employee_name,
    aa.assigned_from,
    aa.location_id,
    l.location_name
FROM store.asset a
JOIN store.item i ON a.item_id = i.id
JOIN store.asset_assignment aa ON a.id = aa.asset_id AND aa.status = 'ACTIVE' AND aa.assignment_type = 'EMPLOYEE'
LEFT JOIN store.storage_location l ON aa.location_id = l.id
WHERE a.asset_status = 'ISSUED';

CREATE OR REPLACE VIEW store.vw_low_stock AS
SELECT
    i.id as item_id,
    i.item_code,
    i.item_name,
    s.id as store_id,
    s.store_code,
    s.store_name,
    COALESCE(SUM(sb.on_hand_qty), 0) as total_on_hand_qty,
    COALESCE(SUM(sb.reserved_qty), 0) as total_reserved_qty,
    COALESCE(SUM(sb.available_qty), 0) as total_available_qty,
    p.reorder_level_qty
FROM store.item_store_policy p
JOIN store.item i ON p.item_id = i.id
JOIN store.store_site s ON p.store_id = s.id
LEFT JOIN store.stock_balance sb ON p.item_id = sb.item_id AND p.store_id = sb.store_id
GROUP BY i.id, i.item_code, i.item_name, s.id, s.store_code, s.store_name, p.reorder_level_qty
HAVING COALESCE(SUM(sb.available_qty), 0) <= p.reorder_level_qty;

CREATE OR REPLACE VIEW store.vw_warranty_expiry AS
SELECT
    a.id as asset_id,
    a.asset_code,
    a.serial_number,
    i.item_code,
    i.item_name,
    a.asset_status,
    a.warranty_start_date,
    a.warranty_end_date,
    (a.warranty_end_date - CURRENT_DATE) as days_to_expiry,
    s.store_code,
    s.store_name
FROM store.asset a
JOIN store.item i ON a.item_id = i.id
LEFT JOIN store.store_site s ON a.store_id = s.id
WHERE a.warranty_end_date IS NOT NULL 
  AND (a.warranty_end_date - CURRENT_DATE) BETWEEN 0 AND 90
  AND a.asset_status != 'DISPOSED';

CREATE OR REPLACE VIEW store.vw_disposal_register AS
SELECT
    d.id as disposal_id,
    d.disposal_method,
    d.disposal_date,
    d.sale_amount as total_disposal_value,
    d.certificate_number,
    d.status,
    di.asset_id,
    a.asset_code,
    a.serial_number,
    i.item_code,
    i.item_name,
    di.realized_value
FROM store.disposal d
JOIN store.disposal_item di ON d.id = di.disposal_id
JOIN store.asset a ON di.asset_id = a.id
JOIN store.item i ON a.item_id = i.id;
