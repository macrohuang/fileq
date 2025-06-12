package com.macrohuang.fileq.conf;

import com.macrohuang.fileq.util.NumberBytesConvertUtil;

/**
 * FileQ核心常量定义
 * 
 * @author macro
 */
public class Constants {
	
	/**
	 * 数据元信息大小（字节）
	 * 包含数据长度、校验和等元信息
	 */
	public static final int DATA_META_SIZE = 16;
	
	/**
	 * 数据校验和大小（字节）
	 * 用于数据完整性验证
	 */
	public static final int DATA_CHECKSUM_SIZE = 16;
	
	/**
	 * FileQ文件格式魔数标识
	 * 用于识别有效的FileQ数据块头部
	 * 数字1314520作为文件格式的唯一标识符
	 */
	public static final int MAGIC_NUMBER = 1314520;
	
	/**
	 * 魔数对应的字节数组头部标识
	 */
	public static final byte[] LEADING_HEAD = NumberBytesConvertUtil.int2ByteArr(MAGIC_NUMBER);
	
	/**
	 * 填充字节值
	 * 用于数据对齐和填充空白区域
	 */
	public static final byte PADDING = (byte) 0x7f;
	
	/**
	 * 队列元信息大小（字节）
	 * 包含读写位置、对象计数等队列状态信息
	 */
	public static final int QUEUE_META_SIZE = 46;
	
	/**
	 * 最大重试次数
	 * 用于IO操作失败时的重试机制
	 */
	public final static int MAX_RETRY = 5;
}
