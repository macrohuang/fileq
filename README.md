fileq
=====

A file base queue, using file channel, mmap and less meta info. 

## Features

- High performance file-based queue implementation
- Uses Java NIO FileChannel and memory mapping for optimal I/O performance
- Thread-safe operations with ReentrantLock
- Configurable file size and backup options
- Multiple codec support (Kryo, Default Object Serialization)
- Comprehensive test coverage

## Performance

### 测试环境

- **CPU**：Apple M3
- **内存**：24GB
- **操作系统**：macOS 15.5
- **JDK版本**：Java 17
- **磁盘**：SSD

### 测试方法

- 顺序写入：1KB消息，单线程持续写入10万次，统计总耗时与平均吞吐量
- 顺序读取：1KB消息，单线程持续读取10万次，统计总耗时与平均吞吐量
- 并发策略对比：1000次操作，分别测试ReadWriteLock、ReentrantLock、SingleThread三种模式
- 编解码器对比：对比KryoCodec与EnhancedKryoCodec的序列化/反序列化性能

### 最新性能数据

| 场景         | 吞吐量（写） | 吞吐量（读） | 备注                |
|--------------|-------------|-------------|---------------------|
| 单线程       | 14,170 ops/s| 9,067 ops/s | 1KB消息，SSD        |

- [Write]Time spend 7060 ms for 100000 times. Avg msg length 1024bytes
- [Read]Time spend 11025 ms for 100000 times. Avg msg length 1024bytes

#### 并发策略对比（1000次操作，EnhancedFileQueueImpl）
| 并发策略         | 1000次操作耗时 | 备注                |
|------------------|---------------|---------------------|
| ReadWriteLock    | 41.04 ms      | 推荐，读多写少场景  |
| ReentrantLock    | 591.55 ms     | 平衡读写场景        |
| SingleThread     | 378.07 ms     | 单线程极致性能      |

#### 编解码器对比
| 编解码器         | 序列化时间 | 反序列化时间 |
|------------------|------------|--------------|
| KryoCodec        | 17,039 μs  | 4,929 μs     |
| EnhancedKryoCodec|    552 μs  |    48 μs     |

- EnhancedKryoCodec序列化速度提升约97%，反序列化提升约99%

### 结论与建议

- FileQ在Apple M3+SSD+Java 17环境下，单线程顺序写入/读取性能分别可达1.4万/0.9万ops/s。
- EnhancedFileQueueImpl在并发场景下表现优异，ReadWriteLock模式强烈推荐。
- EnhancedKryoCodec极大提升了序列化性能，强烈推荐。
- 性能数据会随硬件、JVM参数、磁盘类型等变化，建议在目标环境下自行基准测试。

## Requirements

- Java 17 or higher
- Maven 3.6+

## Quick Start

```java
import com.macrohuang.fileq.FileQueue;
import com.macrohuang.fileq.impl.EnhancedFileQueueImpl;
import com.macrohuang.fileq.conf.Config;

// Create configuration
Config config = new Config();
config.setBasePath("/tmp/myqueue");
config.setFileSize(1024 * 1024 * 100); // 100MB per file

// Create queue
FileQueue<String> queue = new EnhancedFileQueueImpl<>(config);

// Add items
queue.add("Hello");
queue.add("World");

// Retrieve items
String item1 = queue.take(); // "Hello"
String item2 = queue.take(); // "World"

// Clean up
queue.close();
```

## Codec Options

### Default Kryo Codec
```java
Config config = new Config();
// Uses KryoCodec by default
```

### Enhanced Kryo Codec (Recommended for better performance)
```java
import com.macrohuang.fileq.codec.impl.EnhancedKryoCodec;

Config config = new Config();
config.setCodec(new EnhancedKryoCodec());
```

## Known Issues

### ✅ RESOLVED: Arrays.asList() Serialization Issue

**Previous Issue**: In earlier versions with Kryo 2.20, objects containing `Arrays.asList()` fields could not be properly deserialized.

**Solution**: This issue has been completely resolved by upgrading to Kryo 5.6.0. You can now safely use `Arrays.asList()` in your DTOs.

For detailed information about the solution, see [Kryo Serialization Guide](docs/KRYO_SERIALIZATION_GUIDE.md).

### Platform-Specific Issues

**Windows JDK 6 Memory Mapping Issue**: There is a bug in JDK 6 under Windows platform where memory regions cannot be unmapped when mapped multiple times. See [Oracle Bug Database](http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6521677) for details. This issue is resolved in newer JDK versions.

## Documentation

- [Kryo Serialization Guide](docs/KRYO_SERIALIZATION_GUIDE.md) - Detailed guide for serialization issues and solutions
- [TODO List](TODO.md) - Project roadmap and completed tasks

## Contributing

1. Fork the repository
2. Create your feature branch
3. Add tests for your changes
4. Ensure all tests pass
5. Submit a pull request

## License

This project is open source. Please check the license file for details.
