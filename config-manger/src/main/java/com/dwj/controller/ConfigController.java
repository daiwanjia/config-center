package com.dwj.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dwj.entity.Server;
import com.dwj.resource.SysProperties;
import com.dwj.zookeeper.ZookeeperFactory;
import com.dwj.zookeeper.ZookeeperUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author daiwj
 * @date 2021/05/30
 * @description:
 */
@Slf4j
@Controller
@RequestMapping("/config")
public class ConfigController {
    /**
     * 三种配置发布操作
     */
    private static final String DEL = "del";
    private static final String ADD = "add";
    private static final String UPDATE = "update";

    @RequestMapping("/{type}")
    public String getConfig(@PathVariable String type, @RequestParam String name) throws Exception {
        String result;
        String path = SysProperties.get("zookeeper.config.center.path");
        Path filePath = Paths.get(path, name);
        if(!DEL.equals(type) && Files.notExists(filePath)){
            result = "配置[" + filePath + "]不存在且不是删除事件，发布失败，请检查！";
            log.info(result);
            return result;
        }
        //进行配置更新
        //需要发布的节点node
        String parentPath = SysProperties.get("zookeeper.root.path") + SysProperties.get("zookeeper.node.path");
        String configPath = (parentPath.endsWith("/") ? parentPath : parentPath + "/") + name;
        if (DEL.equals(type)) {
            ZookeeperFactory.getInstance().delNodeData(configPath);
            result = "配置[" + filePath + "]删除，发布成功！";
            log.info(result);
            return result;
        }
        String fileName = filePath.getFileName().toString();
        JSONObject json = new JSONObject();
        json.put("content", new String(Files.readAllBytes(filePath)));
        json.put("fileName", fileName);
        byte[] data = json.toString().getBytes();
        ZookeeperFactory.getInstance().setData(configPath, data);
        result = "配置[" + filePath + "]发布成功！";
        log.info(result);
        return result;
    }

    /**
     * 获取节点列表选项
     *
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/getNodes", method = RequestMethod.GET)
    public List<Server> getServers() throws Exception {
        String serverPath = ZookeeperUtil.getServersPath();
        CuratorFramework client = ZookeeperFactory.getCuratorClient();
        List<String> list = client.getChildren().forPath(serverPath);
        List<Server> serverList = new ArrayList<>();
        for (String node : list) {
            String nodeData = ZookeeperFactory.getInstance().getNodeData(serverPath + "/" + node);
            Server server = JSON.parseObject(nodeData, Server.class);
            serverList.add(server);
        }
        log.info("获取到的可发布节点列表信息：{}", serverList);
        return serverList;
    }

    /**
     * 获取指定节点数据
     * @param request
     * @return
     */
    @RequestMapping("/getContent")
    public String getContent(@RequestBody Map<String, Object> request){
        log.info("请求数据信息为：{}", request);
        JSONObject req = new JSONObject(request);
        String node = req.getString("node");
        String path = req.getString("path");
        if(path.startsWith("/")){
            path = path.substring(1);
        }
        if(path.endsWith("/")){
            path = path.substring(0, path.length() - 1);
        }
        ZookeeperFactory zookeeper = ZookeeperFactory.getInstance();
        String nodePath = ZookeeperUtil.getRootPath() + "/" + node + path;
        log.info("获取的节点路径为：{}", nodePath);
        String data;
        try {
            data = zookeeper.getNodeData(nodePath);
        } catch (Exception e) {
            data = "获取节点[" + nodePath + "]数据异常，" + e.getMessage();
        }
        return data;
    }
}
