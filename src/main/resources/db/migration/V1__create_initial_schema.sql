-- ABC Bank Digital Onboarding System
-- Initial Database Schema

-- Onboarding Application Table
CREATE TABLE onboarding_application (
    id UUID PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    -- Personal Information (encrypted at application layer)
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),
    date_of_birth DATE,
    gender VARCHAR(20),
    nationality VARCHAR(10),
    ssn VARCHAR(255),
    address_json JSONB,

    -- OTP handling
    otp_hash VARCHAR(255),
    otp_expires_at TIMESTAMP,
    otp_attempts INTEGER DEFAULT 0,

    -- Customer reference (after approval)
    customer_id UUID,
    account_number VARCHAR(50),

    -- Review workflow
    requires_manual_review BOOLEAN DEFAULT false,
    review_reason VARCHAR(500),
    assigned_to VARCHAR(255),

    -- GDPR
    marked_for_deletion BOOLEAN DEFAULT false,
    data_retention_until TIMESTAMP,

    -- Timestamps
    created_at TIMESTAMP NOT NULL,
    submitted_at TIMESTAMP,
    approved_at TIMESTAMP,
    rejected_at TIMESTAMP,
    rejection_reason VARCHAR(500),

    CONSTRAINT uk_ssn UNIQUE (ssn),
    CONSTRAINT uk_email UNIQUE (email),
    CONSTRAINT uk_phone UNIQUE (phone)
);

-- Indexes for performance
CREATE INDEX idx_status_created ON onboarding_application(status, created_at);
CREATE INDEX idx_email ON onboarding_application(email);
CREATE INDEX idx_phone ON onboarding_application(phone);
CREATE INDEX idx_assigned_to ON onboarding_application(assigned_to);
CREATE INDEX idx_retention ON onboarding_application(marked_for_deletion, data_retention_until);

-- Consent Records Table
CREATE TABLE consent_record (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    consent_type VARCHAR(50) NOT NULL,
    granted BOOLEAN NOT NULL,
    granted_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    ip_address VARCHAR(50),
    consent_text TEXT NOT NULL,
    version VARCHAR(20) NOT NULL,

    FOREIGN KEY (application_id) REFERENCES onboarding_application(id) ON DELETE CASCADE
);

CREATE INDEX idx_consent_application ON consent_record(application_id);

-- Application Documents Table
CREATE TABLE application_document (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100),
    file_size BIGINT,
    status VARCHAR(50) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    verified_by VARCHAR(255),
    rejection_reason VARCHAR(500),

    FOREIGN KEY (application_id) REFERENCES onboarding_application(id) ON DELETE CASCADE
);

CREATE INDEX idx_document_application ON application_document(application_id);
CREATE INDEX idx_document_status ON application_document(status);

-- Audit Trail Table (Immutable)
CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    application_id UUID,
    event_type VARCHAR(100) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    actor VARCHAR(255) NOT NULL,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    event_details JSONB,

    FOREIGN KEY (application_id) REFERENCES onboarding_application(id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_application ON audit_event(application_id, timestamp);
CREATE INDEX idx_audit_timestamp ON audit_event(timestamp);
CREATE INDEX idx_audit_event_type ON audit_event(event_type);

-- Customer Table (Created after approval)
CREATE TABLE customer (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(50) UNIQUE NOT NULL,
    account_number VARCHAR(50) UNIQUE NOT NULL,
    onboarding_application_id UUID,

    -- Encrypted PII
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),

    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL,

    FOREIGN KEY (onboarding_application_id) REFERENCES onboarding_application(id)
);

CREATE INDEX idx_customer_account ON customer(account_number);
CREATE INDEX idx_customer_email ON customer(email);

-- Internal Users Table (Bank Employees)
CREATE TABLE users (
    id UUID PRIMARY KEY,
    employee_id VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    role VARCHAR(50) NOT NULL,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL
);

-- Refresh Tokens Table
CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN DEFAULT false,
    created_at TIMESTAMP NOT NULL,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token_hash ON refresh_token(token_hash);
CREATE INDEX idx_refresh_token_expires ON refresh_token(expires_at);
CREATE INDEX idx_refresh_token_user ON refresh_token(user_id);

-- Comments
COMMENT ON TABLE onboarding_application IS 'Main table storing customer onboarding applications';
COMMENT ON TABLE consent_record IS 'GDPR consent records (immutable)';
COMMENT ON TABLE application_document IS 'Uploaded documents (passport, photo)';
COMMENT ON TABLE audit_event IS 'Complete audit trail for compliance';
COMMENT ON TABLE customer IS 'Approved customers with active accounts';
COMMENT ON TABLE users IS 'Internal bank employees (COMPLIANCE_OFFICER, ADMIN)';
COMMENT ON TABLE refresh_token IS 'JWT refresh tokens for session management';
