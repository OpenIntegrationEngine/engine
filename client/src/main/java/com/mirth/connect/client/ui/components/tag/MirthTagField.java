// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package com.mirth.connect.client.ui.components.tag;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
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
 * <p>
 * The editor sits between two chips, or before or after all of them. Typing puts it in edit mode,
 * shown by an outline, where the arrow, Home, End, Backspace and Delete keys edit its text. Outside
 * edit mode, those keys move the editor between the chips and delete the chips next to it instead.
 * Committing the text or pressing Escape leaves edit mode, as does losing focus with no text.
 */
public class MirthTagField extends JPanel {
    private static final String TAG_TYPE = "tag";
    private static final String NAME_TYPE = "name";
    private static final char DELIM = ':';

    /** Matches the token limit the previous bootstrap-tokenfield implementation enforced. */
    private static final int MAX_TAGS = 10;
    private static final int MAX_TAG_LENGTH = 24;
    private static final Color DEFAULT_BACKGROUND = new Color(0xEE, 0xEE, 0xEE);
    private static final int SCROLL_INCREMENT = 20;
    /** Sized to its text, so the editor grows as the tag is typed. */
    private static final String EDITOR_CONSTRAINTS = "";
    /** While it shows its prompt, the editor takes up the rest of the row. */
    private static final String PROMPT_EDITOR_CONSTRAINTS = "growx, pushx, w 60::";
    /** The scroll amount the toolkits report for one step of the wheel. */
    private static final int WHEEL_SCROLL_AMOUNT = 3;
    /**
     * The X11 toolkit reports horizontal scrolling (X buttons 6 and 7) as presses of mouse buttons 4
     * and 5 instead of as wheel events. On other toolkits those buttons are back and forward.
     */
    private static final boolean X11_HORIZONTAL_SCROLL_BUTTONS = "sun.awt.X11.XToolkit".equals(Toolkit.getDefaultToolkit().getClass().getName());
    /** Room after the text for the caret. */
    private static final int CARET_ROOM = 2;

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

    /** The edit mode outline: a thin rounded rectangle with the same corners as the chips. */
    private static class OutlineBorder extends AbstractBorder {
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            // Looked up on each paint, so the outline follows the look and feel.
            Color color = UIManager.getColor("Button.focus");
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color != null ? color : c.getForeground());
            g2.drawRoundRect(x, y, width - 1, height - 1, TagChip.ARC, TagChip.ARC);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(1, 1, 1, 1);
            return insets;
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
    private Border idleBorder;
    private Border editingBorder;
    private IconButton clearButton;
    private MouseAdapter focusEditorAdapter;
    /** Installed while the field is displayable, on X11 only. */
    private final AWTEventListener horizontalScrollButtonListener = new AWTEventListener() {
        @Override
        public void eventDispatched(AWTEvent event) {
            MouseEvent evt = (MouseEvent) event;
            int button = evt.getButton();

            if (evt.getID() == MouseEvent.MOUSE_PRESSED && (button == 4 || button == 5) && SwingUtilities.isDescendingFrom(evt.getComponent(), scrollPane)) {
                // Only marks the press as handled; Swing's mouse listeners still receive it.
                evt.consume();
                scrollChipsBy((button == 4 ? -1 : 1) * WHEEL_SCROLL_AMOUNT);
            }
        }
    };

    private AutoCompletionProvider provider;
    private AutoCompletionDelegate completionDelegate;

