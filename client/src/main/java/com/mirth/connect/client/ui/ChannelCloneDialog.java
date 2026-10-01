/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * The software in this package is published under the terms of the MPL license.
 */

package com.mirth.connect.client.ui;

import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import com.mirth.connect.model.Channel;
import com.mirth.connect.model.ChannelDependency;
import com.mirth.connect.model.ChannelGroup;
import com.mirth.connect.model.ChannelTag;

import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;

/** Dialog for selecting which channel associations should be copied during a clone. */
public class ChannelCloneDialog extends MirthDialog {

    public interface ChannelNameValidator {
        boolean isValid(String name);
    }

    public static class Option {
        private final String id;
        private final String name;

        public Option(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            return name == null ? id : name;
        }
    }

    private final List<Option> allTags;
    private final List<Option> allDependencies;
    private final Set<String> sourceTagIds;
    private final String sourceId;
    private final ChannelNameValidator channelNameValidator;
    private boolean confirmed;

    private JTextField nameField;
    private JCheckBox pruneCheckBox;
    private JCheckBox codeTemplateCheckBox;
    private JCheckBox resourcesCheckBox;
    private JComboBox<GroupOption> groupComboBox;
    private DefaultListModel<Option> tagModel;
    private DefaultListModel<Option> dependencyModel;
    private JList<Option> tagList;
    private JList<Option> dependencyList;

    public ChannelCloneDialog(Window owner, Channel channel, Collection<ChannelTag> tags, Collection<ChannelDependency> dependencies,
            List<GroupOption> groups, String selectedGroupId, boolean hasCodeTemplates, boolean hasResources, ChannelNameValidator channelNameValidator) {
        super(owner, true);
        sourceId = channel.getId();
        this.channelNameValidator = channelNameValidator;
        allTags = new ArrayList<Option>();
        allDependencies = new ArrayList<Option>();
        sourceTagIds = new HashSet<String>();

        for (ChannelTag tag : tags) {
            allTags.add(new Option(tag.getId(), tag.getName()));
            if (tag.getChannelIds().contains(sourceId)) {
                sourceTagIds.add(tag.getId());
            }
        }

        for (ChannelDependency dependency : dependencies) {
            if (sourceId.equals(dependency.getDependentId())) {
                allDependencies.add(new Option(dependency.getDependencyId(), dependency.getDependencyId()));
            }
        }

        initComponents(channel, groups, selectedGroupId, hasCodeTemplates, hasResources);
        setTitle("Clone channel \"" + channel.getName() + "\"");
        setPreferredSize(new Dimension(650, 540));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
        setVisible(true);
    }

