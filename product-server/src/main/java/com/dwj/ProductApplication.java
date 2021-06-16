package com.dwj;

import com.alibaba.fastjson.JSONObject;
import com.dwj.config.ConfigRefresh;
import com.dwj.resource.SysProperties;
import com.dwj.util.OSInfo;
import com.dwj.zookeeper.ZookeeperFactory;
import com.dwj.zookeeper.ZookeeperListener;
import com.dwj.zookeeper.ZookeeperUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.CreateMode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import sun.misc.Signal;

import java.util.concurrent.TimeUnit;

/**
 * @author daiwj
 * @date 2021/05/30
 * @description:
 */
@SpringBootApplication
@Slf4j
public class ProductApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
        //服务注册
        serverRegister();
        //启动监听
        ZookeeperListener.getInstance().startPathChildrenListener();
        addZookeeperFactoryObservers();
        exit();
    }

    /**
     * 添加zookeeperFactory观察者
     */
    private static void addZookeeperFactoryObservers() {
        //延迟启动观察者，curator对zookeeper的监听实际都是本地缓存视图和zookeeper服务的数据节点进行比较，所以会监听到所有本地和远端不一致的数据内容，
        //即初始启动时会监听到远端zookeeper已存在的数据节点，而这不是我们要的主动触发配置变更，所有需要进行延迟启动
        long delayTime = 5000L;
        String time = SysProperties.get("zookeeper.listener.observer.delay.start.time");
        if (StringUtils.isNotEmpty(time)) {
            delayTime = Long.parseLong(time);
        }
        try {
            TimeUnit.MILLISECONDS.sleep(delayTime);
        } catch (InterruptedException e) {
        }
        ZookeeperListener.getInstance().addObserver(ConfigRefresh.getInstance());
        log.info("添加观察者[{}]成功!", ConfigRefresh.getInstance());
    }


    /**
     * 在Linux下支持的信号（具体信号kill -l命令查看）：
     * SEGV, ILL, FPE, BUS, SYS, CPU, FSZ, ABRT, INT, TERM, HUP, USR1, USR2, QUIT, BREAK, TRAP, PIPE
     * 在Windows下支持的信号：
     * SEGV, ILL, FPE, ABRT, INT, TERM, BREAK
     */
    public static void exit() {
        String signal = SysProperties.get("os." + OSInfo.getOSname().toString().toLowerCase() + ".process.exit.signal");
        Signal.handle(new Signal(signal), signal1 -> {
            System.out.println("SignalHandler: " + signal1);
            System.exit(0);
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(Long.parseLong(SysProperties.get("zookeeper.client.delay.close.time")));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            ZookeeperListener.getInstance().closeClient();
            System.out.println("zookeeper client closed.");
        }));
    }

    /**
     * 服务注册
     * @throws Exception
     */
    private static void serverRegister(){
        String serverName = SysProperties.get("server.name");
        String serverIp = SysProperties.get("server.ip");
        String path = ZookeeperUtil.getServersPath() + "/" + serverName;
        JSONObject data = new JSONObject();
        data.put("server.name", serverName);
        data.put("server.ip", serverIp);
        //创建临时节点 客户端断开连接或宕掉会自动删除节点，达到服务下线目的
        try {
            ZookeeperFactory.getInstance().setData(path, data.toString().getBytes(), CreateMode.EPHEMERAL);
        } catch (Exception e) {
            log.error("写数据到zk异常", e);
            throw new RuntimeException("服务注册失败！");
        }
        log.info("{}[{}]服务注册成功！", serverName, serverIp);
    }
}
