-- Fix column sizes for encrypted PII fields
-- Encrypted data is much longer than plaintext, so we need to increase column sizes

-- Change phone from VARCHAR(50) to VARCHAR(500) to accommodate encrypted data
ALTER TABLE onboarding_application ALTER COLUMN phone TYPE VARCHAR(500);

-- Change date_of_birth from DATE to VARCHAR(500) since we're storing encrypted string
ALTER TABLE onboarding_application ALTER COLUMN date_of_birth TYPE VARCHAR(500);

-- Also update customer table if it has similar issues
ALTER TABLE customer ALTER COLUMN phone TYPE VARCHAR(500);
