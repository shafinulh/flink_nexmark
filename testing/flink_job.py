import logging
from pyflink.datastream import StreamExecutionEnvironment
from pyflink.table import StreamTableEnvironment, EnvironmentSettings
from pyflink.datastream import RocksDBStateBackend

def main():
    # Set up logging
    logging.basicConfig(level=logging.INFO)
    logger = logging.getLogger(__name__)

    # Set up the execution environment
    env = StreamExecutionEnvironment.get_execution_environment()
    settings = EnvironmentSettings.new_instance().in_streaming_mode().build()
    t_env = StreamTableEnvironment.create(env, environment_settings=settings)

    # Configure RocksDB State Backend
    rocks_db_state_backend = RocksDBStateBackend("file:///tmp/flink-rockdb-checkpoints", True)
    env.set_state_backend(rocks_db_state_backend)

    # Add Kafka and JDBC connectors
    t_env.get_config().get_configuration().set_string(
        "pipeline.jars", 
        "file:///opt/flink/lib/flink-sql-connector-kafka-1.17.0.jar;"
        "file:///opt/flink/lib/flink-connector-jdbc-3.0.0-1.16.jar;"
        "file:///opt/flink/lib/postgresql-42.6.0.jar"
    )

    # Set up checkpointing and state backend
    env.enable_checkpointing(60000)  # Checkpoint every 60 seconds
    env.get_checkpoint_config().set_min_pause_between_checkpoints(30000)
    t_env.get_config().set_local_timezone("UTC")

    # Set parallelism
    env.set_parallelism(1)  # Adjust based on your cluster resources

    # Execute SQL statements
    with open('/opt/flink/sql/q3.sql', 'r') as f:
        sql_query = f.read()
    
    statements = sql_query.split(';')
    for statement in statements:
        if statement.strip():
            logger.info(f"Executing SQL: {statement}")
            t_env.execute_sql(statement)

    logger.info("Flink job completed setup. Waiting for data...")

if __name__ == '__main__':
    main()