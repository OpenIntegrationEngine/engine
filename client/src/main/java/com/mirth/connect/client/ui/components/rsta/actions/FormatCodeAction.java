// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Simon Braconnier <simonbraconnier@gmail.com>

package com.mirth.connect.client.ui.components.rsta.actions;

import java.awt.event.ActionEvent;

import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Element;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;

import com.mirth.connect.client.ui.components.rsta.MirthRSyntaxTextArea;
import com.mirth.connect.util.JavaScriptSharedUtil;

public class FormatCodeAction extends MirthRecordableTextAction {

    public FormatCodeAction(MirthRSyntaxTextArea textArea) {
        super(textArea, ActionInfo.FORMAT_CODE);
    }

    @Override
    public void actionPerformedImpl(ActionEvent evt) {

        if (!textArea.isEditable() || !textArea.isEnabled()) {
            UIManager.getLookAndFeel().provideErrorFeedback(textArea);
            return;
        }

        Caret c = textArea.getCaret();
        int start = Math.min(c.getDot(), c.getMark());
        int end = Math.max(c.getDot(), c.getMark());
        if (start == end) {
            formatAll();
        } else {
            formatRange(start, end);
        }

    }

    @Override
    public boolean isEnabled() {
        return textArea.isEditable() && textArea.isEnabled();
    }

    private void formatAll() {

        String code = textArea.getText();
        formatAndReplace(code, 0, code.length());
    }

    private void formatRange(int start, int end) {

        // We want to format all the lines of the selection (not only the selected text)

        try {
            RSyntaxDocument doc = (RSyntaxDocument) textArea.getDocument();
            Element map = doc.getDefaultRootElement();

            // Get the lines indexes from the selected text indexes.
            int startLine = map.getElementIndex(start);
            int endLine = map.getElementIndex(end);

            int replaceRangeStart = 0;
            int replaceRangeEnd = 0;

            // Build a string from the selected lines.
            StringBuilder sb = new StringBuilder();
            for (int line = startLine; line <= endLine; line++) {
                Element elem = map.getElement(line);

                int startOffset = elem.getStartOffset();
                int endOffset = elem.getEndOffset() - 1;

                // Save the start index of the first line for the final replacement.
                if (line == startLine) {
                    replaceRangeStart = startOffset;
                }

                // Save the end index of the last line for the final replacement.
                if (line == endLine) {
                    replaceRangeEnd = endOffset;
                }

                sb.append(doc.getText(startOffset, endOffset - startOffset + 1));
            }

            // Format the code and replace the selected lines.
            formatAndReplace(sb.toString(), replaceRangeStart, replaceRangeEnd);

        } catch (BadLocationException ble) {
            ble.printStackTrace();
            UIManager.getLookAndFeel().provideErrorFeedback(textArea);
        }
    }

    private void formatAndReplace(String code, int start, int end) {

        String formattedCode = JavaScriptSharedUtil.prettyPrint(code);
        textArea.replaceRange(formattedCode, start, end);
        textArea.setCaretPosition(start);
    }
}
