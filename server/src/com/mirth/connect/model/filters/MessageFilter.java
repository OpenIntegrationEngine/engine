/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.model.filters;

import java.io.Serializable;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.model.MessageFilterToStringStyle;
import com.mirth.connect.model.filters.elements.ContentSearchElement;
import com.mirth.connect.model.filters.elements.MetaDataSearchElement;
import com.mirth.connect.model.filters.elements.MetaDataSearchOperator;
import com.mirth.connect.donkey.model.message.ContentType;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * A MessageFilter is used to search the message store.
 * 
 */
@XStreamAlias("messageFilter")
public class MessageFilter implements Serializable {
    private static final MessageFilter EMPTY_FILTER = new MessageFilter();

    /*
     * Note that any filter criteria that is an int must be represented using Integer otherwise it
     * will default to 0 and not pass the isNotNull check in the SQL mapping.
     */
    private Long maxMessageId;
    private Long minMessageId;
    private Long originalIdUpper;
    private Long originalIdLower;
    private Long importIdUpper;
    private Long importIdLower;
    private Calendar startDate;
    private Calendar endDate;
    private String textSearch;
    private Boolean textSearchRegex;
    private Set<Status> statuses;
    private List<Integer> includedMetaDataIds;
    private List<Integer> excludedMetaDataIds;
    private String serverId;
    private List<ContentSearchElement> contentSearch;
    private List<MetaDataSearchElement> metaDataSearch;
    private List<String> textSearchMetaDataColumns;
    private Integer sendAttemptsLower;
    private Integer sendAttemptsUpper;
    /*
     * Despite the comment above, "false" for attachment and error actually mean "not set".
     * There's no way to search for messages that don't have attachments/errors.
     */
    private Boolean attachment;
    private Boolean error;

    public Long getOriginalIdUpper() {
        return originalIdUpper;
    }

    public void setOriginalIdUpper(Long originalIdUpper) {
        this.originalIdUpper = originalIdUpper;
    }

    public Long getOriginalIdLower() {
        return originalIdLower;
    }

    public void setOriginalIdLower(Long originalIdLower) {
        this.originalIdLower = originalIdLower;
    }

    public Long getImportIdUpper() {
        return importIdUpper;
    }

    public void setImportIdUpper(Long importIdUpper) {
        this.importIdUpper = importIdUpper;
    }

    public Long getImportIdLower() {
        return importIdLower;
    }

    public void setImportIdLower(Long importIdLower) {
        this.importIdLower = importIdLower;
    }

    public Calendar getStartDate() {
        return startDate;
    }

    public void setStartDate(Calendar startDate) {
        this.startDate = startDate;
    }

    public Calendar getEndDate() {
        return endDate;
    }

    public void setEndDate(Calendar endDate) {
        this.endDate = endDate;
    }

    public String getTextSearch() {
        return textSearch;
    }

    public void setTextSearch(String textSearch) {
        this.textSearch = textSearch;
    }

    public Boolean getTextSearchRegex() {
        return this.textSearchRegex;
    }

    public void setTextSearchRegex(Boolean textSearchRegex) {
        this.textSearchRegex = textSearchRegex;
    }

    public Set<Status> getStatuses() {
        return statuses;
    }

    public void setStatuses(Set<Status> statuses) {
        this.statuses = statuses;
    }

    public List<Integer> getIncludedMetaDataIds() {
        return includedMetaDataIds;
    }

    public void setIncludedMetaDataIds(List<Integer> includedMetaDataIds) {
        this.includedMetaDataIds = includedMetaDataIds;
    }

    public List<Integer> getExcludedMetaDataIds() {
        return excludedMetaDataIds;
    }

