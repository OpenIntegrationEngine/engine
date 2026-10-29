// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: 2025 Richard Ogin
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.server.userutil;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.mozilla.javascript.Context;

import com.mirth.connect.donkey.server.Constants;
import com.mirth.connect.userutil.ImmutableConnectorMessage;

/**
 * Utility class used in the preprocessor or source filter/transformer to prevent the message from
 * being sent to specific destinations.
 * <p>
 * This is a set of the metaDataIds of the destinations the message will be sent to. Methods that
 * look up or remove destinations accept either a metaDataId or a destination connector name.
 * Destinations cannot be added to the set.
 */
public class DestinationSet extends AbstractSet<Integer> {

    private final Map<String, Integer> destinationIdMap;
    private final Set<Integer> metaDataIds;

    /**
     * DestinationSet instances should NOT be constructed manually. The instance "destinationSet"
     * provided in the scope should be used.
     *
     * @param connectorMessage
     *            The delegate ImmutableConnectorMessage object.
     */
    @SuppressWarnings("unchecked")
    public DestinationSet(ImmutableConnectorMessage connectorMessage) {
        Map<String, Integer> destinationIdMap = null;
        Set<Integer> metaDataIds = null;

        try {
            // No pattern matching here: the client's reference parser only understands Java 5 syntax
            Object set = connectorMessage.getSourceMap().get(Constants.DESTINATION_SET_KEY);
            if (set instanceof Set) {
                destinationIdMap = connectorMessage.getDestinationIdMap();
                metaDataIds = (Set<Integer>) set;
            }
        } catch (Exception e) {
        }

        this.destinationIdMap = destinationIdMap != null ? destinationIdMap : Collections.emptyMap();
        this.metaDataIds = metaDataIds != null ? metaDataIds : Collections.emptySet();
    }

    /**
     * Stop a destination from being processed for this message.
     *
     * @param metaDataIdOrConnectorName
     *            An integer representing the metaDataId of a destination connector, or the actual
     *            destination connector name.
     * @return A boolean indicating whether at least one destination connector was actually removed
     *         from processing for this message.
     */
    @Override
    public boolean remove(Object metaDataIdOrConnectorName) {
        Integer metaDataId = convertToMetaDataId(metaDataIdOrConnectorName);

        if (metaDataId != null) {
            return metaDataIds.remove(metaDataId);
        }

        return false;
    }

    /**
     * Stop a destination from being processed for this message.
     * This is the same as {@link #removeAll(Collection)}.
     *
     * @param metaDataIdOrConnectorNames
     *            A collection of integers representing the metaDataId of a destination connectors,
     *            or the actual destination connector names. JavaScript arrays can be used.
     * @return A boolean indicating whether at least one destination connector was actually removed
     *         from processing for this message.
     */
    public boolean remove(Collection<Object> metaDataIdOrConnectorNames) {
        return removeAll(metaDataIdOrConnectorNames);
    }

    /**
     * Stop all except one destination from being processed for this message.
     *
     * @param metaDataIdOrConnectorName
     *            An integer representing the metaDataId of a destination connector, or the actual
     *            destination connector name.
     * @return A boolean indicating whether at least one destination connector was actually removed
     *         from processing for this message.
     */
    public boolean removeAllExcept(Object metaDataIdOrConnectorName) {
        return retainAll(Collections.singleton(metaDataIdOrConnectorName));
    }

    /**
     * Stop all except one destination from being processed for this message.
     * This is the same as {@link #retainAll(Collection)}.
     *
     * @param metaDataIdOrConnectorNames
     *            A collection of integers representing the metaDataId of a destination connectors,
     *            or the actual destination connector names. JavaScript arrays can be used.
     * @return A boolean indicating whether at least one destination connector was actually removed
     *         from processing for this message.
     */
    public boolean removeAllExcept(Collection<Object> metaDataIdOrConnectorNames) {
        return retainAll(metaDataIdOrConnectorNames);
    }

    /**
     * Stop all destinations from being processed for this message. This does NOT mark the source
     * message as FILTERED.
     * <p>
     * This is the same as {@link #clear()}, except that it returns whether any destinations were
     * removed.
     *
     * @return A boolean indicating whether at least one destination connector was actually removed
     *         from processing for this message.
     */
    public boolean removeAll() {
        if (!metaDataIds.isEmpty()) {
            metaDataIds.clear();
            return true;
        }

        return false;
    }

