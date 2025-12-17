from kazoo.client import KazooClient
from kazoo.exceptions import ConnectionDropped, MarshallingError, ZookeeperError

def reproduce_1m_limit():
    zk = KazooClient(hosts='127.0.0.1:2181')
    zk.start()

    # Create data slightly larger than 1MB (1024*1024 bytes)
    # Default jute.maxbuffer is 1MB (1048576 bytes)
    # We add some padding to ensure it exceeds limits including overhead
    size_in_bytes = 1024 * 1024 + 100
    big_data = b'a' * size_in_bytes

    node_path = "/big_data_node"

    print(f"Attempting to write {len(big_data)} bytes to {node_path}...")

    try:
        if zk.exists(node_path):
            zk.delete(node_path)

        zk.create(node_path, big_data)
        print("Success! (Unexpected)")
    except Exception as e:
        print(f"Caught expected exception: {type(e).__name__}: {e}")
    finally:
        zk.stop()
        zk.close()

if __name__ == "__main__":
    reproduce_1m_limit()
