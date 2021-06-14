package com.dwj.resource;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * @author daiwj
 * @date 2020/11/24
 * @description:
 */
public class SysProperties extends Observable {
    private static final Logger log = LoggerFactory.getLogger(SysProperties.class);
    /**
     * 缓存配置的map
     */
    private final Map<String, ConcurrentHashMap<String, String>> MAP_CACHE = new ConcurrentHashMap<>();
    /**
     * 扫描配置文件的目录
     */
    private static final String SCAN_PROP_DIR = "/config";
    private static final String DEFAULT_FILE_PREFIX = "config";
    /**
     * watchService监控线程池
     */
    private ExecutorService watchThreadPool = Executors.newSingleThreadExecutor();

    private static volatile SysProperties instance;

    private SysProperties(){
        File dir = new File(SysProperties.class.getResource(SCAN_PROP_DIR).getPath());
        if(dir.exists() && dir.isDirectory()){
            File[] propFiles = dir.listFiles(f -> f.getName().endsWith(".properties"));
            if(propFiles != null && propFiles.length > 0){
                for (File file : propFiles) {
                    String keyPrefix = file.getName().substring(0, file.getName().indexOf("."));
                    if(MAP_CACHE.get(keyPrefix) == null){
                        MAP_CACHE.put(keyPrefix, new ConcurrentHashMap<>(16));
                    }
                    loadProperties(file, MAP_CACHE.get(keyPrefix));
                }
            }
        }
        watchThreadPool.submit(() -> watchFile(dir));
    }
    public static SysProperties getInstance(){
        if(instance == null) {
            synchronized (SysProperties.class){
                if(instance == null){
                    instance = new SysProperties();
                }
            }
        }
        return instance;
    }

