package com.openwiki.demo.lock;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 场景 1：ZooKeeper 防惊群分布式锁实战规范
 * <p>
 * 核心机制：
 * 1. 临时顺序节点 (CreateMode.EPHEMERAL_SEQUENTIAL)
 * 2. 避免所有客户端监听同一个节点产生惊群效应 (Herd Effect)
 * 3. 仅注册 Watcher 监听序号恰好比自己小 1 的前序节点
 */
public class DistributedLockDemo {

    private static final String LOCK_ROOT = "/locks";
    private static final String LOCK_PREFIX = "/locks/lock-";

    private final ZooKeeper zk;
    private String currentLockPath;
    private String waitLockPath;
    private CountDownLatch waitLatch;

    public DistributedLockDemo(ZooKeeper zk) {
        this.zk = zk;
    }

    /**
     * 尝试获取分布式锁（阻塞直到成功或超时）
     */
    public boolean acquireLock(long timeout, TimeUnit unit) throws Exception {
        // 确保锁根节点存在
        ensureRoot();

        // 1. 创建临时顺序节点
        currentLockPath = zk.create(
                LOCK_PREFIX,
                "client_lock_data".getBytes(StandardCharsets.UTF_8),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL
        );
        System.out.println("[ZK-Lock] 创建临时顺序节点: " + currentLockPath);

        // 2. 检查是否获取到锁
        return checkLock(timeout, unit);
    }

    private void ensureRoot() throws KeeperException, InterruptedException {
        Stat stat = zk.exists(LOCK_ROOT, false);
        if (stat == null) {
            try {
                zk.create(LOCK_ROOT, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (KeeperException.NodeExistsException ignored) {
                // 并发创建忽略
            }
        }
    }

    private boolean checkLock(long timeout, TimeUnit unit) throws Exception {
        List<String> children = zk.getChildren(LOCK_ROOT, false);
        Collections.sort(children);

        String myNodeName = currentLockPath.substring(LOCK_ROOT.length() + 1);
        int myIndex = children.indexOf(myNodeName);

        if (myIndex == 0) {
            // 自己是序号最小的节点，成功获取到锁！
            System.out.println("[ZK-Lock] 当前节点为最小序号 [" + myNodeName + "]，成功获取分布式锁！");
            return true;
        }

        // 关键防惊群设计：仅监听序号恰好比自己小 1 的前序节点
        String prevNodeName = children.get(myIndex - 1);
        waitLockPath = LOCK_ROOT + "/" + prevNodeName;
        System.out.println("[ZK-Lock] 未获得锁，进入等待，仅监听前序节点: " + waitLockPath);

        waitLatch = new CountDownLatch(1);
        Stat stat = zk.exists(waitLockPath, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if (event.getType() == Event.EventType.NodeDeleted) {
                    System.out.println("[ZK-Lock] 前序节点释放 [" + event.getPath() + "]，唤醒当前线程");
                    if (waitLatch != null) {
                        waitLatch.countDown();
                    }
                }
            }
        });

        // 边界防护：若在注册 Watcher 前，前序节点已恰好释放
        if (stat == null) {
            return checkLock(timeout, unit);
        }

        boolean acquired = waitLatch.await(timeout, unit);
        if (acquired) {
            return checkLock(timeout, unit);
        } else {
            System.err.println("[ZK-Lock] 获取锁等待超时！主动放弃");
            releaseLock();
            return false;
        }
    }

    /**
     * 释放分布式锁（删除临时节点）
     */
    public void releaseLock() {
        if (currentLockPath != null) {
            try {
                zk.delete(currentLockPath, -1);
                System.out.println("[ZK-Lock] 成功释放分布式锁节点: " + currentLockPath);
            } catch (Exception e) {
                System.err.println("[ZK-Lock] 释放锁失败: " + e.getMessage());
            } finally {
                currentLockPath = null;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch connectedSignal = new CountDownLatch(1);
        ZooKeeper zkClient = new ZooKeeper("127.0.0.1:2181", 5000, event -> {
            if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                connectedSignal.countDown();
            }
        });

        connectedSignal.await();
        System.out.println("ZooKeeper 客户端连接成功！");

        DistributedLockDemo lock = new DistributedLockDemo(zkClient);
        try {
            if (lock.acquireLock(10, TimeUnit.SECONDS)) {
                System.out.println("--> 执行核心业务逻辑（交易结算、库存扣减等）...");
                Thread.sleep(1000);
            }
        } finally {
            lock.releaseLock();
            zkClient.close();
        }
    }
}

