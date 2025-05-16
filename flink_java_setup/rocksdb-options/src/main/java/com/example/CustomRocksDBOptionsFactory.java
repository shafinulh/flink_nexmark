package com.example;

import org.apache.flink.contrib.streaming.state.DefaultConfigurableOptionsFactory;
import org.apache.flink.contrib.streaming.state.RocksDBOptionsFactory;
import org.apache.flink.contrib.streaming.state.RocksDBNativeMetricOptions;
import org.rocksdb.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Set;

public class CustomRocksDBOptionsFactory implements RocksDBOptionsFactory {
    private final DefaultConfigurableOptionsFactory defaultFactory = new DefaultConfigurableOptionsFactory();
    
    // Memory configuration constants
    private static final double HIGH_PRIORITY_POOL_RATIO = 0.1;
    private static final long WRITE_BUFFER_SIZE = 32 * 1024 * 1024;

    private static final long BLOCK_CACHE_SIZE = 2 * 1024 * 1024;
    private static final long TOTAL_WRITE_BUFFER_LIMIT = 24 * 1024 * 1024;
    private static final int BLOCK_CACHE_SHARD_BITS = 1;

    // stat dumps to see histogram types - turn off when not needed
    // private static final int STATS_DUMP_PERIOD_SEC = 30; // Dump stats every 90 seconds
    // private static final String ROCKSDB_LOG_SUBDIR_NAME = "rocksdb_native_logs"; // Subdirectory for these logs

    @Override
    public DBOptions createDBOptions(DBOptions currentOptions, Collection<AutoCloseable> handlesToClose) {
        Cache cache = new LRUCache(BLOCK_CACHE_SIZE, BLOCK_CACHE_SHARD_BITS, false, HIGH_PRIORITY_POOL_RATIO);
        handlesToClose.add(cache);

        WriteBufferManager writeBufferManager = new WriteBufferManager(TOTAL_WRITE_BUFFER_LIMIT, new LRUCache(1));
        handlesToClose.add(writeBufferManager);

        Statistics statistics = new Statistics();
        statistics.setStatsLevel(StatsLevel.ALL);
        handlesToClose.add(statistics);

        // currentOptions.setStatsDumpPeriodSec(STATS_DUMP_PERIOD_SEC);
        // String flinkLogDirEnv = System.getenv("FLINK_LOG_DIR");
        // String baseLogDir = (flinkLogDirEnv != null && !flinkLogDirEnv.isEmpty()) ? flinkLogDirEnv : ".";
        // String rocksDbInstanceLogPath = baseLogDir + File.separator + ROCKSDB_LOG_SUBDIR_NAME;
        // File rocksDbLogDirFile = new File(rocksDbInstanceLogPath);
        // if (!rocksDbLogDirFile.exists()) {
        //     rocksDbLogDirFile.mkdirs();
        // }
        // currentOptions.setDbLogDir(rocksDbInstanceLogPath);

        return currentOptions
                .setTableCacheNumshardbits(BLOCK_CACHE_SHARD_BITS)
                .setWriteBufferManager(writeBufferManager)
                .setUseDirectReads(true)
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
            // for monitoring compactions?
            monitorTickerTypes.add(TickerType.COMPACTION_KEY_DROP_OBSOLETE);
            monitorTickerTypes.add(TickerType.COMPACTION_KEY_DROP_USER);
            
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
                .setCacheIndexAndFilterBlocks(false)
                .setCacheIndexAndFilterBlocksWithHighPriority(false)
                .setPinL0FilterAndIndexBlocksInCache(false)
                .setPinTopLevelIndexAndFilter(false)
                .setBlockCache(cache);

        return currentOptions
                .setWriteBufferSize(WRITE_BUFFER_SIZE)
                .setMaxWriteBufferNumber(2)
                .setTableFormatConfig(tableConfig);
        // return currentOptions;
    }
}