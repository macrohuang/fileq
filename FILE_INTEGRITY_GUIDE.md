# FileQ 文件完整性检查与恢复指南

## 概述

FileQ 提供了强大的文件完整性检查和自动恢复功能，确保数据的可靠性和一致性。本指南详细介绍了完整性检查机制、恢复策略和最佳实践。

## 核心功能

### 1. 增强的校验和算法

FileQ 支持多种校验算法，提供不同级别的数据完整性保护：

#### 支持的校验类型

- **SIMPLE_LENGTH**: 简单长度校验（兼容旧版本）
- **CRC32**: CRC32校验，提供强数据完整性保护
- **ADLER32**: Adler32校验，平衡性能和可靠性

#### 自动校验类型推荐

系统会根据数据大小自动推荐最适合的校验算法：

```java
// 小数据 (< 1KB) -> CRC32 (高精度)
// 中等数据 (1KB - 1MB) -> Adler32 (平衡性能)
// 大数据 (> 1MB) -> SIMPLE_LENGTH (高性能)

ChecksumType recommended = EnhancedChecksumUtil.getRecommendedChecksumType(dataSize);
```

### 2. 文件完整性检查

#### 基本完整性检查

```java
try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {
    IntegrityCheckResult result = FileIntegrityChecker.checkFileIntegrity(channel, 0, -1);
    
    if (result.isValid()) {
        System.out.println("文件完整性检查通过");
        System.out.println("有效数据块: " + result.getValidBlockCount());
        System.out.println("有效数据大小: " + result.getValidDataSize() + " bytes");
    } else {
        System.out.println("发现损坏数据块: " + result.getCorruptedBlockCount());
        System.out.println("详细信息: " + result.getSummary());
    }
}
```

#### 检查结果信息

```java
IntegrityCheckResult result = // ... 执行检查
System.out.println("检查结果摘要: " + result.getSummary());
System.out.println("总检查大小: " + result.getTotalSize());
System.out.println("有效数据大小: " + result.getValidDataSize());

// 获取详细的数据块信息
for (DataBlockInfo block : result.getValidBlocks()) {
    System.out.println("有效块位置: " + block.getPosition() + 
                      ", 大小: " + block.getTotalSize() + 
                      ", 校验类型: " + block.getChecksumType());
}

for (DataBlockInfo block : result.getCorruptedBlocks()) {
    System.out.println("损坏块位置: " + block.getPosition() + 
                      ", 错误: " + block.getErrorMessage());
}
```

### 3. 文件恢复机制

#### 恢复策略

FileQ 提供多种恢复策略：

1. **SKIP_CORRUPTED**: 跳过损坏的数据块，保留有效数据
2. **TRUNCATE_TO_LAST_VALID**: 截断文件到最后一个有效数据块
3. **ATTEMPT_REPAIR**: 尝试修复损坏的数据块
4. **BACKUP_AND_RECOVER**: 创建备份后进行恢复

#### 执行文件恢复

```java
// 检查文件完整性
IntegrityCheckResult checkResult;
try (FileChannel sourceChannel = FileChannel.open(sourceFile, StandardOpenOption.READ)) {
    checkResult = FileIntegrityChecker.checkFileIntegrity(sourceChannel, 0, -1);
}

// 执行恢复
if (!checkResult.isValid()) {
    try (FileChannel sourceChannel = FileChannel.open(sourceFile, StandardOpenOption.READ)) {
        RecoveryResult recoveryResult = FileRecoveryManager.recoverFile(
            sourceChannel, 
            "recovered_file.data", 
            checkResult, 
            RecoveryStrategy.BACKUP_AND_RECOVER
        );
        
        if (recoveryResult.isSuccess()) {
            System.out.println("恢复成功: " + recoveryResult.getMessage());
            System.out.println("恢复的数据块: " + recoveryResult.getRecoveredBlocks());
            System.out.println("跳过的数据块: " + recoveryResult.getSkippedBlocks());
            System.out.println("恢复的数据大小: " + recoveryResult.getRecoveredDataSize());
            
            if (recoveryResult.getBackupPath() != null) {
                System.out.println("备份文件: " + recoveryResult.getBackupPath());
            }
        }
    }
}
```

#### 验证恢复结果

```java
boolean isValid = FileRecoveryManager.validateRecoveredFile("recovered_file.data");
if (isValid) {
    System.out.println("恢复文件验证通过");
} else {
    System.out.println("恢复文件仍有问题，需要进一步处理");
}
```

### 4. 集成完整性检查的FileQueue

#### 基本使用

```java
// 创建具有完整性检查功能的队列
IntegrityAwareFileQueueImpl<String> queue = new IntegrityAwareFileQueueImpl<>(config);

// 添加数据（自动使用强校验）
queue.add("重要数据1");
queue.add("重要数据2");

// 读取数据（自动验证完整性）
String data1 = queue.take();
String data2 = queue.take();
```

#### 高级配置