    /**
     * Stop one or more destinations from being processed for this message.
     *
     * @param metaDataIdOrConnectorNames
     *            A collection of integers representing the metaDataId of a destination connectors,
     *            or the actual destination connector names. JavaScript arrays can be used.
     * @return A boolean indicating whether at least one destination connector was actually removed
     *         from processing for this message.
     */
    @Override
    public boolean removeAll(Collection<?> metaDataIdOrConnectorNames) {
        boolean removed = false;

        for (Object metaDataIdOrConnectorName : metaDataIdOrConnectorNames) {
            if (remove(metaDataIdOrConnectorName)) {
                removed = true;
            }
        }

        return removed;
    }

    /**
     * Stop all except the given destinations from being processed for this message.
     *
     * @param metaDataIdOrConnectorNames
     *            A collection of integers representing the metaDataId of a destination connectors,
     *            or the actual destination connector names. JavaScript arrays can be used.
     * @return A boolean indicating whether at least one destination connector was actually removed
     *         from processing for this message.
     */
    @Override
    public boolean retainAll(Collection<?> metaDataIdOrConnectorNames) {
        Set<Integer> set = new HashSet<Integer>();

        for (Object metaDataIdOrConnectorName : metaDataIdOrConnectorNames) {
            Integer metaDataId = convertToMetaDataId(metaDataIdOrConnectorName);

            if (metaDataId != null) {
                set.add(metaDataId);
            }
        }

        return metaDataIds.retainAll(set);
    }

    /**
     * Returns whether a destination will be processed for this message.
     *
     * @param metaDataIdOrConnectorName
     *            An integer representing the metaDataId of a destination connector, or the actual
     *            destination connector name.
     * @return A boolean indicating whether the destination connector will be processed for this
     *         message.
     */
    @Override
    public boolean contains(Object metaDataIdOrConnectorName) {
        return metaDataIds.contains(convertToMetaDataId(metaDataIdOrConnectorName));
    }

    /**
     * Returns an iterator over the metaDataIds of the destinations that will be processed for this
     * message. The iterator's remove method stops that destination from being processed.
     *
     * @return An iterator over the metaDataIds in this set.
     */
    @Override
    public Iterator<Integer> iterator() {
        return metaDataIds.iterator();
    }

    /**
     * Returns the number of destinations that will be processed for this message.
     *
     * @return The number of metaDataIds in this set.
     */
    @Override
    public int size() {
        return metaDataIds.size();
    }

    /**
     * Stop all destinations from being processed for this message. This does NOT mark the source
     * message as FILTERED.
     * <p>
     * This is the same as {@link #removeAll()}, except that it does not return whether any
     * destinations were removed.
     */
    @Override
    public void clear() {
        metaDataIds.clear();
    }

    /**
     * Not supported. Destinations cannot be added to the set.
     *
     * @throws UnsupportedOperationException
     *             always
     */
    @Override
    public boolean add(Integer metaDataId) {
        throw new UnsupportedOperationException("Destinations cannot be added to the destination set.");
    }

    /**
     * Not supported. Destinations cannot be added to the set.
     *
     * @throws UnsupportedOperationException
     *             always
     */
    @Override
    public boolean addAll(Collection<? extends Integer> collection) {
        throw new UnsupportedOperationException("Destinations cannot be added to the destination set.");
    }

    /**
     * Converts a metaDataId or destination connector name to a metaDataId.
     *
     * @param metaDataIdOrConnectorName
     *            A Java or JavaScript number, which is converted to an int, or a destination
     *            connector name, which is looked up in the destination id map.
     * @return The metaDataId, or null if the value is null or is not the name of a destination
     *         connector.
     */
    private Integer convertToMetaDataId(Object metaDataIdOrConnectorName) {
        if (metaDataIdOrConnectorName != null) {
            if (metaDataIdOrConnectorName instanceof Number) {
                return ((Number) metaDataIdOrConnectorName).intValue();
            } else if (metaDataIdOrConnectorName.getClass().getName().equals("org.mozilla.javascript.NativeNumber")) {
                return (Integer) Context.jsToJava(metaDataIdOrConnectorName, int.class);
            } else {
                return destinationIdMap.get(metaDataIdOrConnectorName.toString());
            }
        }

        return null;
    }
}
