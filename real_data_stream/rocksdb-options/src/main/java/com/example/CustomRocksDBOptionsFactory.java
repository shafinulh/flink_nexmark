package com.example;

import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.contrib.streaming.state.DefaultConfigurableOptionsFactory;
import org.apache.flink.contrib.streaming.state.RocksDBOptionsFactory;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;

import java.util.Collection;

public class CustomRocksDBOptionsFactory implements RocksDBOptionsFactory {
    private final DefaultConfigurableOptionsFactory defaultFactory = new DefaultConfigurableOptionsFactory();

    @Override
    public DBOptions createDBOptions(DBOptions currentOptions, Collection<AutoCloseable> handlesToClose) {
        // Set table cache num shard bits to 7
        return defaultFactory.createDBOptions(currentOptions, handlesToClose)
                .setTableCacheNumshardbits(9);
    }

    @Override
    public ColumnFamilyOptions createColumnOptions(ColumnFamilyOptions currentOptions, Collection<AutoCloseable> handlesToClose) {
        return defaultFactory.createColumnOptions(currentOptions, handlesToClose);
    }
}