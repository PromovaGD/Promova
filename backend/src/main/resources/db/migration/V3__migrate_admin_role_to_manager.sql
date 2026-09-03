-- ADMIN existed in releases before T01. Convert stored users before enforcing the new contract.
ALTER TABLE users DROP CONSTRAINT ck_users_role;

UPDATE users
SET role = 'MANAGER'
WHERE role = 'ADMIN';

ALTER TABLE users
    ADD CONSTRAINT ck_users_role CHECK (role IN ('EMPLOYEE', 'MANAGER'));
