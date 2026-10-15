-- Operational state shared by application replicas. Business queries remain read-only.
CREATE TABLE poc_rate_window (
  namespace varchar(120) NOT NULL,
  client_key varchar(200) NOT NULL,
  requests integer NOT NULL,
  expires_at timestamptz NOT NULL,
  PRIMARY KEY(namespace, client_key)
);
CREATE INDEX ix_poc_rate_expiry ON poc_rate_window(expires_at);
CREATE TABLE poc_request_trace (
  namespace varchar(120) NOT NULL,
  request_id varchar(36) NOT NULL,
  payload jsonb NOT NULL,
  expires_at timestamptz NOT NULL,
  PRIMARY KEY(namespace, request_id)
);
CREATE INDEX ix_poc_trace_expiry ON poc_request_trace(expires_at);
