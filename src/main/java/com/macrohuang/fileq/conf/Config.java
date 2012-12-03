package com.macrohuang.fileq.conf;


public class Config {
	public static final String META_FILE_NAME = ".meta";
	private int fileSize = 1024 * 1024 * 100;
	private boolean backup = false;
	private String basePath = System.getProperty("java.io.tmpdir", "/temp");
	private String queueFilePrefix = "fileq_";
	private String queueFileSuffix = ".data";
	private boolean init = false;
	public static final String DATA_DIR = "data";
	public static final String BAK_DIR = "bak";
	public int getFileSize() {
		return fileSize;
	}
	public void setFileSize(int sizePerFile) {
		this.fileSize = sizePerFile;
	}
	public boolean isBackup() {
		return backup;
	}
	public void setBackup(boolean backup) {
		this.backup = backup;
	}

	public String getBasePath() {
		return basePath;
	}

	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}
	public String getQueueFilePrefix() {
		return queueFilePrefix;
	}

	public void setQueueFilePrefix(String queueFilePrefix) {
		this.queueFilePrefix = queueFilePrefix;
	}

	public String getQueueFileSuffix() {
		return queueFileSuffix;
	}

	public void setQueueFileSuffix(String queueFileSuffix) {
		this.queueFileSuffix = queueFileSuffix;
	}

	public boolean isInit() {
		return init;
	}

	public void setInit(boolean init) {
		this.init = init;
	}
}
