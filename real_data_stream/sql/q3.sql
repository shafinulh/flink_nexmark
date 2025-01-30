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
    WATERMARK FOR date_time AS date_time - INTERVAL '4' SECOND
) WITH (
    'connector' = 'kafka',
    'topic' = 'nexmark-events',
    'properties.bootstrap.servers' = 'kafka1:19092',
    'properties.group.id' = 'nexmark-flink-consumer',
    'scan.startup.mode' = 'earliest-offset',
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
    person.date_time,
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
    auction.date_time,
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
    auction_date_time TIMESTAMP (3),
    processing_time TIMESTAMP(3),
    PRIMARY KEY (id) NOT ENFORCED
) WITH (
    'connector' = 'jdbc',
    'url' = 'jdbc:postgresql://postgres:5432/nexmark',
    'table-name' = 'nexmark.q3_results',
    'username' = 'postgres',
    'password' = 'postgres',
    'sink.parallelism' = '4',
    'sink.buffer-flush.interval' = '1s'
);

-- Modified query with processing time
INSERT INTO q3_sink
SELECT
    P.name, 
    P.city, 
    P.state, 
    A.id,
    A.date_time,
    PROCTIME() as processing_time
FROM
    auction AS A 
    INNER JOIN person AS P ON A.seller = P.id
WHERE
    A.category = 10 AND (P.state = 'or' OR P.state = 'id' OR P.state = 'ca');