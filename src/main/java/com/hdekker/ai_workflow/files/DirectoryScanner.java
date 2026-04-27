package com.hdekker.ai_workflow.files;

import java.io.File;

/**
 * Strategy for listing eligible files in a directory.
 * <p>
 * This interface replaces Spring Integration's {@code DefaultDirectoryScanner}
 * to eliminate the dependency on Spring Integration for file scanning.
 */
@FunctionalInterface
public interface DirectoryScanner {

	/**
	 * Lists eligible files in the given directory.
	 *
	 * @param directory the directory to scan
	 * @return an array of files, or {@code null} if the directory is not accessible
	 */
	File[] listEligibleFiles(File directory);
}
