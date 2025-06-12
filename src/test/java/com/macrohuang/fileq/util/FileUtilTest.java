package com.macrohuang.fileq.util;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FileUtilTest {
	@Test
	public void testDelete() throws IOException {
		File file = new File("test");
		if (!file.exists())
			file.createNewFile();
		Assertions.assertNotNull(file);
		FileUtil.delete(file);
		Assertions.assertFalse(file.exists());
	}

	@Test
	public void testDeleteDirectory() throws IOException {
		File file = new File("test");
		if (!file.exists() || !file.isDirectory())
			file.mkdirs();
		File file2 = new File(file.getAbsoluteFile() + File.separator + "test");
		if (!file2.exists())
			file2.createNewFile();
		Assertions.assertNotNull(file);
		Assertions.assertNotNull(file2);
		FileUtil.delete(file);
		Assertions.assertFalse(file.exists());
		Assertions.assertFalse(file2.exists());
	}

	@Test
	public void testCopyFileToDirectory() throws IOException {
		File file = new File("test");
		if (!file.exists() || !file.isDirectory())
			file.mkdirs();
		File file2 = new File("test2");
		if (!file2.exists())
			file2.createNewFile();
		Assertions.assertNotNull(file);
		Assertions.assertNotNull(file2);
		Assertions.assertTrue(file.exists());
		Assertions.assertTrue(file2.exists());
		FileUtil.copyFileToDirectory(file2, file);
		File file3 = new File(file.getAbsoluteFile() + File.separator + file2.getName());
		Assertions.assertTrue(file3.exists());
		FileUtil.delete(file);
		Assertions.assertFalse(file.exists());
		Assertions.assertFalse(file3.exists());
		FileUtil.delete(file2);
		Assertions.assertFalse(file2.exists());
	}
}
