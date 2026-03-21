CREATE TABLE IF NOT EXISTS customers (
  customer_id TEXT PRIMARY KEY,
  full_name TEXT NOT NULL,
  country TEXT NOT NULL,
  risk_segment TEXT NOT NULL,
  daily_limit NUMERIC(12,2) NOT NULL,
  allowed_payment_methods TEXT NOT NULL,
  is_blocked BOOLEAN NOT NULL
);

INSERT INTO customers (customer_id, full_name, country, risk_segment, daily_limit, allowed_payment_methods, is_blocked)
VALUES
  ('c-1', 'Alice Example', 'PL', 'LOW', 1000.00, 'CARD,BLIK', false),
  ('c-2', 'Bob Example', 'DE', 'MEDIUM', 500.00, 'CARD,TRANSFER', false),
  ('c-3', 'Charlie Example', 'PL', 'HIGH', 100.00, 'CARD', true)
ON CONFLICT (customer_id) DO NOTHING;
