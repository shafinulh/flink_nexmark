package com.example;

import org.apache.flink.contrib.streaming.state.DefaultConfigurableOptionsFactory;
import org.apache.flink.contrib.streaming.state.RocksDBOptionsFactory;
import org.apache.flink.contrib.streaming.state.RocksDBNativeMetricOptions;
import org.rocksdb.*;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Set;

public class CustomRocksDBOptionsFactory implements RocksDBOptionsFactory {
    private final DefaultConfigurableOptionsFactory defaultFactory = new DefaultConfigurableOptionsFactory();
    
    // Memory configuration constants
    private static final double HIGH_PRIORITY_POOL_RATIO = 0.1;
    private static final long BLOCK_CACHE_SIZE = 107 * 1024 * 1024; 
    private static final long WRITE_BUFFER_SIZE = 64 * 1024 * 1024;
    private static final long TOTAL_WRITE_BUFFER_LIMIT = 170 * 1024 * 1024;

    @Override
    public DBOptions createDBOptions(DBOptions currentOptions, Collection<AutoCloseable> handlesToClose) {
        Cache cache = new LRUCache(BLOCK_CACHE_SIZE, 4, false, HIGH_PRIORITY_POOL_RATIO);
        handlesToClose.add(cache);

        WriteBufferManager writeBufferManager = new WriteBufferManager(TOTAL_WRITE_BUFFER_LIMIT, cache);
        handlesToClose.add(writeBufferManager);

        Statistics statistics = new Statistics();
        statistics.setStatsLevel(StatsLevel.ALL);
        handlesToClose.add(statistics);

        return currentOptions
                .setTableCacheNumshardbits(4)
                .setWriteBufferManager(writeBufferManager)
                .setStatistics(statistics);
    }

    @Override
    public RocksDBNativeMetricOptions createNativeMetricsOptions(RocksDBNativeMetricOptions nativeMetricOptions) {
        try {
            Field monitorTickerTypesField = RocksDBNativeMetricOptions.class.getDeclaredField("monitorTickerTypes");
            monitorTickerTypesField.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            Set<TickerType> monitorTickerTypes = (Set<TickerType>) monitorTickerTypesField.get(nativeMetricOptions);
            
            // Add all block cache related metrics
            monitorTickerTypes.add(TickerType.BLOCK_CACHE_ADD);
            monitorTickerTypes.add(TickerType.BLOCK_CACHE_ADD_FAILURES);
            monitorTickerTypes.add(TickerType.BLOCK_CACHE_INDEX_MISS);
            monitorTickerTypes.add(TickerType.BLOCK_CACHE_INDEX_HIT);
            monitorTickerTypes.add(TickerType.BLOCK_CACHE_FILTER_MISS);
            monitorTickerTypes.add(TickerType.BLOCK_CACHE_FILTER_HIT);
            monitorTickerTypes.add(TickerType.BLOCK_CACHE_DATA_MISS);
            monitorTickerTypes.add(TickerType.BLOCK_CACHE_DATA_HIT);
            monitorTickerTypes.add(TickerType.BLOCK_CACHE_BYTES_READ);
            monitorTickerTypes.add(TickerType.BLOCK_CACHE_BYTES_WRITE);
            
            return nativeMetricOptions;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to add block cache metrics", e);
        }
    }

    @Override
    public ColumnFamilyOptions createColumnOptions(ColumnFamilyOptions currentOptions, Collection<AutoCloseable> handlesToClose) {
        Cache cache = handlesToClose.stream()
                .filter(h -> h instanceof Cache)
                .map(h -> (Cache) h)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Cache not found"));

        BlockBasedTableConfig tableConfig = new BlockBasedTableConfig()
                .setCacheIndexAndFilterBlocks(true)
                .setCacheIndexAndFilterBlocksWithHighPriority(true)
                .setPinL0FilterAndIndexBlocksInCache(true)
                .setBlockCache(cache);

        return currentOptions
                .setWriteBufferSize(WRITE_BUFFER_SIZE)
                .setTableFormatConfig(tableConfig);
        // return currentOptions;
    }
}