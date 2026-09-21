/*
 * Copyright (c) Open Integration Engine contributors.
 * Licensed under the Mozilla Public License 2.0.
 */

package com.mirth.connect.model.converters;

import java.util.ArrayList;
import java.util.List;

import com.mirth.connect.client.core.ExtensionDependency;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/** Keeps dependency declarations unambiguous for both XStream and the bootstrap DOM reader. */
public class ExtensionDependenciesConverter implements Converter {
    @Override
    public boolean canConvert(Class type) {
        return type == null || List.class.isAssignableFrom(type);
    }

    @Override
    public void marshal(Object value, HierarchicalStreamWriter writer, MarshallingContext context) {
        for (ExtensionDependency dependency : (List<ExtensionDependency>) value) {
            writer.startNode("dependency");
            if (dependency != null) {
                writeAttribute(writer, "type", dependency.getType());
                writeAttribute(writer, "name", dependency.getName());
                writeAttribute(writer, "minVersion", dependency.getMinVersion());
            }
            writer.endNode();
        }
    }

    private void writeAttribute(HierarchicalStreamWriter writer, String name, String value) {
        if (value != null) {
            writer.addAttribute(name, value);
        }
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        if (context.getRequiredType() == null) {
            return null;
        }
        List<ExtensionDependency> dependencies = new ArrayList<>();
        if (!reader.getValue().trim().isEmpty()) {
            throw new ConversionException("Dependencies must contain dependency elements.");
        }
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            if (!"dependency".equals(reader.getNodeName()) || reader.hasMoreChildren() || !reader.getValue().trim().isEmpty()) {
                throw new ConversionException("Expected a dependency with type, name and minVersion attributes.");
            }
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                String name = reader.getAttributeName(i);
                if (!"type".equals(name) && !"name".equals(name) && !"minVersion".equals(name)) {
                    throw new ConversionException("Unknown dependency attribute: " + name);
                }
            }
            dependencies.add(new ExtensionDependency(reader.getAttribute("type"), reader.getAttribute("name"), reader.getAttribute("minVersion")));
            reader.moveUp();
        }
        return dependencies;
    }
}
