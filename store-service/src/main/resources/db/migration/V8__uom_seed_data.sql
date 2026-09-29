INSERT INTO store.uom(uom_code,uom_name,uom_type,decimal_allowed,decimal_scale,description) VALUES
('NOS','Numbers','COUNT',false,0,'Count of individually identifiable items'),
('EACH','Each','COUNT',false,0,'Single unit'),('PCS','Pieces','COUNT',false,0,'Pieces'),
('SET','Set','COUNT',false,0,'Set of items'),('PAIR','Pair','COUNT',false,0,'Pair of items'),
('BOX','Box','BULK',false,0,'Box'),('PACK','Pack','BULK',false,0,'Pack'),
('REAM','Ream','BULK',false,0,'Paper ream'),('ROLL','Roll','BULK',false,0,'Roll'),
('MTR','Meter','LENGTH',true,3,'Length in meter'),('KM','Kilometer','LENGTH',true,3,'Length in kilometer'),
('KG','Kilogram','WEIGHT',true,3,'Weight in kilogram'),('GM','Gram','WEIGHT',true,3,'Weight in gram'),
('LTR','Litre','VOLUME',true,3,'Volume in litre'),('ML','Millilitre','VOLUME',true,3,'Volume in millilitre'),
('LICENSE','License','LICENSE',false,0,'Software entitlement'),('USER','User','LICENSE',false,0,'Per-user licence'),
('DEVICE','Device','LICENSE',false,0,'Per-device licence'),('MONTH','Month','TIME',false,0,'Monthly period'),
('YEAR','Year','TIME',false,0,'Annual period'),('LOT','Lot','BULK',true,3,'Bulk lot'),
('KIT','Kit','COUNT',false,0,'Kit')
ON CONFLICT(uom_code) DO NOTHING;
