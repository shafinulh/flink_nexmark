FROM flink:1.17.0

# Install JDK, python3, pip3, and other dependencies
RUN apt-get update -y && \
    apt-get install -y openjdk-11-jdk-headless python3 python3-pip python3-dev wget && \
    rm -rf /var/lib/apt/lists/*

# Create a symlink for python
RUN ln -s /usr/bin/python3 /usr/bin/python

# Set JAVA_HOME for ARM64
ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk-arm64

# Download Flink connectors and PostgreSQL JDBC driver
RUN wget -P /opt/flink/lib/ https://repo.maven.apache.org/maven2/org/apache/flink/flink-sql-connector-kafka/1.17.0/flink-sql-connector-kafka-1.17.0.jar && \
    wget -P /opt/flink/lib/ https://repo.maven.apache.org/maven2/org/apache/flink/flink-connector-jdbc/3.0.0-1.16/flink-connector-jdbc-3.0.0-1.16.jar && \
    wget -P /opt/flink/lib/ https://jdbc.postgresql.org/download/postgresql-42.6.0.jar

# Enable Prometheus metrics in Flink configuration
RUN echo "metrics.reporters: prom" >> "$FLINK_HOME/conf/flink-conf.yaml" && \
    echo "metrics.reporter.prom.factory.class: org.apache.flink.metrics.prometheus.PrometheusReporterFactory" >> "$FLINK_HOME/conf/flink-conf.yaml"

# Copy Python dependencies
COPY requirements.txt /opt/flink/

# Install Python dependencies
RUN pip install --no-cache-dir -r /opt/flink/requirements.txt

# Copy the Python script and SQL file
COPY flink_job.py /opt/flink/
COPY sql/q3.sql /opt/flink/sql/

# Set the working directory
WORKDIR /opt/flink