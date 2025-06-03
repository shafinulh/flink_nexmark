FROM flink:2.0.0

# copy the nexmark jar and sql files
COPY target/flink-nexmark-java-1.0-SNAPSHOT-jar-with-dependencies.jar /opt/flink/usrlib/
COPY sql/q3.sql /opt/flink/sql/

# Add Kafka 4.0 connector, JDBC, Postgres driver
RUN wget -P /opt/flink/lib/ https://repo1.maven.org/maven2/org/apache/flink/flink-connector-kafka/4.0.0-2.0/flink-connector-kafka-4.0.0-2.0.jar && \
    wget -P /opt/flink/lib/ https://repo1.maven.org/maven2/org/apache/kafka/kafka-clients/3.9.0/kafka-clients-3.9.0.jar && \
    wget -P /opt/flink/lib/ https://repo1.maven.org/maven2/org/apache/flink/flink-connector-jdbc-postgres/4.0.0-2.0/flink-connector-jdbc-postgres-4.0.0-2.0.jar && \
    wget -P /opt/flink/lib/ https://jdbc.postgresql.org/download/postgresql-42.6.0.jar

# Enable Prometheus metrics in flink-conf.yaml
RUN echo "metrics.reporters: prom" >> "$FLINK_HOME/conf/flink-conf.yaml" && \
    echo "metrics.reporter.prom.factory.class: org.apache.flink.metrics.prometheus.PrometheusReporterFactory" >> "$FLINK_HOME/conf/flink-conf.yaml" && \
    echo "metrics.reporter.prom.port: 9249" >> "$FLINK_HOME/conf/flink-conf.yaml"
