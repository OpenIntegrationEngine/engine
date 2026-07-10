/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.textviewer;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.donkey.model.message.attachment.Attachment;
import com.mirth.connect.plugins.AttachmentViewer;

public class TextViewer extends AttachmentViewer {

    public TextViewer(String name) {
        super(name);
    }

    @Override
    public boolean handleMultiple() {
        return false;
    }

    @Override
    public void viewAttachments(String channelId, Long messageId, String attachmentId) {
        // do viewing code
        Frame frame = new Frame("Text Viewer");
        frame.setLayout(new MigLayout("insets 0, novisualpadding, hidemode 3"));
        frame.setBackground(UIConstants.BACKGROUND_COLOR);

        try {
            final Attachment attachment = parent.mirthClient.getAttachment(channelId, messageId, attachmentId);

            boolean isRTF = attachment.getType().toLowerCase().contains("rtf");
            boolean isBase64Encoded = Base64.isBase64(attachment.getContent());
            byte[] attachmentContent = isBase64Encoded ? Base64.decodeBase64(attachment.getContent()) : attachment.getContent();
            final JEditorPane jEditorPane = new JEditorPane(isRTF ? "text/rtf" : "text/plain", org.apache.commons.codec.binary.StringUtils.newStringUtf8(attachmentContent));

            jEditorPane.setEditable(false);
            JScrollPane scrollPane = new javax.swing.JScrollPane();
            scrollPane.setViewportView(jEditorPane);

            final JCheckBox base64CheckBox = new JCheckBox("Decode Base64 Data");
            base64CheckBox.setBackground(frame.getBackground());
            base64CheckBox.setToolTipText("Check this option if the attachment data is Base64 encoded.");
            base64CheckBox.setSelected(isBase64Encoded ? true : false);
            base64CheckBox.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent evt) {
                    try {
                        jEditorPane.setText(org.apache.commons.codec.binary.StringUtils.newStringUtf8(base64CheckBox.isSelected() ? Base64.decodeBase64(attachment.getContent()) : attachment.getContent()));
                        jEditorPane.setCaretPosition(0);
                    } catch(Exception e) {
                        // reset the check box
                        base64CheckBox.setSelected(!base64CheckBox.isSelected());
                        parent.alertThrowable(parent, e);
                    }
                }
            });

            if (!isBase64Encoded) {
                base64CheckBox.setVisible(false);
            }
            frame.add(base64CheckBox, "gapleft 6, gaptop 6");
            frame.add(scrollPane, "newline, grow, push");

            frame.addWindowListener(new WindowAdapter() {

                public void windowClosing(WindowEvent e) {
                    e.getWindow().dispose();
                }
            });

            frame.setSize(600, 800);

            Dimension dlgSize = frame.getSize();
            Dimension frmSize = parent.getSize();
            Point loc = parent.getLocation();

            if ((frmSize.width == 0 && frmSize.height == 0) || (loc.x == 0 && loc.y == 0)) {
                frame.setLocationRelativeTo(null);
            } else {
                frame.setLocation((frmSize.width - dlgSize.width) / 2 + loc.x, (frmSize.height - dlgSize.height) / 2 + loc.y);
            }

            frame.setVisible(true);
        } catch (Exception e) {
            parent.alertThrowable(parent, e);
        }
    }

    @Override
    public boolean isContentTypeViewable(String contentType) {
        return StringUtils.containsIgnoreCase(contentType, "rtf") || StringUtils.containsIgnoreCase(contentType, "text");
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public void reset() {}

    @Override
    public String getPluginPointName() {
        return "Text Viewer";
    }
}