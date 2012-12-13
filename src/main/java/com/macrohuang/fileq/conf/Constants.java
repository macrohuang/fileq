package com.macrohuang.fileq.conf;

import com.macrohuang.fileq.util.NumberBytesConvertUtil;

public class Constants {
	public static final int DATA_META_SIZE = 16;
	public static final int DATA_CHECKSUM_SIZE = 16;
	public static final int MAGIC_NUMBER = 1314520;
	public static final byte[] LEADING_HEAD = NumberBytesConvertUtil.int2ByteArr(MAGIC_NUMBER);
	public static final byte PADDING = (byte) 0x7f;
	public static final int QUEUE_META_SIZE = 46;
	public final static int MAX_RETRY = 5;
}
