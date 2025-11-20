/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.serverlog;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class ServerLogItem implements Serializable {

    public static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private String serverId;
    private Long id;
    private String channelId;
    private String level;
    private Date date;
    private String threadName;
    private String category;
    private String lineNumber;
    private String message;
    private String throwableInformation;
    private String channelName;
    private HashMap<String, Object> context;

    public ServerLogItem() {}

    public ServerLogItem(Map<String, Object> properties) {
        if (properties != null) {
            this.context = new HashMap<>(properties);
        } else {
            this.context = new HashMap<>();
        }

        this.serverId = (String) context.getOrDefault("serverId", "");
        this.id = (Long) context.getOrDefault("id", 0L);
        this.channelId = (String) context.getOrDefault("channelId", "");
        this.channelName = (String) context.getOrDefault("channelName", "");
        this.level = (String) context.getOrDefault("level", "");
        this.date = (Date) context.getOrDefault("date", Date.from(Instant.now()));
        this.threadName = (String) context.getOrDefault("threadName", "");
        this.category = (String) context.getOrDefault("category", "");
        this.lineNumber = (String) context.getOrDefault("lineNumber", "");
        this.message = (String) context.getOrDefault("message", "");
        this.throwableInformation = (String) context.getOrDefault("throwableInformation", "");
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    
    public String getChannelId() {
        return channelId;
    }
    
    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(String lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getThrowableInformation() {
        return throwableInformation;
    }

    public void setThrowableInformation(String throwableInformation) {
        this.throwableInformation = throwableInformation;
    }
    
    public String getChannelName() {
        return channelName;
    }
    
    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }
    
    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = new HashMap<>(context);
    }

    @Override
    public String toString() {
        if (id != null) {
            StringBuilder builder = new StringBuilder();
            builder.append('[').append(DATE_FORMAT.format(date)).append("]  ");
            builder.append(level);
            builder.append("  (").append(category);
            if (StringUtils.isNotBlank(lineNumber)) {
                builder.append(':').append(lineNumber);
            }
            builder.append("): ").append(message);
            if (StringUtils.isNotBlank(throwableInformation)) {
                builder.append('\n').append(throwableInformation);
            }
            return builder.toString();
        } else {
            return message;
        }
    }
}
