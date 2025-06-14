package com.macrohuang.fileq.conf;

import java.io.File;
import com.macrohuang.fileq.codec.Codec;
import com.macrohuang.fileq.codec.impl.KryoCodec;
import com.macrohuang.fileq.concurrent.ConcurrencyStrategy;

/**
 * Configuration class for FileQueue instances that controls all aspects of
 * queue behavior, performance, and storage characteristics.
 * 
 * <p>This class provides a fluent configuration API with sensible defaults
 * for all settings. Key configuration areas include:</p>
 * 
 * <ul>
 *   <li><strong>Storage Configuration</strong> - File paths, sizes, and naming</li>
 *   <li><strong>Concurrency Strategy</strong> - Thread-safety and performance tuning</li>
 *   <li><strong>Serialization</strong> - Codec selection for object persistence</li>
 *   <li><strong>Backup and Recovery</strong> - Data safety and integrity features</li>
 * </ul>
 * 
 * <h3>Quick Start Example</h3>
 * <pre>{@code
 * Config config = new Config();
 * config.setBasePath("/data/queues/myapp");
 * config.setFileSize(50 * 1024 * 1024); // 50MB files
 * config.setConcurrencyMode(AccessMode.READ_WRITE_LOCK);
 * config.setCodec(new EnhancedKryoCodec());
 * 
 * FileQueue<MyObject> queue = new EnhancedFileQueueImpl<>(config);
 * }</pre>
 * 
 * <h3>Performance Tuning</h3>
 * <p>For optimal performance, consider these settings:</p>
 * <ul>
 *   <li><strong>File Size</strong> - Larger files reduce file rotation overhead</li>
 *   <li><strong>Concurrency Mode</strong> - Match your read/write thread ratio</li>
 *   <li><strong>Codec Choice</strong> - EnhancedKryoCodec for speed, standard for compatibility</li>
 *   <li><strong>Fair Locking</strong> - Enable for strict ordering, disable for throughput</li>
 * </ul>
 * 
 * <h3>Thread Safety</h3>
 * <p>Config instances are <strong>not</strong> thread-safe and should be fully
 * configured before passing to FileQueue constructors. Once a queue is created,
 * the configuration should not be modified.</p>
 * 
 * @author macro
 * @version 2.0
 * @since 1.0
 * @see com.macrohuang.fileq.impl.EnhancedFileQueueImpl
 * @see com.macrohuang.fileq.concurrent.ConcurrencyStrategy.AccessMode
 * @see com.macrohuang.fileq.codec.impl.EnhancedKryoCodec
 */
public class Config {
    /** Default meta file name used across all queue instances */
    public static final String META_FILE_NAME = ".meta";
    /** Directory name for data files within the queue base path */
    public static final String DATA_DIR = "data";
    /** Directory name for backup files within the queue base path */
    public static final String BAK_DIR = "bak";

    /** Maximum size in bytes for each data file before rotation */
    private int fileSize;
    /** Whether to create backup files during data file rotation */
    private boolean backup;
    /** Base directory path where all queue files are stored */
    private String basePath;
    /** Prefix for data file names */
    private String filePrefix;
    /** Suffix for data file names */
    private String fileSuffix;
    /** Whether to initialize/clear the queue on first access */
    private boolean init;
    /** Serialization codec for object encoding/decoding */
    private Codec codec;
    
    // Concurrency strategy configuration
    /** Concurrency access mode determining locking strategy */
    private ConcurrencyStrategy.AccessMode concurrencyMode;
    /** Whether to use fair locking (FIFO) or unfair (performance) */
    private boolean fairLock;
    /** Expected number of concurrent reader threads for optimization */
    private int expectedReadThreads;
    /** Expected number of concurrent writer threads for optimization */
    private int expectedWriteThreads;
    /** Whether this queue will be accessed from multiple processes */
    private boolean multiProcessAccess;

