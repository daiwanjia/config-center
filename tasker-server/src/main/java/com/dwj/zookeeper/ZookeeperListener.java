package com.dwj.zookeeper;

import com.dwj.resource.SysProperties;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.*;
import org.apache.curator.retry.RetryUntilElapsed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;

/**
 * @author daiwj
 * @date 2020/10/27
 * @description:
 */
public class ZookeeperListener extends Observable {
    private static final Logger log = LoggerFactory.getLogger(ZookeeperListener.class);

    private CuratorFramework client;

    private static volatile ZookeeperListener instance;

    public static ZookeeperListener getInstance() {
        if (instance == null) {
            synchronized (ZookeeperListener.class) {
                if (instance == null) {
                    instance = new ZookeeperListener();
                }
            }
        }
        return instance;
    }

    private ZookeeperListener() {
        initClient();
    }

    private void initClient() {
        String zkServer = SysProperties.get("zookeeper.server.addr");
        String namespace = SysProperties.get("zookeeper.chroot.namespace");
        String connectTimeout = SysProperties.get("zookeeper.client.connect.timeout");
        String sessionTimeout = SysProperties.get("zookeeper.client.session.timeout");
        log.info("init zookeeper client params: zk_server:{}, namespace:{}, connectTimeout:{}ms, sessionTimeout:{}ms", zkServer, namespace, connectTimeout, sessionTimeout);
        RetryPolicy retryPolicy = new RetryUntilElapsed(1000, 3);
        client = CuratorFrameworkFactory.builder()
                .connectString(zkServer)
                .connectionTimeoutMs(Integer.parseInt(connectTimeout))
                .sessionTimeoutMs(Integer.parseInt(sessionTimeout))
                .retryPolicy(retryPolicy)
                .namespace(namespace)
                .build();
        client.start();
    }
    /**
     * ??????????????????(??????????????????????????????)
     */
    public void startNodeListener() {
        String rootPath = SysProperties.get("zookeeper.root.path");
        String listenNodePath = SysProperties.get("zookeeper.listener.node.path");
        startNodeListener(rootPath + listenNodePath);
    }
    /**
     * ??????????????????(??????????????????????????????)
     */
    public void startNodeListener(String path) {
        if(path.endsWith("/")){
            path = path.substring(0, path.length() -1);
        }
        try {
            NodeCache nodeCache = new NodeCache(client, path);
            NodeCacheListener cacheListener = () -> {
                ChildData currentData = nodeCache.getCurrentData();
                if (currentData != null) {
                    String data = new String(currentData.getData());
                    log.info("??????[{}]???????????????[{}]", currentData.getPath(), data);
//                    setChanged();
//                    notifyObservers(data);
                } else {
                    log.info("????????????????????????");
                }
            };
            nodeCache.getListenable().addListener(cacheListener);
            nodeCache.start();
            log.info("NodeListener?????????[{}]]????????????????????????", path);
        } catch (Exception e) {
            log.error("start node[{}] NodeListener error.", path, e);
        }

    }
    /**
     * ??????????????????????????????????????????????????????
     */
    public void startTreeNodeListener() {
        String rootPath = SysProperties.get("zookeeper.root.path");
        String listenNodePath = SysProperties.get("zookeeper.listener.node.path");
        startTreeNodeListener(rootPath + listenNodePath);
    }
    /**
     * ??????????????????????????????????????????????????????
     */
    public void startTreeNodeListener(String path) {
        if(path.endsWith("/")){
            path = path.substring(0, path.length() -1);
        }
        try {
            TreeCache treeCache = new TreeCache(client, path);
            String finalPath = path;
            treeCache.getListenable().addListener(new TreeCacheListener() {
                @Override
                public void childEvent(CuratorFramework client, TreeCacheEvent event) {
                    ChildData eventData = event.getData();
                    if (eventData == null) return;
                    String eventPath = eventData.getPath();
                    if (finalPath.equals(eventPath)) {
                        log.info("????????????[{}]??????????????????????????????????????????", eventPath);
                        return;
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("path", eventPath);
                    data.put("data", eventData.getData());
                    data.put("version", eventData.getStat().getVersion());
                    data.put("type", event.getType());
                    switch (event.getType()) {
                        case NODE_ADDED:
                        case NODE_UPDATED:
                        case NODE_REMOVED:
                            log.info("[{}]????????????????????????????????????[{}]?????????????????????{}", eventPath, event.getType(), eventData.getStat().getVersion());
//                            setChanged();
//                            notifyObservers(data);
                            break;
                        default:
                            log.info("????????????????????????{}", event.getType());
                            break;
                    }
                }
            });
            treeCache.start();
            log.info("TreeNodeListener?????????[{}]]????????????????????????????????????", path);
        } catch (Exception e) {
            log.error("start node[{}] TreeNodeListener error.", path, e);
        }
    }

    /**
     * ????????????????????????????????????????????????
     */
    public void startPathChildrenListener() {
        String rootPath = SysProperties.get("zookeeper.root.path");
//        String listenNodePath = SysProperties.get("zookeeper.listener.node.path");
        String listenNodePath = SysProperties.get("server.name");
        startPathChildrenListener(rootPath + "/" + listenNodePath);
    }

    /**
     * ????????????????????????????????????????????????
     * @param path zookeeper node
     */
    public void startPathChildrenListener(String path){
        if(path.endsWith("/")){
            path = path.substring(0, path.length() -1);
        }
        try {
            PathChildrenCache cache = new PathChildrenCache(client, path, true);
            PathChildrenCacheListener cacheListener = new PathChildrenCacheListener() {
                @Override
                public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
                    ChildData eventData = event.getData();
                    if (eventData == null) {
                        log.info("????????????????????? eventType???{}", event.getType());
                        return;
                    }
                    String eventPath = eventData.getPath();
                    Map<String, Object> data = new HashMap<>();
                    data.put("path", eventPath);
                    data.put("data", eventData.getData());
                    data.put("version", eventData.getStat().getVersion());
                    data.put("type", event.getType());
                    switch (event.getType()) {
                        case CHILD_ADDED:
                        case CHILD_UPDATED:
                        case CHILD_REMOVED:
                            log.info("[{}]????????????????????????????????????[{}]?????????????????????{}", eventPath, event.getType(), eventData.getStat().getVersion());
                            setChanged();
                            notifyObservers(data);
                            break;
                        default:
                            log.info("????????????????????????{}", event.getType());
                            break;
                    }
                }
            };
            cache.getListenable().addListener(cacheListener);
            cache.start();
            log.info("PathChildrenListener?????????[{}]?????????????????????????????????", path);
        } catch (Exception e) {
            log.error("start node[{}] PathChildrenListener error.", path, e);
        }
    }
    /**
     * @return CuratorFramework client
     */
    public static CuratorFramework getCuratorClient() {
        return ZookeeperListener.getInstance().client;
    }

    /**
     * ??????CuratorFramework client
     */
    public void closeClient() {
        if (client != null) {
            client.close();
            log.info("zookeeper client ?????????");
        }
    }
}