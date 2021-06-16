package com.dwj.zookeeper;

import com.dwj.resource.SysProperties;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author daiwj
 * @date 2020/12/24
 * @description:
 */
public class ZookeeperFactory {
    private static final Logger log = LoggerFactory.getLogger(ZookeeperFactory.class);

    private static volatile ZookeeperFactory instance;

    private CuratorFramework client;

    private ZookeeperFactory() {
        //获取zk  curator client
        client = startClient();
    }

    public static ZookeeperFactory getInstance() {
        if (instance == null) {
            synchronized (ZookeeperFactory.class) {
                if (instance == null) {
                    instance = new ZookeeperFactory();
                }
            }
        }
        return instance;
    }

    /**
     * 获取节点数据
     *
     * @param node
     * @return
     * @throws Exception
     */
    public String getNodeData(String node) throws Exception {
        checkClientStatus();
        //根路径
        try {
            byte[] bytes = client.getData().forPath(node);
            return new String(bytes);
        } catch (Exception e) {
            log.error("节点【{}】数据获取异常！", node, e);
            throw e;
        }
    }

    /**
     * 删除节点数据 会删除子节点
     *
     * @param node
     * @throws Exception
     */
    public void delNodeData(String node) throws Exception {
        checkClientStatus();
        //根路径
        Stat stat = client.checkExists().forPath(node);
        if(stat != null){
            client.delete().deletingChildrenIfNeeded().forPath(node);
            log.info("节点【{}】数据删除成功！", node);
        }
    }

    /**
     * 新增或修改节点数据
     * @param node
     * @param data
     * @throws Exception
     */
    public void setData(String node, byte[] data) throws Exception {
        checkClientStatus();
        //检查节点是否存在
        Stat stat = client.checkExists().forPath(node);
        if (stat == null) {
            log.info("节点【{}】不存在，创建节点并写入...", node);
            //创建节点并写入
            String path = client.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(node, data);
            log.info("[{}]节点创建并写入完成", path);
            return;
        }
        client.setData().forPath(node, data);
    }
    /**
     * 新增或修改节点数据
     * @param node
     * @param data
     * @param createMode 节点模式 持久化/临时/顺序等
     * @throws Exception
     */
    public void setData(String node, byte[] data, CreateMode createMode) throws Exception {
        checkClientStatus();
        //检查节点是否存在
        Stat stat = client.checkExists().forPath(node);
        if (stat == null) {
            log.info("节点【{}】不存在，创建节点并写入...", node);
            //创建节点并写入
            String path = client.create()
                    .creatingParentsIfNeeded()
                    .withMode(createMode)
                    .forPath(node, data);
            log.info("[{}]节点创建并写入完成", path);
            return;
        }
        client.setData().forPath(node, data);
    }
    /**
     * 建立客户端连接
     *
     * @return
     */
    private CuratorFramework startClient() {
        //服务器列表，格式host1:port1,host2:port2,…
        String connectString = SysProperties.get("zookeeper.server.addr");

        //隔离命名空间
        String namespace = SysProperties.get("zookeeper.chroot.namespace");

        int connectTimeout = 5000;
        if (StringUtils.isNotEmpty(SysProperties.get("zookeeper.client.connect.timeout"))) {
            connectTimeout = Integer.parseInt(SysProperties.get("zookeeper.client.connect.timeout"));
        }
        int sessionTimeout = 30000;
        if (StringUtils.isNotEmpty(SysProperties.get("zookeeper.client.session.timeout"))) {
            sessionTimeout = Integer.parseInt(SysProperties.get("zookeeper.client.session.timeout"));
        }
        log.info("正在建立zookeeper client 连接... 配置信息：connectString：{}， namespace：{}，connectTimeout：{}， sessionTimeout：{}",
                connectString, namespace, connectTimeout, sessionTimeout);
        //重试策略
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs(sessionTimeout)
                .connectionTimeoutMs(connectTimeout)
                .retryPolicy(retryPolicy)
                .namespace(namespace)
                .build();
        client.start();
        return client;
    }

    public void checkClientStatus(){
        if (client == null || CuratorFrameworkState.STOPPED.equals(client.getState())) {
            synchronized (this){
                if (client == null || CuratorFrameworkState.STOPPED.equals(client.getState())) {
                    log.info("重新建立zookeeper连接...");
                    client = startClient();
                }
            }
        }
    }

    public void tryLock(String node){
        InterProcessLock lock = new InterProcessMutex(client, SysProperties.get("zookeeper.root.path") + "/curatorLock/" + node);
//        lock.acquire()
    }


    /**
     * @return CuratorFramework client
     */
    public static CuratorFramework getCuratorClient() {
        return ZookeeperFactory.getInstance().client;
    }
    /**
     * 关闭CuratorFramework client
     */
    public void closeClient() {
        if (client != null) {
            client.close();
            log.info("zookeeper client 已关闭");
        }
    }
}
