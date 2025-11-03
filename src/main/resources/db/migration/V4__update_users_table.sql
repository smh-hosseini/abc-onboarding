-- ABC Bank Digital Onboarding System
-- Add Authentication Columns to Users Table

-- Add new columns to existing users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(100) UNIQUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

-- Drop the old employee_id column if it exists
ALTER TABLE users DROP COLUMN IF EXISTS employee_id;

-- Add constraint for user roles
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_user_role;
ALTER TABLE users ADD CONSTRAINT chk_user_role CHECK (role IN ('COMPLIANCE_OFFICER', 'ADMIN'));

-- Create indexes (IF NOT EXISTS not supported, so we need to check manually)
-- These will fail silently if they already exist
CREATE INDEX IF NOT EXISTS idx_user_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_user_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_user_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_user_active ON users(active);

-- Add index to refresh_token if not exists
CREATE INDEX IF NOT EXISTS idx_refresh_token_user_active ON refresh_token(user_id, revoked, expires_at);

-- Update comments
COMMENT ON TABLE users IS 'Internal bank employees (compliance officers and administrators)';
COMMENT ON TABLE refresh_token IS 'JWT refresh tokens for session management with token rotation';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hashed password (strength 12)';
COMMENT ON COLUMN refresh_token.token_hash IS 'SHA-256 hash of the refresh token for secure storage';
