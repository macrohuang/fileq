package com.macrohuang.fileq.conf;

import com.macrohuang.fileq.codec.Codec;
import com.macrohuang.fileq.codec.impl.KryoCodec;


public class Config {
	public static final String META_FILE_NAME = ".meta";
	private int fileSize = 1024 * 1024 * 100;
	private boolean backup = true;
	private String basePath = System.getProperty("java.io.tmpdir", "/temp");
	private String filePrefix = "fileq_";
	private String fileSuffix = ".data";
	private boolean init = false;
	public static final String DATA_DIR = "data";
	public static final String BAK_DIR = "bak";
	private Codec codec = new KryoCodec();
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
	public String getFilePrefix() {
		return filePrefix;
	}

	public void setFilePrefix(String queueFilePrefix) {
		this.filePrefix = queueFilePrefix;
	}

	public String getFileSuffix() {
		return fileSuffix;
	}

	public void setFileSuffix(String queueFileSuffix) {
		this.fileSuffix = queueFileSuffix;
	}

	public boolean isInit() {
		return init;
	}

	public void setInit(boolean init) {
		this.init = init;
	}

	public Codec getCodec() {
		return codec;
	}

	public void setCodec(Codec codec) {
		this.codec = codec;
	}
}
