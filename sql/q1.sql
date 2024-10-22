-- Define the Kafka source table
CREATE TABLE nexmark_events (
    event_type INT,
    person ROW<
        id BIGINT,
        name VARCHAR,
        emailAddress VARCHAR,
        creditCard VARCHAR,
        city VARCHAR,
        state VARCHAR,
        dateTime TIMESTAMP(3),
        extra VARCHAR>,
    auction ROW<
        id BIGINT,
        itemName VARCHAR,
        description VARCHAR,
        initialBid BIGINT,
        reserve BIGINT,
        dateTime TIMESTAMP(3),
        expires TIMESTAMP(3),
        seller BIGINT,
        category BIGINT,
        extra VARCHAR>,
    bid ROW<
        auction BIGINT,
        bidder BIGINT,
        price BIGINT,
        channel VARCHAR,
        url VARCHAR,
        dateTime TIMESTAMP(3),
        extra VARCHAR>,
    dateTime AS
        CASE
            WHEN event_type = 0 THEN person.dateTime
            WHEN event_type = 1 THEN auction.dateTime
            ELSE bid.dateTime
        END,
    WATERMARK FOR dateTime AS dateTime - INTERVAL '4' SECOND
) WITH (
    'connector' = 'kafka',
    'topic' = 'nexmark-events',
    'properties.bootstrap.servers' = 'kafka1:19092',
    'properties.group.id' = 'nexmark-flink-consumer',
    'scan.startup.mode' = 'earliest-offset',
    'sink.partitioner' = 'fixed',
    'format' = 'json'
);

-- Create a view that filters only bid events and flattens the structure
CREATE VIEW bid_source AS
SELECT
    bid.auction,
    bid.bidder,
    bid.price,
    bid.channel,
    bid.url,
    bid.dateTime,
    bid.extra
FROM nexmark_events
WHERE event_type = 2 AND bid IS NOT NULL;

-- Create the output sink
CREATE TABLE print_sink (
    auction BIGINT,
    bidder BIGINT,
    price DECIMAL(23, 3),
    dateTime TIMESTAMP(3),
    extra STRING
) WITH (
    'connector' = 'print'
);

-- Execute Query (similar to the original bid processing)
INSERT INTO print_sink
SELECT
    auction,
    bidder,
    CAST(price * 0.908 AS DECIMAL(23, 3)) AS price,
    dateTime,
    extra
FROM bid_source;