package com.example;

import org.apache.flink.state.rocksdb.RocksDBOptionsFactory;
import org.apache.flink.state.rocksdb.RocksDBNativeMetricOptions;
import org.rocksdb.*;

import java.util.Collection;
import java.util.Set;
import java.lang.reflect.Field;

public class CustomRocksDBOptionsFactory implements RocksDBOptionsFactory {

    // ----------------------------------------
    // MEMORY / CACHE CONFIGURATION CONSTANTS
    // ----------------------------------------
    // Ratio of cache space reserved for high-priority blocks (index & filter)
    private static final double HIGH_PRIORITY_POOL_RATIO = 0.1;

    // Size of the data block cache (in bytes)
    private static final long BLOCK_CACHE_SIZE = 512L * 1024 * 1024;

    // Number of bits to shard the block cache
    private static final int BLOCK_CACHE_SHARD_BITS = 1;

    // Per-Column-Family write-buffer size (memtable) in bytes
    private static final long WRITE_BUFFER_SIZE = 32L * 1024 * 1024;
    // Total write-buffer limit across all column families (for WriteBufferManager)
    private static final long TOTAL_WRITE_BUFFER_LIMIT = 256L * 1024 * 1024;
    // Number of memtables per column family before forcing flush
    private static final int MAX_WRITE_BUFFER_NUMBER = 3; // irrelevant since i see all memtables flushed as soon as they are inactive

    // Number of L0 files needed before triggering a compaction
    private static final int L0_FILE_NUM_COMPACTION_TRIGGER = 4;
    // Target size (bytes) for L1 SST files
    private static final long TARGET_FILE_SIZE_BASE = 64L * 1024 * 1024;
    // Max bytes for the base level (L0) before compaction is triggered
    private static final long MAX_BYTES_FOR_LEVEL_BASE = 256L * 1024 * 1024;

    // ----------------------------------------
    // COMPACTION / FLUSH / I/O CONFIGURATION
    // ----------------------------------------
    // Background threads
    private static final int MAX_BACKGROUND_FLUSHES     = 1;
    private static final int MAX_BACKGROUND_COMPACTIONS = 6;
    private static final int MAX_BACKGROUND_JOBS        = MAX_BACKGROUND_FLUSHES + MAX_BACKGROUND_COMPACTIONS;

    // Subcompactions per compaction task
    private static final int MAX_SUBCOMPACTIONS = 4;

    @Override
    public DBOptions createDBOptions(DBOptions currentOptions, Collection<AutoCloseable> handlesToClose) {
        Cache blockCache = new LRUCache(
            BLOCK_CACHE_SIZE,
            BLOCK_CACHE_SHARD_BITS,
            false,
            HIGH_PRIORITY_POOL_RATIO
        );
        handlesToClose.add(blockCache);

        WriteBufferManager writeBufferManager = new WriteBufferManager(
            TOTAL_WRITE_BUFFER_LIMIT,
            new LRUCache(1) // we pass a dummy cache to not charge Write Buffer Memory to data block cache
        );
        handlesToClose.add(writeBufferManager);

        Statistics statistics = new Statistics();
        statistics.setStatsLevel(StatsLevel.ALL);
        handlesToClose.add(statistics);

        return currentOptions
            // Use the WriteBufferManager instead of letting each CF allocate independently
            .setWriteBufferManager(writeBufferManager)
            // Disable direct reads so caches operate through OS page cache → we measure real disk I/O
            .setUseDirectReads(false)
            // Leave direct writes disabled (default) so memtable flushes go through page cache
            .setUseDirectIoForFlushAndCompaction(true)

            // RocksDB background threads
            .setMaxBackgroundFlushes(MAX_BACKGROUND_FLUSHES)
            .setMaxBackgroundCompactions(MAX_BACKGROUND_COMPACTIONS)
            .setMaxBackgroundJobs(MAX_BACKGROUND_JOBS)

            .setMaxSubcompactions(MAX_SUBCOMPACTIONS)

            // Attach statistics for Prometheus
            .setStatistics(statistics);
    }

    @Override
    public RocksDBNativeMetricOptions createNativeMetricsOptions(RocksDBNativeMetricOptions nativeMetricOptions) {
        try {
            Field f = RocksDBNativeMetricOptions.class.getDeclaredField("monitorTickerTypes");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<TickerType> tickers = (Set<TickerType>) f.get(nativeMetricOptions);

            // Add all block-cache related TickerTypes so Prometheus sees index/filter/data hit/miss
            tickers.add(TickerType.BLOCK_CACHE_ADD);
            tickers.add(TickerType.BLOCK_CACHE_ADD_FAILURES);
            tickers.add(TickerType.BLOCK_CACHE_INDEX_HIT);
            tickers.add(TickerType.BLOCK_CACHE_INDEX_MISS);
            tickers.add(TickerType.BLOCK_CACHE_FILTER_HIT);
            tickers.add(TickerType.BLOCK_CACHE_FILTER_MISS);
            tickers.add(TickerType.BLOCK_CACHE_DATA_HIT);
            tickers.add(TickerType.BLOCK_CACHE_DATA_MISS);
            tickers.add(TickerType.BLOCK_CACHE_BYTES_READ);
            tickers.add(TickerType.BLOCK_CACHE_BYTES_WRITE);

            // Also watch compaction key-drop metrics for visibility
            tickers.add(TickerType.COMPACTION_KEY_DROP_OBSOLETE);
            tickers.add(TickerType.COMPACTION_KEY_DROP_USER);

            return nativeMetricOptions;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to register block cache metrics", e);
        }
    }

    @Override
    public ColumnFamilyOptions createColumnOptions(
            ColumnFamilyOptions currentOptions,
            Collection<AutoCloseable> handlesToClose) {

        Cache blockCache = handlesToClose.stream()
            .filter(h -> h instanceof Cache)
            .map(h -> (Cache) h)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Block cache not found in handlesToClose"));

        BlockBasedTableConfig tableConfig = new BlockBasedTableConfig()
            .setCacheIndexAndFilterBlocks(false)
            .setCacheIndexAndFilterBlocksWithHighPriority(false)
            .setPinL0FilterAndIndexBlocksInCache(false)
            .setPinTopLevelIndexAndFilter(false)
            .setBlockCache(blockCache);

        return currentOptions
            // Write Path Config
            .setWriteBufferSize(WRITE_BUFFER_SIZE)
            .setMaxWriteBufferNumber(MAX_WRITE_BUFFER_NUMBER)
            .setMinWriteBufferNumberToMerge(1)
            .setTargetFileSizeBase(TARGET_FILE_SIZE_BASE)

            // Compaction Trigger
            .setLevel0FileNumCompactionTrigger(L0_FILE_NUM_COMPACTION_TRIGGER)
            .setMaxBytesForLevelBase(MAX_BYTES_FOR_LEVEL_BASE)

            // To pin index/filter, but evict data blocks
            .setTableFormatConfig(tableConfig);
    }
}
