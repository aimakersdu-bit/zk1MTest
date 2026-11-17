# ZooKeeper 生产排错与高频故障诊断手册

> **定位：** 本手册汇总各微服务团队在生产环境最常遇到的 ZooKeeper 疑难杂症、告警根因与排障止血 SOP。

## 故障 1：业务频繁告警 "Session Expired" (会话超时失效)
### 根本原因排查
90% 以上的 Session Expired 并非 ZooKeeper 服务端异常，而是**客户端应用发生长耗时停顿**：
1. **JVM Full GC (STW)：** 业务微服务发生 Full GC，线程全局挂起超过 `tickTime * 2`，导致心跳包无法按时发送；
2. **宿主机 CPU / 内存超载：** 宿主机 CPU 达到 100% 或发生 Swap 换页，心跳线程得不到调度；
3. **跨机房网络抖动：** 防火墙阻断长连接或网络单向丢包。

### 生产治理方案
- **JVM 垃圾收集器升级：** 核心交易微服务推荐迁移至 **ZGC** 或 **G1 GC**，配置 `-XX:MaxGCPauseMillis=200`，彻底消灭秒级 STW。
- **调整 sessionTimeout 参数：** 生产环境客户端超时时间不宜设得过短，推荐配置在 `20,000ms ~ 30,000ms`（20~30秒）。
- **客户端重连重试机制：** 监听 `KeeperState.Expired` 状态，必须重新构建 ZooKeeper 实例并重新注册临时节点与 Watcher。

---

## 故障 2：ZooKeeper 磁盘被打满导致进程崩溃
### 根本原因排查
ZooKeeper 的每次事务写操作均会按顺序追加到 `version-2/log.*` 事务日志文件中，并定期生成内存数据快照 (`snapshot.*`)。如果未启用定时清理机制，日志会持续膨胀，最终写满磁盘。

### 强制规约配置 (`zoo.cfg`)
生产环境必须在 `zoo.cfg` 中配置自动清理：
```properties
# 自动清理周期 (单位: 小时)
autopurge.purgeInterval=24

# 保留最近的历史快照与事务日志数量 (建议 3 ~ 5 份)
autopurge.snapRetainCount=5
```

---

## 故障 3：大报文写入报错 "Packet len is out of range!" (1MB 限制)
### 根本原因排查
ZooKeeper 内部序列化框架使用 Jute，默认对单个 ZNode 节点大小限制为 **1MB (1,048,576 字节)**。
若客户端尝试写入超过 1MB 的超大配置、日志或序列化对象，服务端会立刻抛出 `KeeperException.MarshallingError` 并强制断开连接。

### 处置原则
1. **架构设计规约：** **严禁将 ZooKeeper 当作数据库或大缓存使用！** ZNode 仅应存放元数据、轻量配置或实例地址。
2. **参数应急调大（治标不治本）：**
   若确实需要临时扩容至 2MB，必须在服务端与客户端启动参数中**同时增加**：
   ```bash
   -Djute.maxbuffer=2097152
   ```

---

## 故障 4：脑裂 (Split-Brain) 预防
- **根因：** 当网络发生机房分区时，若两半机房各自推举出 Leader 并接受写请求，将导致数据永久分叉。
- **防御措施：** 严格遵循过半原则（Quorum = N / 2 + 1）。仅包含多数节点的存活分区才能产生有效 Leader；少数派节点直接拒绝写服务，从数学上杜绝脑裂。

