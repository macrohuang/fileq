package com.macrohuang.fileq.util;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.macrohuang.fileq.conf.Config;

public class FileUtil {
	private static final void createBasePathIfNotExists(Config config) {
		File path = new File(config.getBasePath());
		if (!path.exists() || !path.isDirectory())
			path.mkdir();
	}

	public static final boolean isMetaExists(Config config) {
		return !new File(config.getBasePath() + File.separator + Config.DATA_DIR + File.separator + Config.META_FILE_NAME).exists();
	}

	public static final File getMetaFile(Config config) throws IOException {
		createBasePathIfNotExists(config);
		File path = new File(config.getBasePath() + File.separator + Config.DATA_DIR);
		if (!path.exists() || !path.isDirectory())
			path.mkdirs();

		File file = new File(path.getAbsolutePath() + File.separator + Config.META_FILE_NAME);
		if (!file.exists() || !file.isFile()) {
			file.createNewFile();
		}
		return file;
	}
	public static final File getDataFile(Config config, long seq) throws IOException {
		createBasePathIfNotExists(config);
		File path = new File(config.getBasePath() + File.separator + Config.DATA_DIR);
		if (!path.exists() || !path.isDirectory())
			path.mkdirs();

		File file = new File(path.getAbsolutePath() + File.separator + config.getFilePrefix() + String.format("%019d", seq)
				+ config.getFileSuffix());
		if (!file.exists() || !file.isFile())
			file.createNewFile();
		return file;
	}

	public static final File getBakFile(Config config, long seq) throws IOException {
		createBasePathIfNotExists(config);
		SimpleDateFormat pathPattern = new SimpleDateFormat("yyyy-MM-dd");
		File bakPath = new File(config.getBasePath() + File.separator + Config.BAK_DIR + File.separator
				+ pathPattern.format(new Date()));
		if (!bakPath.exists() || !bakPath.isDirectory())
			bakPath.mkdirs();
		File file = new File(bakPath.getAbsolutePath() + File.separator + config.getFilePrefix() + String.format("%019d", seq)
				+ config.getFileSuffix());
		if (!file.exists() || !file.isFile())
			file.createNewFile();
		return file;
	}
}
