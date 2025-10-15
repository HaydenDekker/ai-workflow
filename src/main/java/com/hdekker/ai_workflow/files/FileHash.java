package com.hdekker.ai_workflow.files;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileHash {

	public String hash(String file) {
		
		MessageDigest instance = null;
		try {
			instance = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		byte[] hashBytes = instance.digest(file.getBytes());
		
		StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
	
	}
	
	

}
