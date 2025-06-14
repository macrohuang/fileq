package com.macrohuang.fileq.util;

/**
 * Utility class for converting between numbers and byte arrays.
 * Provides efficient big-endian encoding/decoding for integers and longs.
 * Used internally by FileQueue for serializing metadata and position information.
 * 
 * @author macro
 */
public class NumberBytesConvertUtil {
	// Bit masks for extracting each byte from an integer (big-endian order)
	private static final int[] INT_BASE = new int[] { 0xff000000, 0x00ff0000, 0x0000ff00, 0x000000ff };
	
	// Bit masks for extracting each byte from a long (big-endian order)
	private static final long[] LONG_BASE = new long[] { 0xff00000000000000L, 0x00ff000000000000L, 0x0000ff0000000000L,
			0x000000ff00000000L, 0x00000000ff000000L, 0x0000000000ff0000L, 0x000000000000ff00L, 0x00000000000000ffL };
	
	// Size constants for type safety and clarity
	private static final int SIZE_OF_INT = 4;
	private static final int SIZE_OF_LONG = 8;

	/**
	 * Converts an integer to a 4-byte array using big-endian encoding.
	 * Most significant byte comes first in the resulting array.
	 * 
	 * @param a the integer to convert
	 * @return 4-byte array representing the integer in big-endian format
	 */
	public static byte[] int2ByteArr(int a) {
		byte[] result = new byte[SIZE_OF_INT];
		// Extract each byte from most significant to least significant
		for (int i = 0; i < SIZE_OF_INT; i++) {
			// Apply mask and shift to extract byte at position i
			result[i] = (byte) ((a & INT_BASE[i]) >> ((SIZE_OF_INT - 1 - i) * 8));
		}
		return result;
	}

	/**
	 * Convert some bytes (max to 4) to an integer.
	 * 
	 * @param bytes
	 *            The big-end encoding bytes of an integer.
	 * @return
	 */
	public static int byteArr2Int(byte[] bytes) {
		// Validate input parameters
		if (bytes == null || bytes.length == 0) {
			throw new IllegalArgumentException("Required at least one byte, but receive null or empty.");
		}
		// Ensure we don't overflow into negative numbers (sign bit check)
		if (bytes[0] > 0x7f) {
			throw new NumberFormatException("An integer's biggest byte can't be more than 0x7f");
		}
		
		int result = 0;
		// Reconstruct integer from big-endian byte array
		for (int i = 0; i < bytes.length; i++) {
			// Shift byte to correct position and apply appropriate mask
			result |= bytes[i] << ((bytes.length - 1 - i) * 8) & INT_BASE[INT_BASE.length - bytes.length + i];
		}
		return result;
	}

	/**
	 * Converts a long to an 8-byte array using big-endian encoding.
	 * Most significant byte comes first in the resulting array.
	 * 
	 * @param a the long to convert
	 * @return 8-byte array representing the long in big-endian format
	 */
	public static byte[] long2ByteArr(long a) {
		byte[] result = new byte[SIZE_OF_LONG];
		// Extract each byte from most significant to least significant
		for (int i = 0; i < SIZE_OF_LONG; i++) {
			// Apply mask and shift to extract byte at position i
			result[i] = (byte) ((a & LONG_BASE[i]) >> ((SIZE_OF_LONG - 1 - i) * 8));
		}
		return result;
	}

	/**
	 * Convert some bytes (max to 8) to a long integer.
	 * 
	 * @param bytes
	 *            The big-end encoding bytes of a long integer.
	 * @return
	 */
	public static long byteArr2Long(byte[] bytes) {
		// Validate input parameters
		if (bytes == null || bytes.length == 0 || bytes.length > SIZE_OF_LONG) {
			throw new IllegalArgumentException("Required at least one byte, at most " + SIZE_OF_LONG + " bytes.");
		}
		// Ensure we don't overflow into negative numbers (sign bit check)
		if (bytes[0] > 0x7f) {
			throw new NumberFormatException("An long integer's biggest byte can't be more than 0x7f");
		}
		
		long result = 0;
		// Reconstruct long from big-endian byte array
		for (int i = 0; i < bytes.length; i++) {
			// Cast to long before shift to prevent overflow, then apply mask
			result |= (long)bytes[i] << ((bytes.length - 1 - i) * 8) & LONG_BASE[LONG_BASE.length - bytes.length + i];
		}
		return result;
	}
}
