-- Create schema for storing test results
CREATE SCHEMA IF NOT EXISTS nexmark;

-- Create table for Q3 results
CREATE TABLE IF NOT EXISTS nexmark.q3_results (
    name VARCHAR NOT NULL,
    city VARCHAR NOT NULL,
    state VARCHAR NOT NULL,
    id BIGINT PRIMARY KEY,
    auction_date_time TIMESTAMP NOT NULL,
    processing_time TIMESTAMP NOT NULL,
    ingest_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_q3_state ON nexmark.q3_results(state);
CREATE INDEX IF NOT EXISTS idx_q3_processing_time ON nexmark.q3_results(processing_time);
CREATE INDEX IF NOT EXISTS idx_q3_auction_data_time ON nexmark.q3_results(auction_date_time);

-- Create view for monitoring processing latency
CREATE OR REPLACE VIEW nexmark.processing_latency AS
SELECT 
    date_trunc('minute', processing_time) as process_minute,
    count(*) as events_processed,
    avg(EXTRACT(EPOCH FROM (ingest_time - processing_time))) as avg_latency_seconds,
    min(EXTRACT(EPOCH FROM (ingest_time - processing_time))) as min_latency_seconds,
    max(EXTRACT(EPOCH FROM (ingest_time - processing_time))) as max_latency_seconds
FROM nexmark.q3_results
GROUP BY date_trunc('minute', processing_time)
ORDER BY process_minute DESC;

