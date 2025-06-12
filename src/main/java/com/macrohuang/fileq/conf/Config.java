package com.macrohuang.fileq.conf;

import com.macrohuang.fileq.codec.Codec;
import com.macrohuang.fileq.codec.impl.KryoCodec;

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

    public Config() {
        this.fileSize = 1024 * 1024 * 100;
        this.backup = true;
        this.basePath = System.getProperty("java.io.tmpdir", "/temp");
        this.filePrefix = "fileq_";
        this.fileSuffix = ".data";
        this.init = false;
        this.codec = new KryoCodec();
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
}
