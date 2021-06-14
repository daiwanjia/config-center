package com.dwj.resource;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.WatchEvent.Kind;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static java.nio.file.StandardWatchEventKinds.*;

@Slf4j
public class SysProperties extends Observable {

    private Map<String, ConcurrentHashMap<String, String>> FLOW_DEFINE_CACHE = new ConcurrentHashMap<String, ConcurrentHashMap<String, String>>();
    public static String PROPERTIES_FILE_DIR;
    private ExecutorService watchThreadPool = Executors.newSingleThreadExecutor();
    private static SysProperties instance;

    public static SysProperties getInstance() {
        if (instance == null) {
            synchronized (SysProperties.class) {
                if (instance == null) {
                    instance = new SysProperties();
                }
            }
        }
        return instance;
    }

    private SysProperties() {
        if (PROPERTIES_FILE_DIR == null) {
            PROPERTIES_FILE_DIR = SysProperties.class.getResource("/config").getPath();
        }
        File file = new File(PROPERTIES_FILE_DIR);
        if (file != null && file.exists() && file.isDirectory()) {
            File[] propFiles = file.listFiles(f -> {
                return f.getName().endsWith(".properties");
            });
            Arrays.stream(propFiles).forEach(f -> {
                String keyPrefix = f.getName().substring(0, f.getName().indexOf("."));
                // added 20191118
                if (FLOW_DEFINE_CACHE.get(keyPrefix) == null) {
                    FLOW_DEFINE_CACHE.put(keyPrefix, new ConcurrentHashMap<String, String>());
                }
                loadProperties(f, FLOW_DEFINE_CACHE.get(keyPrefix));
            });
            watchThreadPool.submit(() -> {
                watchFile(file);
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void watchFile(File dir) {
        WatchService watcher = null;
        try {
            watcher = FileSystems.getDefault().newWatchService();
            Path path = dir.toPath();
            path.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);
            while (true) {
                WatchKey key = watcher.take();
                if (!key.isValid()) {
                    break;
                }
                key.pollEvents().stream().forEach(event -> {

                    Kind<?> kind = event.kind();
                    // 事件可能lost or discarded
                    if (kind == OVERFLOW) {
                        log.info(kind.name());
                        return;
                    }

                    WatchEvent<Path> e = (WatchEvent<Path>) event;
                    Path fileName = (Path) e.context();

                    if (kind.name() == "ENTRY_CREATE") {
                        log.info(fileName + " is created.");
                        if (fileName.toString().endsWith(".properties")) {
                            // loadProperties(fileName.toAbsolutePath().toFile()); 在IDE中不可用，可以丢进我们的开发环境试试
                            String keyPrefix = fileName.toString().substring(0, fileName.toString().indexOf("."));
                            ConcurrentHashMap<String, String> map = FLOW_DEFINE_CACHE.get(keyPrefix);
                            if (map == null) {
                                map = new ConcurrentHashMap<String, String>();
                                FLOW_DEFINE_CACHE.put(keyPrefix, map);
                            }
                            reloadProperties(new File(dir.getPath() + File.separator + fileName), map);
                        }
                    } else if (kind.name() == "ENTRY_MODIFY") {
                        log.info(fileName + " is modified.");
                        if (fileName.toString().endsWith(".properties")) {
                            // loadProperties(fileName.toAbsolutePath().toFile()); 在IDE中不可用，可以丢进我们的开发环境试试
                            String keyPrefix = fileName.toString().substring(0, fileName.toString().indexOf("."));
                            ConcurrentHashMap<String, String> map = FLOW_DEFINE_CACHE.get(keyPrefix);
                            if (map == null) {
                                map = new ConcurrentHashMap<String, String>();
                                FLOW_DEFINE_CACHE.put(keyPrefix, map);
                            }
                            reloadProperties(new File(dir.getPath() + File.separator + fileName), map);
                        }
                    }

                });
                if (!key.reset()) {
                    break;
                }
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                log.error("watch thread " + Thread.currentThread().getId() + "-" + Thread.currentThread().getName() + " is interrupted. watch stop...");
            }
            if (e instanceof IOException) {
                log.error("watch thread " + Thread.currentThread().getId() + "-" + Thread.currentThread().getName() + " is stoped by " + e);
            }
            log.error(e.getMessage(), e.getCause());
        } finally {
            log.info("watch task was stopped by exception, now close the watcher and start a new watch task...");
            if (watcher != null) {
                try {
                    watcher.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e.getCause());
                }
            }
            watchThreadPool.submit(() -> {
                watchFile(dir);
            });
            log.info("the new watch task started successfully...");
        }
    }

    /**
     * 重新加载变更的配置文件
     *
     * @param file
     */
    private void reloadProperties(File file, Map<String, String> cacheMap) {
        log.info(">>>>reload " + file.getAbsolutePath() + " begin>>>>");
        BufferedInputStream inputStream = null;
        String filePrefix = file.getName().substring(0, file.getName().indexOf("."));
        Map<String, Map<String, String>> passedMap = new ConcurrentHashMap<String, Map<String, String>>();
        Map<String, String> changedConfig = new ConcurrentHashMap<String, String>();
        try {
            inputStream = new BufferedInputStream(new FileInputStream(file));
            Properties prop = new Properties();
            prop.load(inputStream);
            Iterator<Entry<Object, Object>> ite = prop.entrySet().iterator();
            boolean isModified = false;
            while (ite.hasNext()) {
                Entry<Object, Object> entry = ite.next();
                String key = String.valueOf(entry.getKey());
                String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : null;
                if (key != null) {
                    if (cacheMap.containsKey(key)) {
                        if ((cacheMap.get(key) != null && !cacheMap.get(key).equals(value)) || (value != null && !value.equals(cacheMap.get(key)))) {
                            log.info(file.getAbsolutePath() + " [" + key + "] value changed from [" + cacheMap.get(key) + "] => [" + value + "]");
                            cacheMap.put(key, value);
                            isModified = true;
                            changedConfig.put(key, value);
                        }
                    } else {
                        if (key.startsWith(filePrefix + ".")) {
                            cacheMap.put(key, value);
                            log.info(file.getAbsolutePath() + " add property [" + key + "=" + value + "]");
                            changedConfig.put(key, value);
                        } else {
                            log.error(file.getAbsoluteFile() + " property [" + entry.getKey() + "=" + entry.getValue() + "] cannot be cached , because the key prefix cantnot match the file name");
                        }
                        isModified = true;
                    }
                }
            }
            Iterator<String> keyStringIte = cacheMap.keySet().iterator();
            while (keyStringIte.hasNext()) {
                String key = keyStringIte.next();
                if (prop.get(key) == null) {
                    cacheMap.remove(key);
                    isModified = true;
                    changedConfig.put(key, "");
                    log.info(file.getAbsolutePath() + " property [" + key + "] is deleted");
                }
            }

            if (isModified) {
                this.setChanged();
                passedMap.put(filePrefix, changedConfig);
                this.notifyObservers(passedMap);
            } else {
                log.info(file.getAbsolutePath() + " nothing is changed!!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage(), e.getCause());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e1) {
                    log.error(e1.getMessage(), e1.getCause());
                }
            }
        }

        log.info("<<<<reload " + file.getAbsolutePath() + " end<<<<");
    }

    /**
     * 初始化加载配置文件
     *
     * @param file
     */
    private void loadProperties(File file, Map<String, String> cacheMap) {
        log.info(">>>>load " + file.getAbsolutePath() + " begin>>>>");
        BufferedInputStream inputStream = null;
        String filePrefix = file.getName().substring(0, file.getName().indexOf("."));
        try {
            inputStream = new BufferedInputStream(new FileInputStream(file));
            Properties prop = new Properties();
            prop.load(inputStream);
            Iterator<Entry<Object, Object>> ite = prop.entrySet().iterator();
            while (ite.hasNext()) {
                Entry<Object, Object> entry = ite.next();
                if (entry.getKey().toString().startsWith(filePrefix + ".")) {
                    cacheMap.put(String.valueOf(entry.getKey()), entry.getValue() != null ? String.valueOf(entry.getValue()) : null);
                    log.info(file.getAbsolutePath() + " add property [" + entry.getKey() + "=" + entry.getValue() + "]");
                } else {
                    log.error(file.getAbsoluteFile() + " property [" + entry.getKey() + "=" + entry.getValue() + "] cannot be cached , because the key prefix cantnot match the file name");
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e1) {
                    log.error(e1.getMessage(), e1.getCause());
                }
            }
        }
        log.info("<<<<load " + file.getAbsolutePath() + " end<<<<");
    }

    public static String get(String key) {
        String keyPrefix = key.substring(0, key.indexOf("."));
        Map<String, String> cacheMap = SysProperties.getInstance().FLOW_DEFINE_CACHE.get(keyPrefix);
        if (cacheMap == null) {
            return null;
        }
        return cacheMap.get(key);
    }

    /**
     * 根据配置文件名前缀获取该配置文件的所有配置项
     *
     * @param fileNamePrefix
     * @return
     */
    public static Map<String, String> getConfigMap(String fileNamePrefix) {
        return SysProperties.getInstance().FLOW_DEFINE_CACHE.get(fileNamePrefix);
    }

}
