package com.macrohuang.fileq.conf;

public class Config {
	private int sizePerFile;
	private boolean backup;
	private String queueFilePath;
	private long currentIndex;
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
	public long getCurrentIndex() {
		return currentIndex;
	}
	public void setCurrentIndex(long currentIndex) {
		this.currentIndex = currentIndex;
	}
}
