package com.macrohuang.fileq.util;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class FileUtilTest {
	@Test
	public void testDelete() throws IOException {
		File file = new File("test");
		if (!file.exists())
			file.createNewFile();
		Assert.assertNotNull(file);
		FileUtil.delete(file);
		Assert.assertFalse(file.exists());
	}

	@Test
	public void testDeleteDirectory() throws IOException {
		File file = new File("test");
		if (!file.exists() || !file.isDirectory())
			file.mkdirs();
		File file2 = new File(file.getAbsoluteFile() + File.separator + "test");
		if (!file2.exists())
			file2.createNewFile();
		Assert.assertNotNull(file);
		Assert.assertNotNull(file2);
		FileUtil.delete(file);
		Assert.assertFalse(file.exists());
		Assert.assertFalse(file2.exists());
	}

	@Test
	public void testCopyFileToDirectory() throws IOException {
		File file = new File("test");
		if (!file.exists() || !file.isDirectory())
			file.mkdirs();
		File file2 = new File("test2");
		if (!file2.exists())
			file2.createNewFile();
		Assert.assertNotNull(file);
		Assert.assertNotNull(file2);
		Assert.assertTrue(file.exists());
		Assert.assertTrue(file2.exists());
		FileUtil.copyFileToDirectory(file2, file);
		File file3 = new File(file.getAbsoluteFile() + File.separator + file2.getName());
		Assert.assertTrue(file3.exists());
		FileUtil.delete(file);
		Assert.assertFalse(file.exists());
		Assert.assertFalse(file3.exists());
		FileUtil.delete(file2);
		Assert.assertFalse(file2.exists());
	}
}