    /**
     * 每一次变更是否都触发通知
     * @param filePrefix
     * @return
     */
    private boolean eachChangeShouldNotify(String filePrefix){
        String pattern = get("flowdefine.property.each.change.notify");
        boolean result = false;
        if(StringUtils.isNotEmpty(pattern)){
            result = Pattern.matches(pattern, filePrefix);
        }
        if(!result){
            result = Pattern.matches("^flowdefine$", filePrefix);
        }
        return result;
    }
    private void watchFile(File file){
        WatchService watcher = null;
        try {
            watcher = FileSystems.getDefault().newWatchService();
            Path path = file.toPath();
            path.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);
            while(true){
                WatchKey key = watcher.take();
                if(!key.isValid()){
                    break;
                }
                key.pollEvents().stream().forEach(event -> {
                    WatchEvent.Kind<?> kind = event.kind();
                    if(kind == OVERFLOW){
                        log.info(kind.name());
                        return;
                    }
                    WatchEvent<Path> e = (WatchEvent<Path>) event;
                    Path fileName = e.context();

                    if(ENTRY_CREATE.toString().equals(kind.name()) || ENTRY_MODIFY.toString().equals(kind.name())){
                        log.info("{} is created or modified.", fileName);
                        if(fileName.toString().endsWith(".properties")){
                            String keyPrefix = fileName.toString().substring(0, fileName.toString().indexOf("."));
                            ConcurrentHashMap<String, String> map = MAP_CACHE.get(keyPrefix);
                            if(map == null){
                                map = new ConcurrentHashMap<>();
                                MAP_CACHE.put(keyPrefix, map);
                            }
                            reloadProperties(new File(file.getPath() + File.separator + fileName), map, eachChangeShouldNotify(keyPrefix));
                        }
                    }
                });
                if(!key.reset()){
                    break;
                }
            }
        }catch (Exception e){
            log.error(e.getMessage(), e.getCause());
        }finally {
            log.info("watch task was stop by exception, close this watcher and start a new watcher.");
            if(watcher != null){
                try {
                    watcher.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e.getCause());
                }
            }
            watchThreadPool.submit(() -> {
                watchFile(file);
            });
            log.info("the new watch task started successfully.");
        }
    }

    /**
     * 初始化加载配置文件
     * @param file
     * @param cacheMap
     */
    private void loadProperties(File file, Map<String, String> cacheMap){
        log.info("load {} begin>>>>", file.getAbsolutePath());
        String filePrefix = file.getName().substring(0, file.getName().indexOf("."));
        try (BufferedInputStream buffer = new BufferedInputStream(new FileInputStream(file))){
            Properties prop = new Properties();
            prop.load(buffer);
            Iterator<Map.Entry<Object, Object>> iterator = prop.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Object, Object> entry = iterator.next();
                if(entry.getKey().toString().startsWith(filePrefix + ".") || DEFAULT_FILE_PREFIX.equals(filePrefix)){
                    cacheMap.put(String.valueOf(entry.getKey()), entry.getValue() != null ? entry.getValue().toString() : "");
                    log.info("{} add property[{}={}]", file.getName(), entry.getKey(), entry.getValue());
                }else {
                    log.error("{} property[{}={}] cannot be cached,because the key prefix cannot match the file name", file.getName(), entry.getKey(), entry.getValue());
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 动态加载变更配置
     * @param file
     * @param cacheMap
     * @param eachEventNotify
     */
    private void reloadProperties(File file, Map<String,String> cacheMap, boolean eachEventNotify){
        log.info("reload {} begin>>>>>", file.getName());
        String filePrefix = file.getName().substring(0, file.getName().indexOf("."));
        Map<String, Map<String,String>> passedMap = new ConcurrentHashMap<>();
        Map<String, String> changedConfig = new ConcurrentHashMap<>();
        try (BufferedInputStream buffer = new BufferedInputStream(new FileInputStream(file))){
            Properties prop = new Properties();
            prop.load(buffer);
            Iterator<Map.Entry<Object, Object>> iterator = prop.entrySet().iterator();
            boolean isModified = false;
            while (iterator.hasNext()) {
                Map.Entry<Object, Object> entry = iterator.next();

                if(entry.getKey() != null){
                    String key = entry.getKey().toString();
                    String value = entry.getValue() != null ? entry.getValue().toString() : "";
                    if(cacheMap.containsKey(key)){
                        if((cacheMap.get(key) != null && !cacheMap.get(key).equals(value)) || !value.equals(cacheMap.get(key))){
                            log.info("{} [{}] value changed [{}]==>[{}]", file.getName(), key, cacheMap.get(key), value);
                            cacheMap.put(key, value);
                            isModified = true;
                            if(eachEventNotify){
                                setChanged();
                                notifyObservers(key + "=" + value);
                            }else {
                                changedConfig.put(key, value);
                            }
                        }
                    }else {
                        if(key.startsWith(filePrefix + ".") || DEFAULT_FILE_PREFIX.equals(filePrefix + ".")){
                            cacheMap.put(key, value);
                            log.info("{} add property [{}={}]", file.getName(), key, value);
                            if(eachEventNotify){
                                setChanged();
                                notifyObservers(key + "=" + value);
                            }else {
                                changedConfig.put(key, value);
                            }
                        }else {
                            log.error("{} property[{}={}] cannot be cached,because the key prefix cannot match the file name", file.getName(), entry.getKey(), entry.getValue());
                        }
                        isModified = true;
                    }
                }
            }
            for (String key : cacheMap.keySet()) {
                if(prop.get(key) == null){
                    cacheMap.remove(key);
                    isModified = true;
                    if(eachEventNotify){
                        setChanged();
                        notifyObservers(key + "=");
                    }else {
                        changedConfig.put(key, "");
                    }
                    log.info("{} property[{}] is deleted.", file.getName(), key);
                }
            }
            if(isModified){
                if (!eachEventNotify){
                    setChanged();
                    passedMap.put(filePrefix, changedConfig);
                    notifyObservers(passedMap);
                }
            }else {
                log.info("{} nothing is changed.", file.getName());
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        log.info("reload {} end<<<<<", file.getName());
    }

    public static String get(String key){
        String keyPrefix = key.substring(0, key.indexOf("."));
        ConcurrentHashMap<String, String> cacheMap = getInstance().MAP_CACHE.get(keyPrefix);
        if(cacheMap == null){
            cacheMap = getInstance().MAP_CACHE.get(DEFAULT_FILE_PREFIX);
        }
        return cacheMap.get(key);
    }
    public static Map<String, String> getConfigMap(String fileNamePrefix){
        return getInstance().MAP_CACHE.get(fileNamePrefix);
    }
}
