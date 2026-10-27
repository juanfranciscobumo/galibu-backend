-- Bid ids are composite ("bid_<jobId>_<professionalId>") and exceed 64 chars.
ALTER TABLE bids MODIFY COLUMN id VARCHAR(128) NOT NULL;
