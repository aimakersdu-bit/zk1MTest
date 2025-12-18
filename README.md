# ZooKeeper 1MB Limit Reproduction and Fix

This project demonstrates the default 1MB data transfer limit in ZooKeeper and provides a fix by increasing the `jute.maxbuffer` property.

## The Issue

By default, ZooKeeper limits data packets to 1MB. Attempting to write larger data results in a `ConnectionLossException` as the server closes the connection.

## The Fix

To support larger data packets, you must set the `jute.maxbuffer` system property (in bytes) on **both** the server and the client.

In this example, we set it to `2097152` (2MB).

### Server Side Fix
Create a file `conf/java.env` in your ZooKeeper installation directory with the following content:
```bash
export JVMFLAGS="-Djute.maxbuffer=2097152"
```
(This has already been applied in the provided `env/zookeeper_env.tar.gz`).

### Client Side Fix
Pass the property to the JVM running the client:
```bash
-Djute.maxbuffer=2097152
```
(This has been configured in `pom.xml` for the Maven execution).

## Setup

1.  **Prepare the Environment:**
    Unpack the pre-configured ZooKeeper environment:

    ```bash
    tar -zxf env/zookeeper_env.tar.gz
    mkdir -p zookeeper_data
    ```

2.  **Start ZooKeeper:**
    ```bash
    ./apache-zookeeper-3.8.4-bin/bin/zkServer.sh start
    ```

## Running the Demo

1.  Build the project:
    ```bash
    mvn clean package
    ```

2.  Run the application:
    ```bash
    mvn exec:java -Dexec.mainClass="ZkRepro"
    ```

## Expected Output

With the fix applied, the write operation should succeed:

```
Connecting to Zookeeper...
Connected.
Attempting to write 1048676 bytes to /big_data_node...
Success! (Expected with fix)
```
