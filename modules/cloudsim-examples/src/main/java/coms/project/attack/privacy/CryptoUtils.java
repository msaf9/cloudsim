package coms.project.attack.privacy;

import java.security.Key;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;

/**
 * CryptoUtils provides basic AES encryption and decryption utilities:
 * - Initializes a symmetric key (AES).
 * - Supports encoding/decoding strings using Base64.
 */
public class CryptoUtils {

	private static Key key;

	/**
	 * Initializes AES encryption key:
	 * - Generates a new symmetric key using AES algorithm.
	 * - Must be called before encryption/decryption.
	 */
	public static void init() throws Exception {
		key = KeyGenerator.getInstance("AES").generateKey();
	}

	/**
	 * Encrypts plaintext string:
	 * - Uses AES algorithm and previously initialized key.
	 * - Returns Base64-encoded ciphertext.
	 */
	public static String encrypt(String data) throws Exception {
		Cipher c = Cipher.getInstance("AES");
		c.init(Cipher.ENCRYPT_MODE, key);
		return Base64.getEncoder().encodeToString(c.doFinal(data.getBytes()));
	}

	/**
	 * Decrypts Base64-encoded ciphertext:
	 * - Uses AES and the same key used for encryption.
	 * - Returns the original plaintext string.
	 */
	public static String decrypt(String base64Encrypted) throws Exception {
		Cipher c = Cipher.getInstance("AES");
		c.init(Cipher.DECRYPT_MODE, key);
		return new String(c.doFinal(Base64.getDecoder().decode(base64Encrypted)));
	}
}