    private final List<Token> tokens = new ArrayList<Token>();
    /** The editor's position among the chips, from 0 (before the first) to tokens.size(). */
    private int editorIndex;
    /**
     * Set by typing and cleared by committing, Escape, {@link #clear} or losing focus with no text,
     * but not by backspacing the text away, so holding Backspace can't go on to delete chips.
     */
    private boolean editing;
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
        editorIndex = cachedUserPreferenceTags.size();
        setTokens(cachedUserPreferenceTags, false);
    }

    private void initComponents() {
        setToolTipText(channelContext ? "Add or remove tags here. General tag management may be done in the Settings -> Tags tab." : "Enter tags or free text here. Free text will match on channel names, case insensitive.");

        focusEditorAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent evt) {
                if (!SwingUtilities.isLeftMouseButton(evt)) {
                    return;
                }

                // A click between chips, or past the last one, puts the editor in the nearest gap.
                if (!editing && isEnabled() && !(evt.getComponent() instanceof TagChip)) {
                    moveEditor(editorIndexAt(SwingUtilities.convertPoint(evt.getComponent(), evt.getPoint(), chipRow).x));
                }
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
        chipRow.add(editor, PROMPT_EDITOR_CONSTRAINTS);

        scrollPane = new JScrollPane(chipRow, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(110, 110, 110), 1, false));
        scrollPane.setViewportBorder(null);
        scrollPane.getViewport().setBackground(UIConstants.BACKGROUND_COLOR);
        scrollPane.getViewport().addMouseListener(focusEditorAdapter);
        scrollPane.setWheelScrollingEnabled(false);
        scrollPane.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent evt) {
                scrollChips(evt);
            }
        });

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
        editor = new JTextField() {
            /*
             * PromptSupport reports the prompt's width whenever the text is empty, even while the
             * prompt is hidden, so outside the prompt case the editor measures its own text.
             */
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();

                if (!isShowingPrompt()) {
                    Insets insets = getInsets();
                    size.width = insets.left + getFontMetrics(getFont()).stringWidth(getText()) + CARET_ROOM + insets.right;
                }

                return size;
            }
        };
        idleBorder = BorderFactory.createEmptyBorder(1, 3, 1, 3);
        // The same insets as the idle border, so the outline doesn't shift the chips after it.
        editingBorder = BorderFactory.createCompoundBorder(new OutlineBorder(), BorderFactory.createEmptyBorder(0, 2, 0, 2));
        editor.setBorder(idleBorder);
        editor.setFont(TagChip.FONT);
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

        bindChipKey(KeyEvent.VK_LEFT, "Tag.left", () -> moveEditor(editorIndex - 1));
        bindChipKey(KeyEvent.VK_KP_LEFT, "Tag.keypadLeft", () -> moveEditor(editorIndex - 1));
        bindChipKey(KeyEvent.VK_RIGHT, "Tag.right", () -> moveEditor(editorIndex + 1));
        bindChipKey(KeyEvent.VK_KP_RIGHT, "Tag.keypadRight", () -> moveEditor(editorIndex + 1));
        bindChipKey(KeyEvent.VK_HOME, "Tag.first", () -> moveEditor(0));
        bindChipKey(KeyEvent.VK_END, "Tag.last", () -> moveEditor(tokens.size()));
        bindChipKey(KeyEvent.VK_BACK_SPACE, "Tag.deletePrevious", () -> {
            if (editorIndex > 0) {
                removeToken(editorIndex - 1, true);
            }
        });
        bindChipKey(KeyEvent.VK_DELETE, "Tag.deleteNext", () -> {
            if (editorIndex < tokens.size()) {
                removeToken(editorIndex, true);
            }
        });

        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "Tag.cancel");
        editor.getActionMap().put("Tag.cancel", new AbstractAction() {
            // Disabled outside edit mode, so that Escape still reaches the enclosing window.
            @Override
            public boolean isEnabled() {
                return editing;
            }

            @Override
            public void actionPerformed(ActionEvent evt) {
                editor.setText("");
                setEditing(false);
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
        });

        editor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent evt) {
                updateEditorConstraints();
            }

            @Override
            public void focusLost(FocusEvent evt) {
                closePopupWindow();

                if (!evt.isTemporary()) {
                    // With its text gone there is nothing left to edit.
                    if (editing && editor.getText().isEmpty()) {
                        setEditing(false);
                    }

                    // Back at the end, the prompt shows again. Uncommitted text stays where it is.
                    if (!editing) {
                        moveEditor(tokens.size());
                    }
                }

                updateEditorConstraints();
            }
        });

        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent evt) {
                setEditing(true);
                editorTextChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent evt) {
                editorTextChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent evt) {}
        });
    }

    /**
     * Binds a key that runs chipAction outside edit mode and keeps its usual text editing action in
     * edit mode. The autocomplete popup shadows some of these keys while it is showing.
     */
    private void bindChipKey(int keyCode, String name, final Runnable chipAction) {
        KeyStroke keyStroke = KeyStroke.getKeyStroke(keyCode, 0);
        final Object textActionKey = editor.getInputMap().get(keyStroke);

        editor.getInputMap().put(keyStroke, name);
        editor.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                if (!editing) {
                    chipAction.run();
                    return;
                }

                Action textAction = textActionKey != null ? editor.getActionMap().get(textActionKey) : null;
                if (textAction != null) {
                    textAction.actionPerformed(evt);
                }
            }
        });
    }

    private void setEditing(boolean editing) {
        if (this.editing != editing) {
            this.editing = editing;
            editor.setBorder(editing ? editingBorder : idleBorder);
            updateEditorConstraints();
        }
    }

    /** The prompt shows only in an empty, unfocused editor, which is kept at the end. */
    private boolean isShowingPrompt() {
        // The editor field is still null while the text field's own constructor runs.
        return !editing && editorIndex == tokens.size() && (editor == null || !editor.isFocusOwner());
    }

    /** Only the editor showing its prompt fills the row, so that the prompt has room. */
    private void updateEditorConstraints() {
        ((MigLayout) chipRow.getLayout()).setComponentConstraints(editor, isShowingPrompt() ? PROMPT_EDITOR_CONSTRAINTS : EDITOR_CONSTRAINTS);
        chipRow.revalidate();
    }

    private void editorTextChanged() {
        updatePopup();

        // A JTextField is its own validate root, so the row has to be told its width changed.
        chipRow.revalidate();
        scrollEditorToVisible();
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

    /** Rebuilds the chips from {@link #tokens} around the editor, which stays at editorIndex. */
    private void rebuild() {
        for (Component component : chipRow.getComponents()) {
            if (component instanceof TagChip) {
                chipRow.remove(component);
            }
        }

        for (int i = 0; i < tokens.size(); i++) {
            chipRow.add(createChip(tokens.get(i), i), "", i < editorIndex ? i : i + 1);
        }

        updateEditorConstraints();

        clearButton.setEnabled(isEnabled() && !tokens.isEmpty());

        chipRow.revalidate();
        chipRow.repaint();
    }

    private TagChip createChip(Token token, final int index) {
        TagChip chip = new TagChip(token.name, token.background, token.foreground, new Runnable() {
            @Override
            public void run() {
                // Leave the row where it is, so the next chip slides under the pointer.
                removeToken(index, false);
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

        editorIndex = Math.min(editorIndex, tokens.size());
        rebuild();

        if (fireEvent) {
            updateSearchPerformed();
        }
    }

    private void addToken(String type, String name) {
        editor.setText("");
        setEditing(false);

        if (StringUtils.isBlank(name) || tokens.size() >= MAX_TAGS || isDuplicate(name)) {
            return;
        }

        tokens.add(editorIndex++, resolve(type, name));
        rebuild();
        scrollEditorToVisible();

        PlatformUI.MIRTH_FRAME.setSaveEnabled(PlatformUI.MIRTH_FRAME.currentContentPage == PlatformUI.MIRTH_FRAME.channelEditPanel);
        updateSearchPerformed();
    }

    private void removeToken(int index, boolean scrollToEditor) {
        closePopupWindow();
        tokens.remove(index);
        if (index < editorIndex) {
            editorIndex--;
        }
        rebuild();
        if (scrollToEditor) {
            scrollEditorToVisible();
        }
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

    /** The editor position nearest to x in the row: after every chip whose middle is left of x. */
    private int editorIndexAt(int x) {
        int index = 0;

        for (Component component : chipRow.getComponents()) {
            if (component instanceof TagChip && component.getX() + component.getWidth() / 2 < x) {
                index++;
            }
        }

        return index;
    }

    private void moveEditor(int index) {
        index = Math.max(0, Math.min(index, tokens.size()));

        if (index != editorIndex) {
            closePopupWindow();
            editorIndex = index;
            // Moved within its own container, the editor keeps focus, so the chips can stay put.
            chipRow.setComponentZOrder(editor, editorIndex);
            updateEditorConstraints();
            chipRow.repaint();
        }

        scrollEditorToVisible();
    }

    /**
     * With no scrollbar to show, the wheel (or a sideways trackpad swipe, which arrives with Shift
     * held) scrolls the chips horizontally. While they fit, the event goes on to the components
     * around the field, so an enclosing scroll pane still scrolls.
     */
    private void scrollChips(MouseWheelEvent evt) {
        if (scrollChipsBy(evt.getPreciseWheelRotation() * evt.getScrollAmount())) {
            evt.consume();
        } else {
            dispatchEvent(SwingUtilities.convertMouseEvent(scrollPane, evt, this));
        }
    }

    /** Scrolls the chips by a number of scroll units, returning false when they all fit. */
    private boolean scrollChipsBy(double units) {
        JViewport viewport = scrollPane.getViewport();
        int overflow = chipRow.getWidth() - viewport.getWidth();

        if (overflow <= 0) {
            return false;
        }

        Point position = viewport.getViewPosition();
        position.x = (int) Math.max(0, Math.min(overflow, Math.round(position.x + units * SCROLL_INCREMENT)));
        viewport.setViewPosition(position);
        return true;
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
    public void addNotify() {
        super.addNotify();

        if (X11_HORIZONTAL_SCROLL_BUTTONS) {
            Toolkit.getDefaultToolkit().addAWTEventListener(horizontalScrollButtonListener, AWTEvent.MOUSE_EVENT_MASK);
        }
    }

    @Override
    public void removeNotify() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(horizontalScrollButtonListener);
        super.removeNotify();
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

        // Committed from outside the field, as by Save, the editor goes back to the end and its prompt.
        if (!editor.isFocusOwner()) {
            moveEditor(tokens.size());
        }
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

        // Text left uncommitted in the previous channel doesn't belong to this one.
        editor.setText("");
        setEditing(false);
        editorIndex = channelTokens.size();
        setTokens(channelTokens, true);
    }

    public void clear() {
        editor.setText("");
        setEditing(false);
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
        editorIndex = cachedUserPreferenceTags.size();
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
