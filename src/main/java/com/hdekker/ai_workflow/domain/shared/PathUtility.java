package com.hdekker.ai_workflow.domain.shared;

public class PathUtility {

    /**
     * Extracts the relative path starting from the specified classpath root folder.
     * * @param fullResourcePath The URI/URL string of the resource (e.g., jar:file:/.../classes/root-folder/file.txt)
     * @param rootFolderName The name of the folder inside the classpath (e.g., "root-folder")
     * @return The path relative to the root folder (e.g., "file.txt" or "sub/file.txt")
     */
    public static String getRelativePath(String fullResourcePath, String rootFolderName) {
        
        // 1. Define the search term: the folder name followed by a path separator.
        // We use a forward slash '/' as it's the standard path separator inside JARs and web contexts.
    	
    	String normalizedPath = fullResourcePath.replace('\\', '/');
    	
        String searchSegment = rootFolderName + "/"; 
        
        // 2. Find the starting index of the search segment.
        int startIndex = normalizedPath.indexOf(searchSegment);

        if (startIndex != -1) {
            // 3. Calculate the index where the relative path begins.
            // This is the start index PLUS the length of the search segment.
            int relativePathStart = startIndex + searchSegment.length();
            
            // 4. Cut the string to get the relative path.
            String relativePath = fullResourcePath.substring(relativePathStart);
            
            // OPTIONAL: Clean up any query parameters or fragments if present (though rare for classpath files)
            int queryIndex = relativePath.indexOf('?');
            if (queryIndex != -1) {
                relativePath = relativePath.substring(0, queryIndex);
            }
            
            return relativePath;
        } else {
            // Handle case where the root folder name is not found
            System.err.println("Error: Root folder '" + rootFolderName + "' not found in the full path.");
            return fullResourcePath; // Or throw an exception
        }
    }
}
