package com.openwiki.demo.config;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

/**
 * 场景 3：ZooKeeper 分布式配置中心与动态热更新实战
 * <p>
 * 核心机制：
 * 1. 在 /configs/app_config 下保存 JSON 或 Properties 配置文本；
 * 2. 客户端启动时读取数据并注册持久 Watcher；
 * 3. 当运维或发布系统修改该节点内容时，服务端触发 NodeDataChanged 事件通知所有微服务客户端；
 * 4. 客户端实现零重启热加载。
 */
public class ConfigCenterDemo {

    private static final String CONFIG_NODE = "/configs/app_config";
    private final ZooKeeper zk;
    private volatile String cachedConfig;

    public ConfigCenterDemo(ZooKeeper zk) {
        this.zk = zk;
    }

    /**
     * 初始化并订阅配置
     */
    public void initAndWatch() throws Exception {
        ensureNode();

        // 读取当前数据并绑定持续监听 Watcher
        byte[] data = zk.getData(CONFIG_NODE, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if (event.getType() == Event.EventType.NodeDataChanged) {
                    System.out.println("⚡ [ConfigCenter] 检测到配置节点发生变更！重新拉取最新配置...");
                    try {
                        // 重新读取并持续监听
                        byte[] latestData = zk.getData(CONFIG_NODE, this, null);
                        cachedConfig = new String(latestData, StandardCharsets.UTF_8);
                        System.out.println("✅ [ConfigCenter] 最新配置已热更新并生效:\n" + cachedConfig);
                    } catch (Exception e) {
                        System.err.println("❌ [ConfigCenter] 读取配置失败: " + e.getMessage());
                    }
                }
            }
        }, null);

        cachedConfig = new String(data, StandardCharsets.UTF_8);
        System.out.println("🚀 [ConfigCenter] 初始加载配置成功:\n" + cachedConfig);
    }

    /**
     * 模拟运维人员发布新配置
     */
    public void publishConfig(String newConfig) throws Exception {
        zk.setData(CONFIG_NODE, newConfig.getBytes(StandardCharsets.UTF_8), -1);
        System.out.println("📢 [ConfigCenter] 运维端已成功发布新配置");
    }

    private void ensureNode() throws Exception {
        String root = "/configs";
        if (zk.exists(root, false) == null) {
            try {
                zk.create(root, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (KeeperException.NodeExistsException ignored) {
            }
        }
        if (zk.exists(CONFIG_NODE, false) == null) {
            String initial = "timeout_ms=5000\nmax_connections=200\nfeature_flag_audit=true";
            try {
                zk.create(CONFIG_NODE, initial.getBytes(StandardCharsets.UTF_8), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (KeeperException.NodeExistsException ignored) {
            }
        }
    }

    public String getCachedConfig() {
        return cachedConfig;
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        ZooKeeper zkClient = new ZooKeeper("127.0.0.1:2181", 3000, event -> {
            if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                connected.countDown();
            }
        });
        connected.await();

        ConfigCenterDemo center = new ConfigCenterDemo(zkClient);
        center.initAndWatch();

        // 模拟 1 秒后热更新
        Thread.sleep(1000);
        center.publishConfig("timeout_ms=8000\nmax_connections=500\nfeature_flag_audit=false");

        Thread.sleep(1000);
        zkClient.close();
    }
}

