// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package com.mirth.connect.client.ui.components.tag;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdesktop.swingx.prompt.PromptSupport;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.IconButton;
import com.mirth.connect.client.ui.components.MirthFieldConstraints;
import com.mirth.connect.model.ChannelTag;
import com.mirth.connect.util.ColorUtil;

/**
 * A single-line field holding a row of tag chips plus a text editor for the tag being typed.
 * Completions are supplied through {@link #update} and offered by an autocomplete popup installed
 * on the editor.
 */
public class MirthTagField extends JPanel {
    private static final String TAG_TYPE = "tag";
    private static final String NAME_TYPE = "name";
    private static final char DELIM = ':';

    /** Matches the token limit the previous bootstrap-tokenfield implementation enforced. */
    private static final int MAX_TAGS = 10;
    private static final int MAX_TAG_LENGTH = 24;
    private static final Color DEFAULT_BACKGROUND = new Color(0xEE, 0xEE, 0xEE);
    private static final Font FONT = new Font("Tahoma", Font.PLAIN, 11);
    private static final int SCROLL_INCREMENT = 20;

    /**
     * Lays the chips out in a single row that fills the viewport while they fit and scrolls
     * horizontally once they don't.
     */
    private static class ChipRow extends JPanel implements Scrollable {
        ChipRow() {
            super(new MigLayout("insets 0 2 0 2, gap 3, novisualpadding, nogrid, filly"));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return SCROLL_INCREMENT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() != null && getPreferredSize().width <= getParent().getWidth();
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return true;
        }
    }

    /** A tag currently shown in the field. */
    private static class Token {
        private final String type;
        private final String name;
        private final Color background;
        private final Color foreground;

        Token(String type, String name, Color background, Color foreground) {
            this.type = type;
            this.name = name;
            this.background = background;
            this.foreground = foreground;
        }
    }

    private Frame parent;
    private Logger logger = LogManager.getLogger(this.getClass());

    private final boolean channelContext;
    private final boolean restorePreferences;

    private JScrollPane scrollPane;
    private ChipRow chipRow;
    private JTextField editor;
    private IconButton clearButton;
    private MouseAdapter focusEditorAdapter;

    private AutoCompletionProvider provider;
    private AutoCompletionDelegate completionDelegate;

    private final List<Token> tokens = new ArrayList<Token>();
    /** Keyed by type and name, since a channel and a tag may go by the same name. */
    private final Map<String, FilterCompletion> completions = new TreeMap<String, FilterCompletion>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Color> tagColorMap = new HashMap<String, Color>();
    private final List<SearchFilterListener> updateSearchListeners = new ArrayList<SearchFilterListener>();

    private List<Token> cachedUserPreferenceTags = new ArrayList<Token>();

    public MirthTagField(String preferencePrefix, boolean channelContext, Set<FilterCompletion> tags) {
        parent = PlatformUI.MIRTH_FRAME;
        this.channelContext = channelContext;
        this.restorePreferences = !channelContext;

        if (StringUtils.isNotBlank(preferencePrefix)) {
            try {
                Properties userPreferences = parent.mirthClient.getUserPreferences(parent.getCurrentUser(parent).getId(), Collections.singleton("initialTags" + preferencePrefix));
                cachedUserPreferenceTags = getUserPreferenceTags(userPreferences.getProperty("initialTags" + preferencePrefix));
            } catch (ClientException e) {
                logger.error("Error restoring tag preferences.", e);
            }
        }

        setBackground(channelContext ? UIConstants.BACKGROUND_COLOR : null);

        initComponents();
        initLayout();

        setCompletions(tags);
        setProviderCompletions(tags);
        setTokens(cachedUserPreferenceTags, false);
    }

