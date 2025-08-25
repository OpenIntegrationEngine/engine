/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.ui.components;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.DateFormatter;
import javax.swing.text.DefaultFormatterFactory;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;

public class MirthTimePicker extends JSpinner {

    private DateFormatter formatter;
    private final JSpinner spinner;
    private Frame parent;
    private boolean saveEnabled = true;

    public MirthTimePicker() {
        init("hh:mm aa", Calendar.MINUTE);
        spinner = this;
    }

    public MirthTimePicker(String format, int accuracy) {
        init(format, accuracy);
        spinner = this;
    }

    public void init(String format, int accuracy) {
        this.parent = PlatformUI.MIRTH_FRAME;

        //removed the simple date format and replaced with a function to format the date, see below
        //SimpleDateFormat dateFormat = new SimpleDateFormat(format);
        GregorianCalendar calendar = new GregorianCalendar();
        Date now = calendar.getTime();
        SpinnerDateModel dateModel = new SpinnerDateModel(now, null, null, accuracy);
        getEditor().setFont(UIConstants.TEXTFIELD_PLAIN_FONT);
        setModel(dateModel);
        JFormattedTextField tf = ((JSpinner.DefaultEditor) getEditor()).getTextField();

        tf.addKeyListener(new KeyListener() {

            public void keyTyped(KeyEvent e) {}

            public void keyPressed(KeyEvent e) {
                if (saveEnabled) {
                    parent.setSaveEnabled(true);
                }
            }

            public void keyReleased(KeyEvent e) {}
        });

        //Replace the default formatter by a function that uses the given format
        /*DefaultFormatterFactory factory = (DefaultFormatterFactory) tf.getFormatterFactory();
        formatter = (DateFormatter) factory.getDefaultFormatter();
        formatter.setFormat(dateFormat);
        fireStateChanged();*/
        setFormatter(format);

        this.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent event) {
                if (saveEnabled) {
                    parent.setSaveEnabled(true);
                }
            }
        });
    }

    /**
     * Sets the format string used by the DateFormatter used by this time picker.
     *
     * @param formatString
     *            the format string to use
     */
    public void setFormatter(String formatString) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(formatString);
        JFormattedTextField tf = ((JSpinner.DefaultEditor) getEditor()).getTextField();
        DefaultFormatterFactory factory = (DefaultFormatterFactory) tf.getFormatterFactory();
        formatter = (DateFormatter) factory.getDefaultFormatter();
        formatter.setFormat(dateFormat);
        fireStateChanged();
    }

    public void setSaveEnabled(boolean saveEnabled) {
        this.saveEnabled = saveEnabled;
    }

    public void setDate(String date) {

        try {
            this.setValue(formatter.stringToValue(date));

            if (saveEnabled) {
                parent.setSaveEnabled(false);
            }
        } catch (ParseException e) {
        }
    }

    public String getDate() {
        return ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().getText();
    }
}
