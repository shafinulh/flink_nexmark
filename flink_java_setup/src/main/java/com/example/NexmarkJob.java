package com.example;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.api.TableEnvironment;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class NexmarkJob {
    public static void main(String[] args) throws Exception {
        // Set up the Flink streaming environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        EnvironmentSettings settings = EnvironmentSettings.newInstance()
                .inStreamingMode()
                .build();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);

        // Optional: set parallelism or RocksDB config
        env.setParallelism(2);

        // Read the SQL file
        String sql = new String(Files.readAllBytes(Paths.get("/opt/flink/sql/q3.sql")));

        // Execute each statement individually
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
