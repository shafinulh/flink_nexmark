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
    WATERMARK FOR dateTime AS dateTime - INTERVAL '80' SECOND
) WITH (
    'connector' = 'kafka',
    'topic' = 'nexmark-events',
    'properties.bootstrap.servers' = 'kafka1:19092',
    'properties.group.id' = 'nexmark-flink-consumer',
    'scan.startup.mode' = 'earliest-offset',
    'sink.partitioner' = 'fixed',
    'format' = 'json'
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
    person.dateTime,
    person.extra
FROM nexmark_events WHERE event_type = 0;

-- Create view for auction events
CREATE VIEW auction AS
SELECT 
    auction.id,
    auction.itemName,
    auction.description,
    auction.initialBid,
    auction.reserve,
    auction.dateTime,
    auction.expires,
    auction.seller,
    auction.category,
    auction.extra
FROM nexmark_events WHERE event_type = 1;

-- Create the output sink
CREATE TABLE q3_sink (
    name VARCHAR,
    city VARCHAR,
    state VARCHAR,
    id BIGINT
) WITH (
    'connector' = 'blackhole'
);

-- Query 3: 
INSERT INTO q3_sink
SELECT
    P.name, P.city, P.state, A.id
FROM
    auction AS A 
    INNER JOIN person AS P ON A.seller = P.id
WHERE
    A.category = 10 AND (P.state = 'OR' OR P.state = 'ID' OR P.state = 'CA');