    public void setExcludedMetaDataIds(List<Integer> excludedMetaDataIds) {
        this.excludedMetaDataIds = excludedMetaDataIds;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public List<ContentSearchElement> getContentSearch() {
        return contentSearch;
    }

    public void setContentSearch(List<ContentSearchElement> contentSearch) {
        this.contentSearch = contentSearch;
    }

    public List<MetaDataSearchElement> getMetaDataSearch() {
        return metaDataSearch;
    }

    public void setMetaDataSearch(List<MetaDataSearchElement> metaDataSearch) {
        this.metaDataSearch = metaDataSearch;
    }

    public List<String> getTextSearchMetaDataColumns() {
        return textSearchMetaDataColumns;
    }

    public void setTextSearchMetaDataColumns(List<String> textSearchMetaDataColumns) {
        this.textSearchMetaDataColumns = textSearchMetaDataColumns;
    }

    public Integer getSendAttemptsLower() {
        return sendAttemptsLower;
    }

    public void setSendAttemptsLower(Integer sendAttemptsLower) {
        this.sendAttemptsLower = sendAttemptsLower;
    }

    public Integer getSendAttemptsUpper() {
        return sendAttemptsUpper;
    }

    public void setSendAttemptsUpper(Integer sendAttemptsUpper) {
        this.sendAttemptsUpper = sendAttemptsUpper;
    }

    public Long getMaxMessageId() {
        return maxMessageId;
    }

    public void setMaxMessageId(Long maxMessageId) {
        this.maxMessageId = maxMessageId;
    }

    public Long getMinMessageId() {
        return minMessageId;
    }

    public void setMinMessageId(Long minMessageId) {
        this.minMessageId = minMessageId;
    }

    public Boolean getAttachment() {
        return attachment;
    }

    public void setAttachment(Boolean attachment) {
        this.attachment = attachment;
    }

    public Boolean getError() {
        return error;
    }

    public void setError(Boolean error) {
        this.error = error;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MessageFilter filter && equals(filter);
    }

    /** Check if the filter has logically identical criteria */
    public boolean equals(MessageFilter filter) {
        return filter != null
            && Objects.equals(maxMessageId, filter.maxMessageId)
            && Objects.equals(minMessageId, filter.minMessageId)
            && Objects.equals(originalIdUpper, filter.originalIdUpper)
            && Objects.equals(originalIdLower, filter.originalIdLower)
            && Objects.equals(importIdUpper, filter.importIdUpper)
            && Objects.equals(importIdLower, filter.importIdLower)
            && Objects.equals(startDate, filter.startDate)
            && Objects.equals(endDate, filter.endDate)
            && Objects.equals(textSearch, filter.textSearch)
            && Objects.equals(textSearchRegex, filter.textSearchRegex)
            && Objects.equals(statuses, filter.statuses)
            && Objects.equals(
                (includedMetaDataIds == null || includedMetaDataIds.isEmpty()) ? null : includedMetaDataIds,
                (filter.includedMetaDataIds == null || filter.includedMetaDataIds.isEmpty()) ? null : filter.includedMetaDataIds)
            && Objects.equals(
                (excludedMetaDataIds == null || excludedMetaDataIds.isEmpty()) ? null : excludedMetaDataIds,
                (filter.excludedMetaDataIds == null || filter.excludedMetaDataIds.isEmpty()) ? null : filter.excludedMetaDataIds)
            && Objects.equals(serverId, filter.serverId)
            && Objects.equals(contentSearch, filter.contentSearch)
            && Objects.equals(metaDataSearch, filter.metaDataSearch)
            && Objects.equals(textSearchMetaDataColumns, filter.textSearchMetaDataColumns)
            && Objects.equals(sendAttemptsLower, filter.sendAttemptsLower)
            && Objects.equals(sendAttemptsUpper, filter.sendAttemptsUpper)
            && Objects.equals(Boolean.TRUE.equals(attachment), Boolean.TRUE.equals(filter.attachment))
            && Objects.equals(Boolean.TRUE.equals(error), Boolean.TRUE.equals(filter.error));
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxMessageId, startDate, endDate, textSearch);
    }

    /** Check if the filter has any criteria */
    public boolean isEmpty() {
        return this.equals(EMPTY_FILTER);
    }

