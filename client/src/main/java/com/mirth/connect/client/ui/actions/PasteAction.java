/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.ui.actions;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.ui.components.MirthTextInterface;

/** Allows for Pasting in text components. */
public class PasteAction extends AbstractAction {

    private static final Logger logger = LogManager.getLogger(PasteAction.class);
    MirthTextInterface comp;

    public PasteAction(MirthTextInterface comp) {
        super("Paste");
        this.comp = comp;
    }

    public void actionPerformed(ActionEvent e) {
        comp.paste();
    }

    public boolean isEnabled() {
        if (comp.isVisible() && comp.isEditable() && comp.isEnabled()) {
            try {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                if (clipboard != null) {
                    Transferable contents = clipboard.getContents(this);
                    if (contents != null) {
                        return contents.isDataFlavorSupported(DataFlavor.stringFlavor);
                    }
                }
                return false;
            } catch (IllegalStateException e) {
                return false;
            } catch (Error e) {
                logger.warn("Could not check clipboard contents.", e);
                return false;
            }
        } else {
            return false;
        }
    }
}
