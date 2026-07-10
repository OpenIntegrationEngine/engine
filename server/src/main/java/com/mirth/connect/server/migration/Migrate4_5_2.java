package com.mirth.connect.server.migration;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.core.PropertiesConfigurationUtil;
import com.mirth.connect.model.util.MigrationException;
import com.mirth.connect.server.tools.ClassPathResource;

public class Migrate4_5_2 extends Migrator implements ConfigurationMigrator {
	private Logger logger = LogManager.getLogger(getClass());
	
	@Override
	public void migrate() throws MigrationException {
		migrateLog4jProperties();
	}
	
	private void migrateLog4jProperties() {
		updateAppender("log4j2.properties");
		updateAppender("log4j2-cli.properties");
	}

	private void updateAppender(String fileName) {
		try {
			URI uri = ClassPathResource.getResourceURI(fileName);
			if (uri == null) {
				logger.info("Migration could not find {}.", fileName);
				return;
			}
			FileBasedConfigurationBuilder<PropertiesConfiguration> builder = PropertiesConfigurationUtil.createBuilder(new File(uri));
			PropertiesConfiguration properties = builder.getConfiguration();
			String consoleCharset = (String) properties.getProperty("appender.console.layout.charset");
			if (StringUtils.isBlank(consoleCharset)) {
				properties.setProperty("appender.console.layout.charset", "UTF-8");
				builder.save();
			}
	 	} catch (ConfigurationException | IOException e) {
			logger.error(String.format("Failed to migrate %s.", fileName), e);
		}
	}

	@Override
	public Map<String, Object> getConfigurationPropertiesToAdd() {
		return null;
	}

	@Override
	public String[] getConfigurationPropertiesToRemove() {
		return null;
	}

	@Override
	public void updateConfiguration(PropertiesConfiguration configuration) {}


	@Override
	public void migrateSerializedData() throws MigrationException {}

}
