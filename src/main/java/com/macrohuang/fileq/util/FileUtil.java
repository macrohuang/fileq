package com.macrohuang.fileq.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.macrohuang.fileq.conf.Config;

/**
 * File system utility class for FileQueue operations.
 * Handles directory creation, file management, and backup operations.
 * 
 * @author macro
 */
public class FileUtil {
	/**
	 * Ensures the base directory exists for queue operations.
	 * Creates the directory if it doesn't exist.
	 */
	private static final void createBasePathIfNotExists(Config config) {
		File path = new File(config.getBasePath());
		// Create base directory if missing or not a directory
		if (!path.exists() || !path.isDirectory())
			path.mkdir();
	}

	/**
	 * Checks if the queue metadata file exists.
	 * Used to determine if this is a new queue or existing one.
	 * 
	 * @param config queue configuration
	 * @return true if this is a new queue (no metadata file exists)
	 */
	public static final boolean isMetaExists(Config config) {
		// Note: returns true for NEW queue (file doesn't exist)
		return !new File(config.getBasePath() + File.separator + Config.DATA_DIR + File.separator + Config.META_FILE_NAME).exists();
	}

	/**
	 * Gets or creates the metadata file for the queue.
	 * Creates necessary directory structure if missing.
	 * 
	 * @param config queue configuration
	 * @return File object for the metadata file
	 * @throws IOException if file creation fails
	 */
	public static final File getMetaFile(Config config) throws IOException {
		// Ensure base directory exists
		createBasePathIfNotExists(config);
		
		// Create data directory if needed
		File path = new File(config.getBasePath() + File.separator + Config.DATA_DIR);
		if (!path.exists() || !path.isDirectory())
			path.mkdirs();

		// Create metadata file if it doesn't exist
		File file = new File(path.getAbsolutePath() + File.separator + Config.META_FILE_NAME);
		if (!file.exists() || !file.isFile()) {
			file.createNewFile();
		}
		return file;
	}
	/**
	 * Gets or creates a data file for the specified sequence number.
	 * Uses zero-padded 19-digit sequence numbers for proper ordering.
	 * 
	 * @param config queue configuration
	 * @param seq sequence number for the data file
	 * @return File object for the data file
	 * @throws IOException if file creation fails
	 */
	public static final File getDataFile(Config config, long seq) throws IOException {
		createBasePathIfNotExists(config);
		
		// Ensure data directory exists
		File path = new File(config.getBasePath() + File.separator + Config.DATA_DIR);
		if (!path.exists() || !path.isDirectory())
			path.mkdirs();

		// Create filename with zero-padded sequence: prefix + 19-digit-seq + suffix
		File file = new File(path.getAbsolutePath() + File.separator + config.getFilePrefix() + String.format("%019d", seq)
				+ config.getFileSuffix());
		if (!file.exists() || !file.isFile())
			file.createNewFile();
		return file;
	}

	/**
	 * Gets or creates a backup file for the specified sequence number.
	 * Organizes backups by date for easy management and cleanup.
	 * 
	 * @param config queue configuration
	 * @param seq sequence number for the backup file
	 * @return File object for the backup file
	 * @throws IOException if file creation fails
	 */
	public static final File getBakFile(Config config, long seq) throws IOException {
		createBasePathIfNotExists(config);
		
		// Create date-based backup directory structure: bak/yyyy-MM-dd/
		SimpleDateFormat pathPattern = new SimpleDateFormat("yyyy-MM-dd");
		File bakPath = new File(config.getBasePath() + File.separator + Config.BAK_DIR + File.separator
				+ pathPattern.format(new Date()));
		if (!bakPath.exists() || !bakPath.isDirectory())
			bakPath.mkdirs();
		
		// Create backup file with same naming as data files
		File file = new File(bakPath.getAbsolutePath() + File.separator + config.getFilePrefix() + String.format("%019d", seq)
				+ config.getFileSuffix());
		if (!file.exists() || !file.isFile())
			file.createNewFile();
		return file;
	}

	/**
	 * Recursively deletes a file or directory and all its contents.
	 * 
	 * @param file the file or directory to delete
	 * @return true if all deletions succeeded, false if any failed
	 */
	public static boolean delete(File file) {
		boolean success = true;
		
		// Recursively delete directory contents first
		if (file.isDirectory()) {
			for (File child : file.listFiles()) {
				success &= delete(child);  // Delete each child recursively
			}
		}
		
		// Delete the file/directory itself
		success &= file.delete();
		return success;
	}

	/**
	 * Copies a file to the specified target directory.
	 * Uses proper resource management with try-with-resources.
	 * 
	 * @param sourceFile the file to copy
	 * @param targetDirectory the destination directory
	 * @return true if copy succeeded, false otherwise
	 */
	public static boolean copyFileToDirectory(File sourceFile, File targetDirectory) {
		if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
			return false;
		}
		
		try {
			// Ensure target directory exists
			if (!targetDirectory.exists() || !targetDirectory.isDirectory()) {
				targetDirectory.mkdirs();
			}
			
			File targetFile = new File(targetDirectory, sourceFile.getName());
			return copyFile(sourceFile, targetFile);
			
		} catch (Exception e) {
			// Silent failure for compatibility, but could be logged
			return false;
		}
	}
	
	/**
	 * Copies a file from source to target location.
	 * Uses NIO channels for efficient copying with proper resource management.
	 * 
	 * @param sourceFile source file to copy
	 * @param targetFile target file location
	 * @return true if copy succeeded, false otherwise
	 */
	public static boolean copyFile(File sourceFile, File targetFile) {
		try {
			// Create target file if it doesn't exist
			if (!targetFile.exists()) {
				targetFile.createNewFile();
			}
			
			// Use try-with-resources for automatic resource management
			try (FileInputStream inputStream = new FileInputStream(sourceFile);
				 FileChannel sourceChannel = inputStream.getChannel();
				 FileOutputStream outputStream = new FileOutputStream(targetFile);
				 FileChannel targetChannel = outputStream.getChannel()) {
				
				// Transfer entire file content efficiently
				sourceChannel.transferTo(0, sourceChannel.size(), targetChannel);
				return true;
			}
			
		} catch (IOException e) {
			// Silent failure for compatibility
			return false;
		}
	}

	/**
	 * Moves a file to the specified target directory.
	 * Performs copy then delete operation with proper error handling.
	 * 
	 * @param sourceFile the file to move
	 * @param targetDirectory the destination directory
	 * @return true if move succeeded, false otherwise
	 */
	public static boolean moveFileToDirectory(File sourceFile, File targetDirectory) {
		if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
			return false;
		}
		
		try {
			// Ensure target directory exists
			if (!targetDirectory.exists() || !targetDirectory.isDirectory()) {
				targetDirectory.mkdirs();
			}
			
			File targetFile = new File(targetDirectory, sourceFile.getName());
			
			// First copy the file
			if (copyFile(sourceFile, targetFile)) {
				// Only delete source if copy succeeded
				return delete(sourceFile);
			}
			
			return false;
			
		} catch (Exception e) {
			// Silent failure for compatibility
			return false;
		}
	}
}