    /** Serialize to event log audit string */
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, MessageFilterToStringStyle.instance());
    }

    /** Serialize to a human-readable version of the filter, shown in MessageBrowser */
    public String toDisplayString() {
        return toDisplayString(Map.of(), "\n", false);
    }

    /** 
     * Serialize to a human-readable version of the filter, shown in MessageBrowser 
     * @param connectors A map of connector IDs to names, used to display connector criteria in a more user-friendly way
     * @param padding A string to insert between criteria
     * @param includeEmptyCriteria Whether to include criteria that are not set (e.g. "Statuses: (any)") in the output
     */
    public String toDisplayString(Map<Integer, String> connectors, String padding, boolean includeEmptyCriteria) {
        StringBuilder text = new StringBuilder();

        if (maxMessageId != null) {
            text.append("Max Message Id: ");
            text.append(maxMessageId);
        }

        if (minMessageId != null) {
            if (!text.isEmpty()) text.append(padding);
            text.append("Min Message Id: ");
            text.append(minMessageId);
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        if (includeEmptyCriteria || startDate != null || endDate != null) {
            if (!text.isEmpty()) text.append(padding);
            text.append("Date Range: ");

            if (startDate == null) {
                text.append("(any)");
            } else {
                text.append(dateFormat.format(startDate.getTime()));
            }

            text.append(" to ");

            if (endDate == null) {
                text.append("(any)");
            } else {
                text.append(dateFormat.format(endDate.getTime()));
            }
        }

        if (includeEmptyCriteria || (statuses != null && !statuses.isEmpty())) {
            if (!text.isEmpty()) text.append(padding);
            text.append("Statuses: ");

            if (statuses == null) {
                text.append("(any)");
            } else {
                text.append(StringUtils.join(statuses, ", "));
            }
        }

        if (textSearch != null) {
            if (!text.isEmpty()) text.append(padding);
            text.append("Text Search: ");
            text.append(textSearch);
        }

        if (includeEmptyCriteria
            || (includedMetaDataIds != null && !includedMetaDataIds.isEmpty())
            || (excludedMetaDataIds != null && !excludedMetaDataIds.isEmpty())
        ) {
            getConnectorSearchCriteriaText(text, padding, connectors);
        }

        if (originalIdLower != null || originalIdUpper != null) {
            if (!text.isEmpty()) text.append(padding);
            text.append("Original Id: ");
            if (originalIdUpper == null) {
                text.append("Greater than ");
                text.append(originalIdLower);
            } else if (originalIdLower == null) {
                text.append("Less than ");
                text.append(originalIdUpper);
            } else {
                text.append("Between ");
                text.append(originalIdLower);
                text.append(" and ");
                text.append(originalIdUpper);
            }
        }

        if (importIdLower != null || importIdUpper != null) {
            if (!text.isEmpty()) text.append(padding);
            text.append("Import Id: ");
            if (importIdUpper == null) {
                text.append("Greater than ");
                text.append(importIdLower);
            } else if (importIdLower == null) {
                text.append("Less than ");
                text.append(importIdUpper);
            } else {
                text.append("Between ");
                text.append(importIdLower);
                text.append(" and ");
                text.append(importIdUpper);
            }
        }

        if (serverId != null) {
            if (!text.isEmpty()) text.append(padding);
            text.append("Server Id: ");
            text.append(serverId);
        }

        if (sendAttemptsLower != null || sendAttemptsUpper != null) {
            if (!text.isEmpty()) text.append(padding);
            text.append("# of Send Attempts: ");

            if (sendAttemptsLower != null) {
                text.append(sendAttemptsLower);
            } else {
                text.append("(any)");
            }

            text.append(" - ");

            if (sendAttemptsUpper != null) {
                text.append(sendAttemptsUpper);
            } else {
                text.append("(any)");
            }
        }

        if (contentSearch != null) {
            for (ContentSearchElement element : contentSearch) {
                for (String value : element.getSearches()) {
                    if (!text.isEmpty()) text.append(padding);
                    text.append(ContentType.fromCode(element.getContentCode()));
                    text.append(" contains \"");
                    text.append(value);
                    text.append("\"");
                }
            }
        }

        if (metaDataSearch != null) {
            for (MetaDataSearchElement element : metaDataSearch) {
                if (!text.isEmpty()) text.append(padding);
                text.append(element.getColumnName());
                text.append(" ");
                text.append(MetaDataSearchOperator.fromString(element.getOperator()).toString());
                text.append(" ");
                if (element.getValue() instanceof Calendar calendar) {
                    text.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(calendar.getTime()));
                } else {
                    text.append(element.getValue());
                }
                if (Boolean.TRUE.equals(element.getIgnoreCase())) {
                    text.append(" (Ignore Case)");
                }
            }
        }

        if (Boolean.TRUE.equals(attachment)) {
            if (!text.isEmpty()) text.append(padding);
            text.append("Has Attachment");
        }

        if (Boolean.TRUE.equals(error)) {
            if (!text.isEmpty()) text.append(padding);
            text.append("Has Error");
        }

        if (text.length() == 0) {
            text.append("(no criteria)");
        }

        return text.toString();
    }
    
    private void getConnectorSearchCriteriaText(StringBuilder text, String padding, Map<Integer, String> connectors) {
    	if (!text.isEmpty()) text.append(padding);
    	text.append("Connectors: ");

        if (includedMetaDataIds == null) {
            text.append("(any)");
        } else if (includedMetaDataIds.isEmpty()) {
            text.append("(none)");
        } else {
            boolean first = true;
            for (var includedConnectorId : includedMetaDataIds) {
                if (!first) text.append(", ");
                first = false;

                text.append(connectors.getOrDefault(includedConnectorId, String.valueOf(includedConnectorId)));
            }
        }
        
        if (excludedMetaDataIds != null && !excludedMetaDataIds.isEmpty()) {
            text.append(" except ");

            boolean first = true;
            for (var excludedConnectorId : excludedMetaDataIds) {
                if (!first) text.append(", ");
                first = false;

                text.append(connectors.getOrDefault(excludedConnectorId, String.valueOf(excludedConnectorId)));
            }
        }
    }
}
