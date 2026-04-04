CREATE TABLE IF NOT EXISTS documents (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL,
  title VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS operations (
  id UUID PRIMARY KEY,
  document_id UUID NOT NULL REFERENCES documents(id),
  user_id UUID NOT NULL,
  lamport_clock INT NOT NULL,
  operation_data JSONB NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS snapshots (
  id UUID PRIMARY KEY,
  document_id UUID NOT NULL REFERENCES documents(id),
  s3_url VARCHAR(500) NOT NULL,
  based_on_operation_id UUID NOT NULL REFERENCES operations(id),
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS documents_access (
  document_id UUID NOT NULL REFERENCES documents(id),
  user_id UUID NOT NULL,
  user_role TEXT NOT NULL CHECK (user_role IN ('owner', 'editor', 'viewer')),
  PRIMARY KEY (document_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_operations_document_id ON operations(document_id);
CREATE INDEX IF NOT EXISTS idx_operations_lamport_clock ON operations(document_id, lamport_clock);
CREATE INDEX IF NOT EXISTS idx_snapshots_document_id ON snapshots(document_id);
CREATE INDEX IF NOT EXISTS idx_document_access_user_id ON documents_access(user_id, document_id);
