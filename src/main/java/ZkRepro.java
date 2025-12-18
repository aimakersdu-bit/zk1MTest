import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class ZkRepro {

    public static void main(String[] args) throws Exception {
        CountDownLatch connectedSignal = new CountDownLatch(1);
        ZooKeeper zk = new ZooKeeper("127.0.0.1:2181", 3000, new Watcher() {
            public void process(WatchedEvent event) {
                if (event.getState() == Event.KeeperState.SyncConnected) {
                    connectedSignal.countDown();
                }
            }
        });

        System.out.println("Connecting to Zookeeper...");
        connectedSignal.await();
        System.out.println("Connected.");

        // Create data slightly larger than 1MB (1024*1024 bytes)
        // Default jute.maxbuffer is 1MB (1048576 bytes)
        int sizeInBytes = 1024 * 1024 + 100;
        byte[] bigData = new byte[sizeInBytes];
        for (int i = 0; i < sizeInBytes; i++) {
            bigData[i] = 'a';
        }

        String nodePath = "/big_data_node";
        System.out.println("Attempting to write " + bigData.length + " bytes to " + nodePath + "...");

        try {
            if (zk.exists(nodePath, false) != null) {
                zk.delete(nodePath, -1);
            }

            zk.create(nodePath, bigData, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            System.out.println("Success! (Expected with fix)");
        } catch (Exception e) {
            System.out.println("Caught unexpected exception: " + e);
            e.printStackTrace();
        } finally {
            zk.close();
        }
    }
}
