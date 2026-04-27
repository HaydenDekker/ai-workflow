package com.hdekker.ai_workflow.files;

import java.io.FilenameFilter;
import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link DirectoryScanner} that retries directory access on Windows.
 * <p>
 * On Windows, {@code Files.createDirectory()} may return before the directory is
 * fully visible to other processes. This scanner retries once on first access,
 * then caches the result so all subsequent polls are fast.
 * <p>
 * This class replaces Spring Integration's {@code DefaultDirectoryScanner} to
 * eliminate the dependency on Spring Integration for file scanning.
 */
public class RetryableDirectoryScanner implements DirectoryScanner {

	private static final Logger log = LoggerFactory.getLogger(RetryableDirectoryScanner.class);

	private static final int MAX_RETRIES = 50;
	private static final long RETRY_INTERVAL_MS = 100;

	// Only retry once; subsequent polls call through directly
	private volatile boolean directoryVerified = false;

	// Filter for filtering files
	private FilenameFilter filter;

	/**
	 * Add retry logic for Windows file system timing issues.
	 * Retries only on the first access; all subsequent calls skip retry.
	 */
	@Override
	public File[] listEligibleFiles(File directory) {
		if (directoryVerified) {
			return doList(directory);
		}
		File file = directory;
		int retries = MAX_RETRIES;
		while (retries > 0) {
			if (file.exists() && file.isDirectory() && file.canRead()) {
				break;
			}
			try {
				Thread.sleep(RETRY_INTERVAL_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
			retries--;
		}
		directoryVerified = true;
		if (!file.exists() || !file.isDirectory() || !file.canRead()) {
			log.warn("Directory not accessible after retries: {}. exists={}, isDir={}, canRead={}",
					directory, file.exists(), file.isDirectory(), file.canRead());
			return null;
		}
		return doList(directory);
	}

	private File[] doList(File dir) {
		File[] files = dir.listFiles();
		if (filter != null && files != null) {
			return applyFilter(files, dir);
		}
		return files;
	}

	private File[] applyFilter(File[] files, File dir) {
		int count = 0;
		for (File f : files) {
			if (filter.accept(dir, f.getName())) {
				count++;
			}
		}
		File[] result = new File[count];
		int i = 0;
		for (File f : files) {
			if (filter.accept(dir, f.getName())) {
				result[i++] = f;
			}
		}
		return result;
	}

	/**
	 * Set a filename filter. Used when the scanner is created from a Spring
	 * Integration context that calls {@code setFilter}.
	 */
	public void setFilter(FilenameFilter filter) {
		this.filter = filter;
	}
}
