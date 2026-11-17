package com.openwiki.demo.election;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 场景 2：ZooKeeper 集群 Master-Worker 服务选主机制实战
 * <p>
 * 核心机制：
 * 1. 多个微服务实例启动时争当 Leader；
 * 2. 在 /election 根目录下创建临时顺序节点；
 * 3. 序号最小者当选为 Leader，其余成为 Follower；
 * 4. Follower 仅监听排在自己前一号的节点，一旦 Leader 崩溃宕机，临时节点自动删除并平滑触发下一代选举。
 */
public class LeaderElectionDemo {

    private static final String ELECTION_ROOT = "/election";
    private final ZooKeeper zk;
    private final String serverId;
    private String currentZnodePath;
    private volatile boolean isLeader = false;

    public LeaderElectionDemo(ZooKeeper zk, String serverId) {
        this.zk = zk;
        this.serverId = serverId;
    }

    public void startElection() throws Exception {
        ensureRoot();

        // 创建临时顺序节点
        currentZnodePath = zk.create(
                ELECTION_ROOT + "/node-",
                serverId.getBytes(StandardCharsets.UTF_8),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL
        );
        System.out.println("[Election] 实例 [" + serverId + "] 创建选主节点: " + currentZnodePath);

        reElect();
    }

    private void ensureRoot() throws KeeperException, InterruptedException {
        Stat stat = zk.exists(ELECTION_ROOT, false);
        if (stat == null) {
            try {
                zk.create(ELECTION_ROOT, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (KeeperException.NodeExistsException ignored) {
            }
        }
    }

    private void reElect() throws Exception {
        List<String> children = zk.getChildren(ELECTION_ROOT, false);
        Collections.sort(children);

        String myZnodeName = currentZnodePath.substring(ELECTION_ROOT.length() + 1);
        int myIndex = children.indexOf(myZnodeName);

        if (myIndex == 0) {
            isLeader = true;
            System.out.println("👑 [Election] 实例 [" + serverId + "] 成为集群 LEADER！承担主控调度任务。");
        } else {
            isLeader = false;
            String prevZnode = children.get(myIndex - 1);
            String watchPath = ELECTION_ROOT + "/" + prevZnode;
            System.out.println("🛡️ [Election] 实例 [" + serverId + "] 为 FOLLOWER，监听前序节点: " + watchPath);

            zk.exists(watchPath, event -> {
                if (event.getType() == Watcher.Event.EventType.NodeDeleted) {
                    System.out.println("[Election] 前序节点掉线，触发下一轮故障转移选举...");
                    try {
                        reElect();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    public boolean isLeader() {
        return isLeader;
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        ZooKeeper zkClient = new ZooKeeper("127.0.0.1:2181", 3000, event -> {
            if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                connected.countDown();
            }
        });
        connected.await();

        LeaderElectionDemo election = new LeaderElectionDemo(zkClient, "node-inst-01");
        election.startElection();

        Thread.sleep(3000);
        zkClient.close();
    }
}

