package com.taipei.iot.common.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptHashGenerator {

	public static void main(String[] args) {
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		String password = "Test1234!";
		String hash = encoder.encode(password);
		System.out.println("=== BCrypt Hash Generator ===");
		System.out.println("Password: " + password);
		System.out.println("Hash: " + hash);
		System.out.println("Verify: " + encoder.matches(password, hash));
	}

}
