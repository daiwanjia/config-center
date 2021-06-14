package com.dwj.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dwj.resource.SysProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

/**
 * @author daiwj
 * @date 2021/05/30
 * @description: 更新配置 配置内容为
 */
@Component
@Slf4j
public class ConfigRefresh implements Observer {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmssSSS");
    private static volatile ConfigRefresh instance;
    private ConfigRefresh(){}

    public static ConfigRefresh getInstance(){
        if (instance == null){
            synchronized (ConfigRefresh.class){
                if (instance == null){
                    instance = new ConfigRefresh();
                }
            }
        }
        return instance;
    }
    @Override
    public void update(Observable o, Object arg) {
        try {
            if (!(arg instanceof Map)) {
                return;
            }
            Map<String, Object> map = (Map<String, Object>) arg;
            log.info("接受到的通知事件信息为：{}", map);
            String path = (String) map.get("path");

            Object eventData = map.get("data");
            if(eventData instanceof byte[]){
                eventData = new String((byte[])eventData);
            }
            //事件类型 TreeCacheEvent/PathChildrenCacheEvent
            Object type = map.get("type");

            JSONObject data = JSON.parseObject(eventData.toString());
            String fileName = data.getString("fileName");
            String content = data.getString("content");
            String fileDir = SysProperties.get("zookeeper.config.path");
            String rootPath = SysProperties.get("zookeeper.root.path") + SysProperties.get("zookeeper.listener.node.path");
            Path filePath = Paths.get(fileDir, path.substring(rootPath.length()));
            log.info("目标配置路径为：{}", filePath);
            //删除事件
            if(type.equals(TreeCacheEvent.Type.NODE_REMOVED) || type.equals(PathChildrenCacheEvent.Type.CHILD_REMOVED)){
                log.info("目标配置[{}]进行删除操作", filePath);
                backupFile(filePath);
                Files.deleteIfExists(filePath);
                log.info("配置[{}]删除成功！！！", fileName);
                return;
            }
            //配置新增
            if(Files.notExists(filePath)){
                Files.write(filePath, content.getBytes());
                log.info("目标配置[{}]新增！", filePath);
                return;
            }
            //先检查文件是否相同，无变更则不进行操作；变更进行备份并更新
            if(checkMd5(Files.readAllBytes(filePath), content)){
                log.info("目标配置[{}]未发生变更，无需进行同步。", filePath);
                return;
            }
            backupFile(filePath);
            Files.write(filePath, content.getBytes());
            log.info("配置[{}]内容更新成功", fileName);

        } catch (IOException e) {
            log.error("配置同步更新失败", e);
        }
    }

    /**
     * 文件备份
     * @param filePath
     * @throws IOException
     */
    private static void backupFile(Path filePath) throws IOException {
        if(Files.exists(filePath)){
            log.info("文件[{}]存在 进行备份", filePath);
            Path bakFilePath = Paths.get(filePath + ".bak" + DATE_TIME_FORMATTER.format(LocalDateTime.now()));
            Files.copy(filePath, bakFilePath);
        }
    }
    private static boolean checkMd5(byte[] localFile, String content){
        return DigestUtils.md5Hex(localFile).equals(DigestUtils.md5Hex(content));
    }
}