    private void initComponents(Channel channel, List<GroupOption> groups, String selectedGroupId, boolean hasCodeTemplates, boolean hasResources) {
        nameField = new JTextField(StringUtils.substring(channel.getName(), 0, 39) + "1");
        pruneCheckBox = new JCheckBox("Include prune settings", true);
        codeTemplateCheckBox = new JCheckBox("Include code template libraries", hasCodeTemplates);
        resourcesCheckBox = new JCheckBox("Include resources", hasResources);
        groupComboBox = new JComboBox<GroupOption>(new DefaultComboBoxModel<GroupOption>(groups.toArray(new GroupOption[groups.size()])));
        for (int i = 0; i < groupComboBox.getItemCount(); i++) {
            if (selectedGroupId != null && selectedGroupId.equals(groupComboBox.getItemAt(i).getId())) {
                groupComboBox.setSelectedIndex(i);
                break;
            }
        }

        tagModel = new DefaultListModel<Option>();
        for (Option option : allTags) {
            if (channelHasTag(channel, option.getId())) {
                tagModel.addElement(option);
            }
        }
        tagList = createList(tagModel);

        dependencyModel = new DefaultListModel<Option>();
        for (Option option : allDependencies) {
            dependencyModel.addElement(option);
        }
        dependencyList = createList(dependencyModel);

        JPanel content = new JPanel(new MigLayout("insets 12, fill, wrap 2", "[][grow]", "[]10[]10[]10[grow]10[grow]10[]"));
        content.add(new JLabel("New Name:"));
        content.add(nameField, "growx");
        content.add(pruneCheckBox, "span 2");
        content.add(new JLabel("Group:"));
        content.add(groupComboBox, "growx");
        content.add(new JLabel("Tags:"));
        content.add(createListPanel(tagList, tagModel, allTags), "grow, push");
        content.add(new JLabel("Channel Dependencies:"));
        content.add(createListPanel(dependencyList, dependencyModel, allDependencies), "grow, push");
        content.add(codeTemplateCheckBox, "span 2");
        content.add(resourcesCheckBox, "span 2");

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        JButton okButton = new JButton("OK");
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) {
                    JOptionPane.showMessageDialog(ChannelCloneDialog.this, "A channel name is required.", "Invalid Name", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (channelNameValidator != null && !channelNameValidator.isValid(name)) {
                    return;
                }
                confirmed = true;
                dispose();
            }
        });

        JPanel buttons = new JPanel(new MigLayout("insets 0, right"));
        buttons.add(cancelButton, "w 80!");
        buttons.add(okButton, "w 80!");
        getContentPane().setLayout(new MigLayout("insets 0, fill, wrap"));
        getContentPane().add(content, "grow, push");
        getContentPane().add(buttons, "growx");
    }

    private JList<Option> createList(DefaultListModel<Option> model) {
        JList<Option> list = new JList<Option>(model);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        return list;
    }

    private JPanel createListPanel(JList<Option> list, DefaultListModel<Option> model, List<Option> allOptions) {
        JPanel panel = new JPanel(new MigLayout("fill, insets 0", "[grow][]", "[grow][]"));
        panel.add(new JScrollPane(list), "grow, push");
        JButton addButton = new JButton("Add");
        JButton removeButton = new JButton("Remove");
        addButton.addActionListener(e -> {
            List<Option> available = new ArrayList<Option>();
            Set<String> selected = new HashSet<String>();
            for (int i = 0; i < model.size(); i++) {
                selected.add(model.get(i).getId());
            }
            for (Option option : allOptions) {
                if (!selected.contains(option.getId())) {
                    available.add(option);
                }
            }
            if (!available.isEmpty()) {
                Option option = (Option) JOptionPane.showInputDialog(this, "Select an item to add:", "Add", JOptionPane.PLAIN_MESSAGE, null,
                        available.toArray(), available.get(0));
                if (option != null) {
                    model.addElement(option);
                }
            }
        });
        removeButton.addActionListener(e -> {
            for (Option option : list.getSelectedValuesList()) {
                model.removeElement(option);
            }
        });
        JPanel controls = new JPanel(new MigLayout("insets 0, wrap"));
        controls.add(addButton, "w 80!");
        controls.add(removeButton, "w 80!");
        panel.add(controls, "top");
        return panel;
    }

    private boolean channelHasTag(Channel channel, String tagId) {
        return sourceTagIds.contains(tagId);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getChannelName() {
        return nameField.getText().trim();
    }

    public boolean isIncludePruneSettings() {
        return pruneCheckBox.isSelected();
    }

    public boolean isIncludeCodeTemplateLibraries() {
        return codeTemplateCheckBox.isSelected();
    }

    public boolean isIncludeResources() {
        return resourcesCheckBox.isSelected();
    }

    public String getGroupId() {
        GroupOption group = (GroupOption) groupComboBox.getSelectedItem();
        return group == null ? ChannelGroup.DEFAULT_ID : group.getId();
    }

    public Set<String> getTagIds() {
        return getIds(tagModel);
    }

    public Set<String> getDependencyIds() {
        return getIds(dependencyModel);
    }

    private Set<String> getIds(DefaultListModel<Option> model) {
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < model.size(); i++) {
            ids.add(model.get(i).getId());
        }
        return ids;
    }

    public static class GroupOption {
        private final String id;
        private final String name;

        public GroupOption(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
