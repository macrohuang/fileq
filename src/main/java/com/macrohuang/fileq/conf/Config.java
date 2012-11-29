package com.macrohuang.fileq.conf;


public class Config {
	public static final String META_FILE_NAME = ".meta";
	private int sizePerFile = 1024 * 1024 * 100;
	private boolean backup = false;
	private String queueFilePath = System.getProperty("java.io.tmpdir", "/temp");
	private String queueFilePrefix = "fileq_";
	private String queueFileSuffix = ".data";
	public int getSizePerFile() {
		return sizePerFile;
	}
	public void setSizePerFile(int sizePerFile) {
		this.sizePerFile = sizePerFile;
	}
	public boolean isBackup() {
		return backup;
	}
	public void setBackup(boolean backup) {
		this.backup = backup;
	}
	public String getQueueFilePath() {
		return queueFilePath;
	}
	public void setQueueFilePath(String queueFilePath) {
		this.queueFilePath = queueFilePath;
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
}
