/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.servlets;

import com.mirth.connect.client.core.BrandingConstants;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.jaxrs2.integration.ServletOpenApiContextBuilder;
import io.swagger.v3.oas.integration.OpenApiConfigurationException;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ConfigurationBuilder;

import com.mirth.connect.client.core.Version;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;

public class SwaggerServlet extends HttpServlet {

	private String basePath;
	private Version version;
	private Version apiVersion;
	private Set<String> resourcePackages;
	private Set<Class<?>> resourceClasses;
	private boolean allowHTTP;
	private Logger logger = LogManager.getLogger(this.getClass());

	public SwaggerServlet(String basePath, Version version, Version apiVersion, Set<String> resourcePackages,
			Set<Class<?>> resourceClasses, boolean allowHTTP) {
		this.basePath = basePath;
		this.version = version;
		this.apiVersion = apiVersion;
		this.resourcePackages = resourcePackages;
		this.resourceClasses = resourceClasses;
		this.allowHTTP = allowHTTP;
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		OpenAPI oas = new OpenAPI();
		
		List<Server> servers = new ArrayList<Server>();
		servers.add(new Server().url(basePath));
		oas.servers(servers);
		
		Info info = new Info().title(String.format("%s Client API", BrandingConstants.PRODUCT_NAME))
				.description(String.format("Swagger documentation for the %s Client API.", BrandingConstants.PRODUCT_NAME))
				.version(apiVersion.toString());

		oas.info(info);
		addConnectorPropertiesSchemas(oas);
		SwaggerConfiguration oasConfig = new SwaggerConfiguration()
				.openAPI(oas)
				.resourceClasses(resourceClasses.stream().map(Class::getName).collect(Collectors.toSet()));

		try {
			new ServletOpenApiContextBuilder()
				.servletConfig(config)
				.openApiConfiguration(oasConfig)
				.buildContext(true)
				.read();			
		} catch (OpenApiConfigurationException e) {
			logger.error("Failed to initialize Swagger servlet", e);
			throw new ServletException(e.getMessage(), e);
		}
	}

	private void addConnectorPropertiesSchemas(OpenAPI openApi) {
		Components components = openApi.getComponents();
		if (components == null) {
			components = new Components();
			openApi.setComponents(components);
		}

		Map<String, Schema> schemas = components.getSchemas();
		if (schemas == null) {
			schemas = new java.util.LinkedHashMap<>();
			components.setSchemas(schemas);
		}

		SortedSet<Class<? extends ConnectorProperties>> subtypes = findConnectorPropertiesSubtypes();
		if (subtypes.isEmpty()) {
			return;
		}

		ComposedSchema connectorPropertiesSchema = new ComposedSchema();
		Schema<?> existingSchema = schemas.get("ConnectorProperties");
		if (existingSchema instanceof ComposedSchema) {
			connectorPropertiesSchema = (ComposedSchema) existingSchema;
		}
		connectorPropertiesSchema.setOneOf(new ArrayList<>());

		for (Class<? extends ConnectorProperties> subtype : subtypes) {
			addSubtypeSchema(schemas, connectorPropertiesSchema, subtype);
		}

		schemas.put("ConnectorProperties", connectorPropertiesSchema);
	}

	private SortedSet<Class<? extends ConnectorProperties>> findConnectorPropertiesSubtypes() {
		Reflections reflections = new Reflections(new ConfigurationBuilder()
				.forPackages("com.mirth.connect")
				.addScanners(new SubTypesScanner(false)));

		SortedSet<Class<? extends ConnectorProperties>> subtypes = new TreeSet<>(
				Comparator.comparing(Class::getName));
		for (Class<? extends ConnectorProperties> subtype : reflections.getSubTypesOf(ConnectorProperties.class)) {
			if (!subtype.isInterface() && !java.lang.reflect.Modifier.isAbstract(subtype.getModifiers())) {
				subtypes.add(subtype);
			}
		}
		return subtypes;
	}

	private void addSubtypeSchema(Map<String, Schema> schemas, ComposedSchema connectorPropertiesSchema, Class<? extends ConnectorProperties> subtype) {
		Map<String, Schema> subtypeSchemas = ModelConverters.getInstance().readAll(subtype);
		if (subtypeSchemas != null) {
			subtypeSchemas.forEach(schemas::putIfAbsent);
		}

		String schemaName = resolveSchemaName(subtypeSchemas, subtype);
		if (schemaName != null) {
			Schema<?> refSchema = new Schema<>();
			refSchema.set$ref("#/components/schemas/" + schemaName);
			connectorPropertiesSchema.addOneOfItem(refSchema);
		}
	}

	private String resolveSchemaName(Map<String, Schema> subtypeSchemas, Class<? extends ConnectorProperties> subtype) {
		if (subtypeSchemas == null || subtypeSchemas.isEmpty()) {
			return null;
		}

		String simpleName = subtype.getSimpleName();
		if (subtypeSchemas.containsKey(simpleName)) {
			return simpleName;
		}

		return subtypeSchemas.keySet().iterator().next();
	}
}