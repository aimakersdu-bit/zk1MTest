# ZooKeeper 1MB Limit Reproduction Demo

This project provides a Java example to demonstrate the default 1MB data transfer limit in ZooKeeper. When a client attempts to write data larger than the `jute.maxbuffer` setting (default 1MB), the server closes the connection, resulting in a `ConnectionLossException`.

## Prerequisites

*   Java Development Kit (JDK) 8 or later
*   Apache Maven (optional, for easy building)
*   A running ZooKeeper server instance

## Setup

1.  **Start ZooKeeper:**
    Ensure you have a ZooKeeper server running locally on port 2181.

    If you need to set one up manually:
    ```bash
    wget https://archive.apache.org/dist/zookeeper/zookeeper-3.8.4/apache-zookeeper-3.8.4-bin.tar.gz
    tar -zxf apache-zookeeper-3.8.4-bin.tar.gz
    echo "tickTime=2000" > apache-zookeeper-3.8.4-bin/conf/zoo.cfg
    echo "dataDir=./zookeeper_data" >> apache-zookeeper-3.8.4-bin/conf/zoo.cfg
    echo "clientPort=2181" >> apache-zookeeper-3.8.4-bin/conf/zoo.cfg
    mkdir -p zookeeper_data
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

### Using javac (Manual)

1.  Compile the code (ensure ZooKeeper libraries are in your classpath):
    ```bash
    # Example assuming you have the zookeeper-bin directory from the setup step
    javac -cp "apache-zookeeper-3.8.4-bin/lib/*" ZkRepro.java
    ```

2.  Run the code:
    ```bash
    java -cp ".:apache-zookeeper-3.8.4-bin/lib/*" ZkRepro
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
