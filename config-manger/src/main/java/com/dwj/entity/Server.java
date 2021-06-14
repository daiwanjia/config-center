package com.dwj.entity;

/**
 * @author daiwj
 * @date 2021/06/10
 * @description:
 */
public class Server {
    private String name;
    private String ip;

    public String getName() {
        return name;
    }

    public Server setName(String name) {
        this.name = name;
        return this;
    }

    public String getIp() {
        return ip;
    }

    public Server setIp(String ip) {
        this.ip = ip;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("\"name\":\"")
                .append(name).append('\"');
        sb.append(",\"ip\":\"")
                .append(ip).append('\"');
        sb.append('}');
        return sb.toString();
    }
}
