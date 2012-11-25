package com.macrohuang.fileq.util;

/**
 * 
 * @author macro
 *
 */
public class IntByteArrayConverter {
	/**
	 * Convert an integer to 4 big-end bytes.
	 * @param a
	 * @return
	 */
    public static byte[] int2ByteArr(int a){
       return new byte[] { (byte) ((a & 0xff000000) >> 24), (byte) ((a & 0x00ff0000) >> 16),
                (byte) ((a & 0x0000ff00) >> 8), (byte) ((a & 0x000000ff)) };
    }

    /**
     * Convert an 4 big-end byte array to an integer.
     * @param bytes The big-end encoding bytes of an integer.
     * @return
     */
    public static int byteArr2Int(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Required 4 bytes, but receive null");
        }
        if (bytes.length != 4) {
            throw new IllegalArgumentException("Required 4 bytes, but receive " + bytes.length
                    + " bytes");
        }
        if (bytes[0] > 0x7f){
        	throw new NumberFormatException("An integer's biggest byte can't more than 0x7f");
        }
        return bytes[0] << 24 | bytes[1] << 16 & 0x00ff0000 | bytes[2] << 8 & 0x0000ff00 | bytes[3]
                & 0x000000ff;
    }
}
