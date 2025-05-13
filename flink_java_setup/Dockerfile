FROM flink:1.18.0

# copy the nexmark jar and sql files
COPY target/flink-nexmark-java-1.0-SNAPSHOT-jar-with-dependencies.jar /opt/flink/usrlib/
COPY sql/q3.sql /opt/flink/sql/

# Add Kafka connector, JDBC connector, Postgres driver, Kafka client
RUN wget -P /opt/flink/lib/ https://repo1.maven.org/maven2/org/apache/flink/flink-connector-kafka/3.0.2-1.18/flink-connector-kafka-3.0.2-1.18.jar && \
    wget -P /opt/flink/lib/ https://repo1.maven.org/maven2/org/apache/flink/flink-connector-jdbc/3.1.2-1.18/flink-connector-jdbc-3.1.2-1.18.jar && \
    wget -P /opt/flink/lib/ https://jdbc.postgresql.org/download/postgresql-42.6.0.jar && \
    wget -P /opt/flink/lib/ https://repo1.maven.org/maven2/org/apache/kafka/kafka-clients/3.2.3/kafka-clients-3.2.3.jar

# Enable Prometheus metrics in flink-conf.yaml
RUN echo "metrics.reporters: prom" >> "$FLINK_HOME/conf/flink-conf.yaml" && \
    echo "metrics.reporter.prom.factory.class: org.apache.flink.metrics.prometheus.PrometheusReporterFactory" >> "$FLINK_HOME/conf/flink-conf.yaml" && \
    echo "metrics.reporter.prom.port: 9249" >> "$FLINK_HOME/conf/flink-conf.yaml"
