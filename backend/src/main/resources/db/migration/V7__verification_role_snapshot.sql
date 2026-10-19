-- Old rows remain NULL: do not invent historical roles from today's memberships.
ALTER TABLE verification ADD COLUMN verifier_role VARCHAR(20);
CREATE INDEX idx_verification_evidence ON verification(evidence_id,id);
