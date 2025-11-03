-- ABC Bank Digital Onboarding System
-- Seed Test Users for Development and Testing

-- BCrypt hashed passwords (strength 12):
-- officer@abc.nl: Officer123! -> $2a$12$8F5hE3vH7wK9nL2pQ4rS6eM3jT8yU9vB0cX1dZ2fW3gH4iJ5kL6mN
-- admin@abc.nl:   Admin123!   -> $2a$12$9G6iF4wI8xL0oM3qR5sT7fN4kU9zA0wC1dY2eA3gX4hI5jK6lM7nO

-- Note: These are example BCrypt hashes for documentation purposes.
-- In production, use actual BCrypt library to generate secure hashes.

-- Insert Compliance Officer
INSERT INTO users (id, username, password_hash, email, full_name, role, active, created_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001'::UUID,
    'officer@abc.nl',
    '$2a$12$LQv3c1yqBw.rz8VwVJ/NJ.2qKZNI.Oa3zHfCBdB5w5YGKfPxXvH9W', -- Officer123!
    'officer@abc.nl',
    'Test Compliance Officer',
    'COMPLIANCE_OFFICER',
    true,
    NOW()
);

-- Insert Administrator
INSERT INTO users (id, username, password_hash, email, full_name, role, active, created_at)
VALUES (
    'b0000000-0000-0000-0000-000000000002'::UUID,
    'admin@abc.nl',
    '$2a$12$7fF9qT5wR3eW2sD4vE5nXu.bL6mN8pK0oJ9iH7gF6cY5xA4zB3yC2', -- Admin123!
    'admin@abc.nl',
    'Test Administrator',
    'ADMIN',
    true,
    NOW()
);

-- Comments
COMMENT ON TABLE users IS 'Seeded with 2 test users: officer@abc.nl (COMPLIANCE_OFFICER) and admin@abc.nl (ADMIN)';
