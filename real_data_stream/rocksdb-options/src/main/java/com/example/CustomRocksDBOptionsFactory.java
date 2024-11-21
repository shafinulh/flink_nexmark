package com.example;

import org.apache.flink.contrib.streaming.state.DefaultConfigurableOptionsFactory;
import org.apache.flink.contrib.streaming.state.RocksDBOptionsFactory;
import org.rocksdb.*;

import java.util.Collection;

public class CustomRocksDBOptionsFactory implements RocksDBOptionsFactory {
    private final DefaultConfigurableOptionsFactory defaultFactory = new DefaultConfigurableOptionsFactory();
    
    // Memory configuration constants
    private static final double HIGH_PRIORITY_POOL_RATIO = 0.1;
    private static final long BLOCK_CACHE_SIZE = 213 * 1024 * 1024; // 213MB
    private static final long WRITE_BUFFER_SIZE = 64 * 1024 * 1024; // 64MB nominal size
    // Increase total write buffer limit to get ~32MB flush trigger
    private static final long TOTAL_WRITE_BUFFER_LIMIT = 84 * 1024 * 1024; // 128MB total limit

    @Override
    public DBOptions createDBOptions(DBOptions currentOptions, Collection<AutoCloseable> handlesToClose) {
        // Create cache with high priority pool
        Cache cache = new LRUCache(BLOCK_CACHE_SIZE, -1, false, HIGH_PRIORITY_POOL_RATIO);
        handlesToClose.add(cache);

        // Create write buffer manager with adjusted total limit
        WriteBufferManager writeBufferManager = new WriteBufferManager(TOTAL_WRITE_BUFFER_LIMIT, cache);
        handlesToClose.add(writeBufferManager);

        return defaultFactory.createDBOptions(currentOptions, handlesToClose)
                .setTableCacheNumshardbits(6)
                .setWriteBufferManager(writeBufferManager);
    }

    @Override
    public ColumnFamilyOptions createColumnOptions(ColumnFamilyOptions currentOptions, Collection<AutoCloseable> handlesToClose) {
        // Get the cache from handlesToClose
        Cache cache = handlesToClose.stream()
                .filter(h -> h instanceof Cache)
                .map(h -> (Cache) h)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Cache not found"));

        // Create block-based table config
        BlockBasedTableConfig tableConfig = new BlockBasedTableConfig()
                .setCacheIndexAndFilterBlocks(false)
                .setCacheIndexAndFilterBlocksWithHighPriority(false)
                .setPinL0FilterAndIndexBlocksInCache(false)
                .setBlockCache(cache);

        return currentOptions
                .setWriteBufferSize(WRITE_BUFFER_SIZE)  // Set nominal size to 64MB
                .setTableFormatConfig(tableConfig);
    }
}