    /**
     * Creates a new Config instance with sensible default values.
     * 
     * <p>Default configuration:</p>
     * <ul>
     *   <li>File size: 100MB per data file</li>
     *   <li>Base path: System temp directory + "fileq"</li>
     *   <li>Backup enabled</li>
     *   <li>Reentrant lock concurrency strategy</li>
     *   <li>Standard Kryo codec</li>
     *   <li>Single reader/writer thread expected</li>
     * </ul>
     */
    public Config() {
        this.fileSize = FileConstants.DEFAULT_FILE_SIZE_BYTES;
        this.backup = true;
        this.basePath = System.getProperty("java.io.tmpdir", System.getProperty("java.io.tmpdir") + File.separator + "fileq");
        this.filePrefix = "fileq_";
        this.fileSuffix = ".data";
        this.init = false;
        this.codec = new KryoCodec();
        
        // 并发策略默认配置
        this.concurrencyMode = ConcurrencyStrategy.AccessMode.REENTRANT_LOCK;
        this.fairLock = false;
        this.expectedReadThreads = 1;
        this.expectedWriteThreads = 1;
        this.multiProcessAccess = false;
    }

    /**
     * Creates a new Config instance with specified core parameters.
     * 
     * <p>Concurrency settings will use defaults (ReentrantLock, single thread).
     * Use the setters to customize concurrency behavior after construction.</p>
     * 
     * @param fileSize maximum size in bytes for each data file
     * @param backup whether to enable backup file creation
     * @param basePath directory path where queue files will be stored
     * @param filePrefix prefix for data file names
     * @param fileSuffix suffix for data file names
     * @param init whether to initialize/clear queue on first access
     * @param codec serialization codec for object persistence
     */
    public Config(int fileSize, boolean backup, String basePath, String filePrefix, 
                  String fileSuffix, boolean init, Codec codec) {
        this.fileSize = fileSize;
        this.backup = backup;
        this.basePath = basePath;
        this.filePrefix = filePrefix;
        this.fileSuffix = fileSuffix;
        this.init = init;
        this.codec = codec;
    }

    // Getters
    public int getFileSize() {
        return fileSize;
    }

    public boolean isBackup() {
        return backup;
    }

    public String getBasePath() {
        return basePath;
    }

    public String getFilePrefix() {
        return filePrefix;
    }

    public String getFileSuffix() {
        return fileSuffix;
    }

    public boolean isInit() {
        return init;
    }

    public Codec getCodec() {
        return codec;
    }

    // Setters
    public void setFileSize(int fileSize) {
        this.fileSize = fileSize;
    }

    public void setBackup(boolean backup) {
        this.backup = backup;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public void setFilePrefix(String filePrefix) {
        this.filePrefix = filePrefix;
    }

    public void setFileSuffix(String fileSuffix) {
        this.fileSuffix = fileSuffix;
    }

    public void setInit(boolean init) {
        this.init = init;
    }

    public void setCodec(Codec codec) {
        this.codec = codec;
    }
    
    // 并发策略配置的 Getters 和 Setters
    public ConcurrencyStrategy.AccessMode getConcurrencyMode() {
        return concurrencyMode;
    }
    
    public void setConcurrencyMode(ConcurrencyStrategy.AccessMode concurrencyMode) {
        this.concurrencyMode = concurrencyMode;
    }
    
    public boolean isFairLock() {
        return fairLock;
    }
    
    public void setFairLock(boolean fairLock) {
        this.fairLock = fairLock;
    }
    
    public int getExpectedReadThreads() {
        return expectedReadThreads;
    }
    
    public void setExpectedReadThreads(int expectedReadThreads) {
        this.expectedReadThreads = expectedReadThreads;
    }
    
    public int getExpectedWriteThreads() {
        return expectedWriteThreads;
    }
    
    public void setExpectedWriteThreads(int expectedWriteThreads) {
        this.expectedWriteThreads = expectedWriteThreads;
    }
    
    public boolean isMultiProcessAccess() {
        return multiProcessAccess;
    }
    
    public void setMultiProcessAccess(boolean multiProcessAccess) {
        this.multiProcessAccess = multiProcessAccess;
    }
}
