# ZooKeeper 1MB Limit Reproduction Demo

This project provides a Java example to demonstrate the default 1MB data transfer limit in ZooKeeper. When a client attempts to write data larger than the `jute.maxbuffer` setting (default 1MB), the server closes the connection, resulting in a `ConnectionLossException`.

## Prerequisites

*   Java Development Kit (JDK) 8 or later
*   Apache Maven

## Setup

1.  **Prepare the Environment:**
    A pre-configured ZooKeeper environment is provided in `env/zookeeper_env.tar.gz`.

    ```bash
    # Unpack the environment
    tar -zxf env/zookeeper_env.tar.gz

    # Create the data directory (if not exists)
    mkdir -p zookeeper_data
    ```

2.  **Start ZooKeeper:**
    ```bash
    ./apache-zookeeper-3.8.4-bin/bin/zkServer.sh start
    ```

## Running the Demo

### Using Maven (Recommended)

1.  Build the project:
    ```bash
    mvn clean package
    ```

2.  Run the application:
    ```bash
    mvn exec:java -Dexec.mainClass="ZkRepro"
    ```

## Expected Output

You should see output indicating that the connection was established, followed by an attempt to write >1MB of data, and finally a `ConnectionLossException`:

```
Connecting to Zookeeper...
Connected.
Attempting to write 1048676 bytes to /big_data_node...
...
Caught expected exception: org.apache.zookeeper.KeeperException$ConnectionLossException: KeeperErrorCode = ConnectionLoss for /big_data_node
```
