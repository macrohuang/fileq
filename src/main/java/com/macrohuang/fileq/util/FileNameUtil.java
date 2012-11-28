package com.macrohuang.fileq.util;

import java.io.File;

import com.macrohuang.fileq.conf.Config;

public class FileNameUtil {
	public static final String getFileName(Config config, long seq) {
		return config.getQueueFilePath() + File.separator + config.getQueueFilePrefix() + String.format("%019d", seq)
				+ config.getQueueFileSuffix();
	}
}
