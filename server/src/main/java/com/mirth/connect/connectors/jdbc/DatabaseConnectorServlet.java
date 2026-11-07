/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Set;
import java.util.SortedSet;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.controllers.ContextFactoryController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.util.TemplateValueReplacer;
import com.mirth.connect.server.util.javascript.MirthContextFactory;

public class DatabaseConnectorServlet extends MirthServlet implements DatabaseConnectorServletInterface {

    private static final Logger logger = LogManager.getLogger(DatabaseConnectorServlet.class);
    private static final TemplateValueReplacer replacer = new TemplateValueReplacer();
    private static final ContextFactoryController contextFactoryController = ControllerFactory.getFactory().createContextFactoryController();

    public DatabaseConnectorServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc, PLUGIN_POINT);
    }

    @Override
    public SortedSet<Table> getTables(String channelId, String channelName, String driver, String url, String username, String password, Set<String> tableNamePatterns, Set<String> resourceIds) {
        CustomDriver customDriver = null;
        Connection connection = null;
        try {
            url = replacer.replaceValues(url, channelId, channelName);
            username = replacer.replaceValues(username, channelId, channelName);
            password = replacer.replaceValues(password, channelId, channelName);

            try {
                MirthContextFactory contextFactory = contextFactoryController.getContextFactory(resourceIds);

                try {
                    ClassLoader isolatedClassLoader = contextFactory.getIsolatedClassLoader();
                    if (isolatedClassLoader != null) {
                        customDriver = new CustomDriver(isolatedClassLoader, driver);
                        logger.debug("Custom driver created: " + customDriver.toString() + ", Version " + customDriver.getMajorVersion() + "." + customDriver.getMinorVersion());
                    } else {
                        logger.debug("Custom classloader is not being used, defaulting to DriverManager.");
                    }
                } catch (Exception e) {
                    logger.debug("Error creating custom driver, defaulting to DriverManager.", e);
                }
            } catch (Exception e) {
                logger.debug("Error retrieving context factory, defaulting to DriverManager.", e);
            }

            if (customDriver == null) {
                Class.forName(driver);
            }

            int oldLoginTimeout = DriverManager.getLoginTimeout();
            DriverManager.setLoginTimeout(30);

            if (customDriver != null) {
                connection = customDriver.connect(url, username, password);
            } else {
                connection = DriverManager.getConnection(url, username, password);
            }

            DriverManager.setLoginTimeout(oldLoginTimeout);

            return TableMetadataReader.getTables(connection, tableNamePatterns, username);
        } catch (Exception e) {
            throw new MirthApiException(new Exception("Could not retrieve database tables and columns.", e));
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                }
            }
        }
    }

}