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
        date_time TIMESTAMP(3),
        extra VARCHAR>,
    auction ROW<
        id BIGINT,
        itemName VARCHAR,
        description VARCHAR,
        initial_bid BIGINT,
        reserve BIGINT,
        date_time TIMESTAMP(3),
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
        date_time TIMESTAMP(3),
        extra VARCHAR>,
    date_time AS
        CASE
            WHEN event_type = 0 THEN person.date_time
            WHEN event_type = 1 THEN auction.date_time
            ELSE bid.date_time
        END,
    WATERMARK FOR date_time AS date_time - INTERVAL '1' MINUTE
) WITH (
    'connector' = 'kafka',
    'topic' = 'nexmark-events',
    'properties.bootstrap.servers' = 'kafka1:19092',
    'properties.group.id' = 'nexmark-flink-consumer',
    'scan.startup.mode' = 'earliest-offset',
    'format' = 'json',
    'scan.parallelism' = '4'
);

-- Create view for person events
CREATE VIEW person AS
SELECT
    person.id,
    person.name,
    person.emailAddress,
    person.creditCard,
    person.city,
    person.state,
    date_time,
    person.extra
FROM nexmark_events WHERE event_type = 0;

-- Create view for auction events
CREATE VIEW auction AS
SELECT
    auction.id,
    auction.itemName,
    auction.description,
    auction.initial_bid,
    auction.reserve,
    date_time,
    auction.expires,
    auction.seller,
    auction.category,
    auction.extra
FROM nexmark_events WHERE event_type = 1;

-- Create the PostgreSQL sink table
CREATE TABLE q3_sink (
    name VARCHAR,
    city VARCHAR,
    state VARCHAR,
    id BIGINT,
    auction_rowtime TIMESTAMP(3),
    processing_time TIMESTAMP(3),
    PRIMARY KEY (id) NOT ENFORCED
) WITH (
    'connector' = 'blackhole'
);

-- Modified query with processing time
INSERT INTO q3_sink
SELECT
    P.name,
    P.city,
    P.state,
    A.id,
    A.date_time AS auction_rowtime,
    PROCTIME() AS processing_time
FROM auction AS A
INNER JOIN person AS P
ON  A.seller = P.id
-- AND A.date_time BETWEEN P.date_time - INTERVAL '4' MINUTE
--                     AND P.date_time + INTERVAL '4' MINUTE
WHERE
    A.category = 10
    AND P.state IN ('or', 'id', 'ca');