package com.example;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.CoreOptions;
import org.apache.flink.configuration.PipelineOptions;
import org.apache.flink.configuration.StateBackendOptions;
import static org.apache.flink.configuration.StateBackendOptions.STATE_BACKEND_CACHE_SIZE;
import org.apache.flink.contrib.streaming.state.RocksDBStateBackend;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.api.TableEnvironment;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class NexmarkJob {
    public static void main(String[] args) throws Exception {
        // Create Flink config and modify it
        Configuration config = new Configuration();
        config.set(CoreOptions.DEFAULT_PARALLELISM, 2);
        config.set(StateBackendOptions.STATE_BACKEND, "rocksdb");
        // config.set(STATE_BACKEND_CACHE_SIZE, 500);

        // Set up environments with custom config
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(config);
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);
        env.setStateBackend(new RocksDBStateBackend("file:///tmp/flink-checkpoints", true));

        EnvironmentSettings settings = EnvironmentSettings.newInstance()
                .inStreamingMode()
                .build();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);

        // Load SQL and execute statements
        String sql = new String(Files.readAllBytes(Paths.get("/opt/flink/sql/q3.sql")));
        Arrays.stream(sql.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(stmt -> {
                    System.out.println("Executing SQL: " + stmt);
                    tableEnv.executeSql(stmt);
                });

        System.out.println("Nexmark Job setup complete. Waiting for streaming...");
    }
}
