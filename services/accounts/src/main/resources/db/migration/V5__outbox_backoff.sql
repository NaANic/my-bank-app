-- Backoff between retries: a NEW entry becomes claimable only once
-- next_attempt_at has passed (null = immediately).
alter table outbox add column next_attempt_at timestamptz;