    private void initComponents() {
        setToolTipText(channelContext ? "Add or remove tags here. General tag management may be done in the Settings -> Tags tab." : "Enter tags or free text here. Free text will match on channel names, case insensitive.");

        focusEditorAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent evt) {
                editor.requestFocusInWindow();
            }
        };

        initEditor();
        initAutoCompletion();

        chipRow = new ChipRow();
        chipRow.setBackground(UIConstants.BACKGROUND_COLOR);
        chipRow.setToolTipText(getToolTipText());
        chipRow.addMouseListener(focusEditorAdapter);
        // Added once and never removed, so rebuilding the chips can't take focus away from it.
        chipRow.add(editor, "growx, pushx, w 60::");

        scrollPane = new JScrollPane(chipRow, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(110, 110, 110), 1, false));
        scrollPane.setViewportBorder(null);
        scrollPane.getViewport().setBackground(UIConstants.BACKGROUND_COLOR);
        scrollPane.getViewport().addMouseListener(focusEditorAdapter);

        clearButton = new IconButton();
        clearButton.setIcon(UIConstants.ICON_X);
        clearButton.setEnabled(false);
        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                clear();
            }
        });
    }

    private void initEditor() {
        editor = new JTextField();
        editor.setBorder(BorderFactory.createEmptyBorder());
        editor.setFont(FONT);
        editor.setToolTipText(getToolTipText());
        /*
         * Restricts input to the character class and length the previous implementation validated
         * after the fact, so invalid text can never be typed or pasted in the first place.
         */
        editor.setDocument(new MirthFieldConstraints(channelContext ? MAX_TAG_LENGTH : 0, false, true, true));
        PromptSupport.setPrompt(channelContext ? " Enter channel tag" : " Enter channel tag or name", editor);
        PromptSupport.setForeground(Color.GRAY, editor);

        /*
         * Enter and Down go through the input map rather than a key listener so that the
         * autocomplete popup can shadow them while it is showing and restore them when it hides.
         */
        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "Tag.commit");
        editor.getActionMap().put("Tag.commit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                commitEditorText();
            }
        });
        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "Tag.showCompletions");
        editor.getActionMap().put("Tag.showCompletions", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                completionDelegate.doCompletion();
            }
        });

        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent evt) {
                if (evt.getKeyChar() == ',') {
                    evt.consume();
                    commitEditorText();
                }
            }

            @Override
            public void keyPressed(KeyEvent evt) {
                if (evt.getKeyCode() == KeyEvent.VK_BACK_SPACE && StringUtils.isEmpty(editor.getText())) {
                    evt.consume();
                    closePopupWindow();

                    if (!tokens.isEmpty()) {
                        removeToken(tokens.size() - 1);
                    }
                }
            }
        });

        editor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent evt) {
                closePopupWindow();
            }
        });

        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent evt) {
                updatePopup();
            }

            @Override
            public void removeUpdate(DocumentEvent evt) {
                updatePopup();
            }

            @Override
            public void changedUpdate(DocumentEvent evt) {}
        });
    }

    private void initAutoCompletion() {
        provider = new AutoCompletionProvider();
        provider.setListCellRenderer(new TagCompletionRenderer());

        completionDelegate = new AutoCompletionDelegate(provider, this::acceptCompletion);
        completionDelegate.setAutoCompleteSingleChoices(false);
        completionDelegate.install(editor);
    }

    private void initLayout() {
        setLayout(new MigLayout("novisualpadding, hidemode 3, insets 0, fill"));

        add(scrollPane, "h 24!, gaptop 1, growx, push");

        if (!channelContext) {
            add(clearButton, "h 22!, w 22!, aligny top, gaptop 2");
        }
    }

    /** Rebuilds the chips from {@link #tokens}, keeping the editor last. */
    private void rebuild() {
        for (Component component : chipRow.getComponents()) {
            if (component instanceof TagChip) {
                chipRow.remove(component);
            }
        }

        for (int i = 0; i < tokens.size(); i++) {
            chipRow.add(createChip(tokens.get(i), i), "", i);
        }

        clearButton.setEnabled(isEnabled() && !tokens.isEmpty());

        chipRow.revalidate();
        chipRow.repaint();
    }

    private TagChip createChip(Token token, final int index) {
        TagChip chip = new TagChip(token.name, token.background, token.foreground, new Runnable() {
            @Override
            public void run() {
                removeToken(index);
            }
        });
        chip.setToolTipText(getToolTipText());
        chip.setEnabled(isEnabled());
        chip.addMouseListener(focusEditorAdapter);
        return chip;
    }

    /** Resolves a tag's colors from the current completion set, allocating one if it is new. */
    private Token resolve(String type, String name) {
        /*
         * In the channel editor everything in the field is a tag, including free text for a tag
         * that doesn't exist yet. Elsewhere free text filters on channel name and stays neutrally
         * colored, so it deliberately matches nothing.
         */
        FilterCompletion completion = completions.get(completionKey(channelContext && NAME_TYPE.equals(type) ? TAG_TYPE : type, name));

        if (completion != null) {
            return new Token(type, name, completion.getBackgroundColor(), completion.getForegroundColor());
        }

        if (channelContext) {
            Color background = tagColorMap.get(name);
            if (background == null) {
                background = ColorUtil.getNewColor();
                tagColorMap.put(name, background);
            }
            return new Token(type, name, background, ColorUtil.getForegroundColor(background));
        }

        return new Token(type, name, DEFAULT_BACKGROUND, Color.BLACK);
    }

    private void setTokens(List<Token> newTokens, boolean fireEvent) {
        List<Token> source = new ArrayList<Token>(newTokens);

        tokens.clear();
        for (Token token : source) {
            tokens.add(resolve(token.type, token.name));
        }

        rebuild();

        if (fireEvent) {
            updateSearchPerformed();
        }
    }

    private void addToken(String type, String name) {
        editor.setText("");

        if (StringUtils.isBlank(name) || tokens.size() >= MAX_TAGS || isDuplicate(name)) {
            return;
        }

        tokens.add(resolve(type, name));
        rebuild();
        scrollEditorToVisible();

        PlatformUI.MIRTH_FRAME.setSaveEnabled(PlatformUI.MIRTH_FRAME.currentContentPage == PlatformUI.MIRTH_FRAME.channelEditPanel);
        updateSearchPerformed();
    }

    private void removeToken(int index) {
        tokens.remove(index);
        rebuild();
        deleteTagActionPerformed();
    }

    private boolean isDuplicate(String name) {
        for (Token token : tokens) {
            if (channelContext ? token.name.equalsIgnoreCase(name) : token.name.equals(name)) {
                return true;
            }
        }

        return false;
    }

    private void commitEditorText() {
        // Free text always filters on channel name; the popup supplies the type otherwise.
        addToken(NAME_TYPE, StringUtils.trim(editor.getText()));
    }

    private void acceptCompletion(TagCompletion completion) {
        addToken(completion.getType(), completion.getName());
    }

    private void updatePopup() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (StringUtils.isNotBlank(editor.getText())) {
                    completionDelegate.doCompletion();
                } else {
                    completionDelegate.hidePopupWindow();
                }
            }
        });
    }

    private void scrollEditorToVisible() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                chipRow.scrollRectToVisible(editor.getBounds());
            }
        });
    }

    private void updateSearchPerformed() {
        String filterString = getTags();
        syncUserTags();

        for (SearchFilterListener listener : updateSearchListeners) {
            if (listener != null) {
                listener.doSearch(filterString);
            }
        }
    }

    private void deleteTagActionPerformed() {
        String filterString = getTags();
        syncUserTags();

        for (SearchFilterListener listener : updateSearchListeners) {
            if (listener != null) {
                listener.doDelete(filterString);
            }
        }
    }

    /** Keeps the tags to restore on the next {@link #update} in step with what is displayed. */
    private void syncUserTags() {
        if (restorePreferences) {
            cachedUserPreferenceTags = new ArrayList<Token>(tokens);
        }
    }

    /** The tags the chips take their colors from. */
    private void setCompletions(Set<FilterCompletion> tags) {
        completions.clear();

        for (FilterCompletion tag : tags) {
            completions.put(completionKey(tag.getType(), tag.getName()), tag);
        }
    }

    private static String completionKey(String type, String name) {
        return type + DELIM + name;
    }

    /** The tags the autocomplete popup offers. */
    private void setProviderCompletions(Set<FilterCompletion> tags) {
        provider.clear();

        for (FilterCompletion tag : tags) {
            provider.addCompletion(new TagCompletion(provider, tag.getName(), tag.getType(), tag.getBackgroundColor(), tag.getIcon()));
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        if (editor == null) {
            return;
        }

        editor.setEnabled(enabled);
        editor.setEditable(enabled);
        scrollPane.getViewport().setBackground(enabled ? UIConstants.BACKGROUND_COLOR : UIManager.getColor("control"));
        chipRow.setBackground(scrollPane.getViewport().getBackground());

        for (Component component : chipRow.getComponents()) {
            component.setEnabled(enabled);
        }

        clearButton.setEnabled(enabled && !tokens.isEmpty());

        if (!enabled) {
            closePopupWindow();
        }
    }

    public void addUpdateSearchListener(SearchFilterListener searchListener) {
        if (!updateSearchListeners.contains(searchListener)) {
            updateSearchListeners.add(searchListener);
        }
    }

    public void createTagOnFocusLost() {
        commitEditorText();
    }

    public void setFocus(boolean focus) {
        if (focus) {
            editor.requestFocusInWindow();
        } else {
            closePopupWindow();
        }
    }

    public void closePopupWindow() {
        completionDelegate.hideChildWindows();
        completionDelegate.hidePopupWindow();
    }

    public void setChannelTags(List<ChannelTag> tags) {
        List<Token> channelTokens = new ArrayList<Token>();

        for (ChannelTag tag : tags) {
            channelTokens.add(new Token(TAG_TYPE, tag.getName(), null, null));
        }

        setTokens(channelTokens, true);
    }

    public void clear() {
        editor.setText("");
        tagColorMap.clear();
        setTokens(new ArrayList<Token>(), true);
    }

    public String getTags() {
        StringBuilder builder = new StringBuilder();

        for (Token token : tokens) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(token.type).append(DELIM).append(token.name);
        }

        return builder.toString();
    }

    public Map<String, Color> getTagColors() {
        return tagColorMap;
    }

    public boolean isFilterEnabled() {
        return !tokens.isEmpty();
    }

    public void update(Set<FilterCompletion> tags, boolean channelContext, boolean updateUserTags, boolean updateController) {
        setProviderCompletions(tags);

        // With nothing to offer, the tags already displayed keep the colors they were given.
        if (CollectionUtils.isNotEmpty(tags)) {
            setCompletions(tags);

            /*
             * Re-resolve the displayed tags against the new completions. When updateUserTags is set
             * the cached selection is reinstated instead, even if it is empty - ChannelSetup relies
             * on that to drop the previously edited channel's tags.
             */
            setTokens(updateUserTags ? cachedUserPreferenceTags : tokens, updateUserTags && updateController);
        }
    }

    public void setUserPreferenceTags() {
        setTokens(cachedUserPreferenceTags, true);
    }

    private List<Token> getUserPreferenceTags(String userTags) {
        List<Token> userPreferenceTags = new ArrayList<Token>();

        try {
            if (restorePreferences && StringUtils.isNotBlank(userTags)) {
                for (String tag : userTags.split(",")) {
                    String[] tagPair = tag.split(":");

                    if (ArrayUtils.isNotEmpty(tagPair) && tagPair.length == 2) {
                        userPreferenceTags.add(new Token(tagPair[0].trim(), tagPair[1].trim(), null, null));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error restoring tag preferences.", e);
        }

        return userPreferenceTags;
    }
}
