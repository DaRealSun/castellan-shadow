ALTER TABLE holding ADD COLUMN cusip VARCHAR(12);
ALTER TABLE holding ADD COLUMN security_name VARCHAR(200);
ALTER TABLE holding ADD COLUMN price NUMERIC(14,4);

CREATE INDEX idx_holding_cusip ON holding(cusip);
