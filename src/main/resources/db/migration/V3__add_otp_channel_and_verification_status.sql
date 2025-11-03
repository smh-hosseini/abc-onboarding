-- Refactor OTP handling: Move OTP data to separate table and add verification status

-- Add columns to track email and phone verification status in application
ALTER TABLE onboarding_application ADD COLUMN email_verified BOOLEAN DEFAULT false;
ALTER TABLE onboarding_application ADD COLUMN phone_verified BOOLEAN DEFAULT false;

-- Remove OTP fields from onboarding_application (moving to separate table)
ALTER TABLE onboarding_application DROP COLUMN IF EXISTS otp_hash;
ALTER TABLE onboarding_application DROP COLUMN IF EXISTS otp_expires_at;
ALTER TABLE onboarding_application DROP COLUMN IF EXISTS otp_attempts;
ALTER TABLE onboarding_application DROP COLUMN IF EXISTS otp_channel;

-- Create dedicated OTP verification table
CREATE TABLE otp_verification (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,

    FOREIGN KEY (application_id) REFERENCES onboarding_application(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_otp_application_channel ON otp_verification(application_id, channel);
CREATE INDEX idx_otp_status ON otp_verification(status);
CREATE INDEX idx_otp_expires ON otp_verification(expires_at);
