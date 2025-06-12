package com.macrohuang.fileq.conf;

import java.io.File;
import com.macrohuang.fileq.codec.Codec;
import com.macrohuang.fileq.codec.impl.KryoCodec;
import com.macrohuang.fileq.concurrent.ConcurrencyStrategy;

public class Config {
    public static final String META_FILE_NAME = ".meta";
    public static final String DATA_DIR = "data";
    public static final String BAK_DIR = "bak";

    private int fileSize;
    private boolean backup;
    private String basePath;
    private String filePrefix;
    private String fileSuffix;
    private boolean init;
    private Codec codec;
    
    // 并发策略配置
    private ConcurrencyStrategy.AccessMode concurrencyMode;
    private boolean fairLock;
    private int expectedReadThreads;
    private int expectedWriteThreads;
    private boolean multiProcessAccess;

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
