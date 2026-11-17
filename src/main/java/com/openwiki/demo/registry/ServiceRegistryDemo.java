package com.openwiki.demo.registry;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 场景 4：ZooKeeper 微服务注册中心与动态健康发现
 * <p>
 * 核心机制：
 * 1. 服务提供方启动时在 /services/{serviceName}/{ip:port} 下创建临时节点；
 * 2. 节点持有会话心跳，若服务崩溃或网络失联，会话超时后临时节点自动被集群摘除；
 * 3. 消费方监听该服务根节点下的子节点变化 (NodeChildrenChanged)，实现动态负载均衡列表自动刷新。
 */
public class ServiceRegistryDemo {

    private static final String REGISTRY_ROOT = "/services";
    private final ZooKeeper zk;

    public ServiceRegistryDemo(ZooKeeper zk) {
        this.zk = zk;
    }

    /**
     * 服务提供者：注册自身实例（临时节点）
     */
    public void registerInstance(String serviceName, String instanceAddress) throws Exception {
        ensureRoot(REGISTRY_ROOT + "/" + serviceName);

        String path = REGISTRY_ROOT + "/" + serviceName + "/" + instanceAddress;
        zk.create(path, "online".getBytes(StandardCharsets.UTF_8), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        System.out.println("🟢 [Registry] 实例已上线并注册: " + path);
    }

    /**
     * 服务消费者：发现服务实例并监听变更
     */
    public List<String> discoverAndWatch(String serviceName) throws Exception {
        String servicePath = REGISTRY_ROOT + "/" + serviceName;
        ensureRoot(servicePath);

        List<String> instances = zk.getChildren(servicePath, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if (event.getType() == Event.EventType.NodeChildrenChanged) {
                    System.out.println("🔄 [Registry] 检测到服务 [" + serviceName + "] 实例拓扑变更！重新拉取...");
                    try {
                        List<String> latest = zk.getChildren(servicePath, this);
                        System.out.println("✅ [Registry] 当前健康实例列表: " + latest);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        System.out.println("🔎 [Registry] 首次发现服务 [" + serviceName + "] 可用实例: " + instances);
        return instances;
    }

    private void ensureRoot(String path) throws Exception {
        String[] parts = path.split("/");
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            current.append("/").append(part);
            String p = current.toString();
            if (zk.exists(p, false) == null) {
                try {
                    zk.create(p, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                } catch (KeeperException.NodeExistsException ignored) {
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        ZooKeeper zkClient = new ZooKeeper("127.0.0.1:2181", 3000, event -> {
            if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                connected.countDown();
            }
        });
        connected.await();

        ServiceRegistryDemo demo = new ServiceRegistryDemo(zkClient);
        // 1. 消费者订阅
        demo.discoverAndWatch("order-service");

        // 2. 模拟服务实例启动上线
        demo.registerInstance("order-service", "192.168.1.100:8080");
        Thread.sleep(500);

        demo.registerInstance("order-service", "192.168.1.101:8080");
        Thread.sleep(1000);

        zkClient.close();
    }
}