```java
Config config = new Config();
config.setBasePath("/data/fileq");
config.setFileSize(100 * 1024 * 1024); // 100MB

// 创建具有自定义完整性设置的队列
IntegrityAwareFileQueueImpl<MyData> queue = new IntegrityAwareFileQueueImpl<>(
    config,
    true,                                    // 启用强校验
    ChecksumType.CRC32,                     // 使用CRC32校验
    true,                                   // 启用自动恢复
    RecoveryStrategy.BACKUP_AND_RECOVER,    // 恢复策略
    1000                                    // 每1000次操作检查一次完整性
);

// 手动触发完整性检查
IntegrityCheckResult result = queue.checkIntegrity();
if (!result.isValid()) {
    System.out.println("发现完整性问题: " + result.getSummary());
    
    // 手动触发恢复
    RecoveryResult recovery = queue.recoverFile(
        "/backup/recovered_queue.data", 
        RecoveryStrategy.SKIP_CORRUPTED
    );
    
    if (recovery.isSuccess()) {
        System.out.println("手动恢复成功");
    }
}
```

### 5. 备份管理

#### 自动备份清理

```java
// 清理旧备份，只保留最新的5个
FileRecoveryManager.cleanupOldBackups("/data/fileq/queue.data", 5);
```

#### 备份文件命名规则

备份文件使用时间戳命名：
```
原文件: /data/fileq/queue.data
备份文件: /data/fileq/queue.data.backup_20231201_143022
```

## 性能考虑

### 1. 校验算法性能对比

| 校验类型 | 计算速度 | 检测能力 | 适用场景 |
|---------|---------|---------|---------|
| SIMPLE_LENGTH | 最快 | 基本 | 大文件、高性能要求 |
| ADLER32 | 快 | 良好 | 中等文件、平衡需求 |
| CRC32 | 中等 | 优秀 | 小文件、高可靠性要求 |

### 2. 完整性检查频率

- **启动检查**: 队列启动时自动执行
- **定期检查**: 可配置每N次操作后执行
- **手动检查**: 随时可以手动触发

建议配置：
- 高可靠性场景：每100次操作检查一次
- 平衡场景：每1000次操作检查一次
- 高性能场景：每10000次操作检查一次

### 3. 内存使用优化

完整性检查使用流式处理，内存使用量与文件大小无关，适合处理大文件。

## 最佳实践

### 1. 生产环境配置

```java
Config config = new Config();
config.setBasePath("/data/fileq");
config.setFileSize(100 * 1024 * 1024);

// 生产环境推荐配置
IntegrityAwareFileQueueImpl<MyData> queue = new IntegrityAwareFileQueueImpl<>(
    config,
    true,                                    // 启用强校验
    ChecksumType.ADLER32,                   // 平衡性能和可靠性
    true,                                   // 启用自动恢复
    RecoveryStrategy.BACKUP_AND_RECOVER,    // 创建备份后恢复
    5000                                    // 每5000次操作检查一次
);
```

### 2. 监控和告警

```java
// 定期检查队列健康状态
public void healthCheck() {
    IntegrityCheckResult result = queue.checkIntegrity();
    
    if (!result.isValid()) {
        // 发送告警
        alertService.sendAlert("FileQ完整性检查失败: " + result.getSummary());
        
        // 记录详细日志
        logger.error("发现{}个损坏数据块，{}个有效数据块", 
                    result.getCorruptedBlockCount(), 
                    result.getValidBlockCount());
    }
}
```

### 3. 灾难恢复流程

1. **检测问题**: 通过完整性检查发现损坏
2. **创建备份**: 保留原始文件
3. **执行恢复**: 使用适当的恢复策略
4. **验证结果**: 确认恢复文件的完整性
5. **切换使用**: 将恢复文件投入使用
6. **清理备份**: 定期清理旧备份文件

### 4. 错误处理

```java
try {
    queue.add(data);
} catch (FileQueueIOException e) {
    if (e.getCause() instanceof CheckSumFailException) {
        // 校验失败，可能需要恢复
        logger.error("数据校验失败，触发恢复流程", e);
        triggerRecovery();
    } else {
        // 其他IO错误
        logger.error("文件IO错误", e);
    }
}
```

## 故障排除

### 常见问题

1. **校验失败但文件看起来正常**
   - 可能是并发写入导致的临时不一致
   - 建议等待一段时间后重新检查

2. **恢复后数据丢失**
   - 检查恢复策略是否合适
   - 考虑使用TRUNCATE_TO_LAST_VALID策略

3. **性能下降**
   - 调整完整性检查频率
   - 考虑使用更快的校验算法

### 调试工具

```java
// 启用详细日志
Logger logger = LoggerFactory.getLogger(IntegrityAwareFileQueueImpl.class);
logger.setLevel(Level.DEBUG);

// 获取详细统计信息
System.out.println("强校验启用: " + queue.isStrongChecksumEnabled());
System.out.println("校验类型: " + queue.getChecksumType());
System.out.println("自动恢复: " + queue.isAutoRecoveryEnabled());
System.out.println("检查间隔: " + queue.getIntegrityCheckInterval());
```

## 总结

FileQ的文件完整性检查与恢复功能提供了：

- **多层次保护**: 从简单校验到强加密校验
- **自动化恢复**: 检测到问题时自动恢复
- **灵活配置**: 根据需求调整性能和可靠性平衡
- **生产就绪**: 完整的监控、告警和故障恢复机制

通过合理配置和使用这些功能，可以显著提升FileQ在生产环境中的数据可靠性和系统稳定性。 