package com.dwj.zookeeper;

import com.dwj.resource.SysProperties;

/**
 * @author daiwj
 * @date 2021/06/09
 * @description:
 */
public class ZookeeperUtil {
    /**
     * 获取项目根目录
     * @return
     */
    public static String getRootPath(){
        return SysProperties.get("zookeeper.root.path");
    }

    /**
     * 获取可发布终端节点路径
     * @return
     */
    public static String getServersPath(){
        return getRootPath() + SysProperties.get("config.public.nodes.path");
    }
}
