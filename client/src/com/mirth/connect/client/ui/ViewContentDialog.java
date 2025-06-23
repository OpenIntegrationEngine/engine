package com.mirth.connect.client.ui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Enhanced dialog for viewing content with improved formatting,
 * search capabilities, and user experience features.
 * This is a read-only view - content cannot be edited.
 */
public class ViewContentDialog extends MirthDialog {

    // Constants
    private static final int DEFAULT_WIDTH = 900;
    private static final int DEFAULT_HEIGHT = 700;
    private static final String DIALOG_TITLE = "View Content";

    // Components
    private final Frame parent;
    private RSyntaxTextArea messageContent;
    private JLabel statusLabel;
    private SearchDialog searchDialog;
    private String originalContent;
    private ContentFormatter formatter;

    // State
    private boolean isFormatted = false;
    private String currentFormat = "NONE";

    /**
     * Creates a new ViewContentDialog with the specified parent and content.
     * 
     * @param parent the parent frame
     * @param text   the content to display
     */
    public ViewContentDialog(Frame parent, String text) {
        super(parent);
        this.parent = parent;
        this.originalContent = text;
        this.formatter = new ContentFormatter();

        initComponents();
        setupKeyBindings();

        messageContent.setText(text.replaceAll("\\t", "    ")); // Convert tabs to spaces
        messageContent.setCaretPosition(0);
        updateStatus();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setModal(true);
        centerDialog();
        setVisible(true);
    }

    /**
     * Backward compatibility constructor
     */
    public ViewContentDialog(String text) {
        this(PlatformUI.MIRTH_FRAME, text);
    }

    private void initComponents() {
        setTitle(DIALOG_TITLE);
        setLayout(new BorderLayout());

        // Create main content area
        createContentArea();

        // Create button panel (now includes all buttons)
        createButtonPanel();

        // Setup the layout
        add(createScrollPane(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
    }

    private void createContentArea() {
        messageContent = new RSyntaxTextArea();
        setupContextMenu();
        messageContent.setEditable(false); // Read-only view
        messageContent.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        messageContent.setCodeFoldingEnabled(true);
        messageContent.setTabSize(4);
        messageContent.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        // Add caret listener for status updates
        messageContent.addCaretListener(e -> updateStatus());
    }

    private JScrollPane createScrollPane() {
        RTextScrollPane scrollPane = new RTextScrollPane(messageContent);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setFoldIndicatorEnabled(true);
        return scrollPane;
    }

    private void setupContextMenu() {
        JPopupMenu popupMenu = messageContent.getPopupMenu(); // Get the existing RSyntaxTextArea menu

        // Add a separator to keep it clean
        popupMenu.addSeparator();

        // Word Wrap toggle
        JCheckBoxMenuItem wrapItem = new JCheckBoxMenuItem("Word Wrap", messageContent.getLineWrap());
        wrapItem.addActionListener(e -> {
            boolean enabled = wrapItem.isSelected();
            messageContent.setLineWrap(enabled);
            messageContent.setWrapStyleWord(enabled);
        });
        popupMenu.add(wrapItem);

        // Show Line Endings toggle
        JCheckBoxMenuItem eolItem = new JCheckBoxMenuItem("Show Line Endings", messageContent.getEOLMarkersVisible());
        eolItem.addActionListener(e -> messageContent.setEOLMarkersVisible(eolItem.isSelected()));
        popupMenu.add(eolItem);
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new BorderLayout());

        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        buttonPanel.add(statusLabel, BorderLayout.WEST);

        JPanel actionButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        // Platform-aware modifier key for tooltips
        String modifierText = System.getProperty("os.name").toLowerCase().contains("mac") ? "⌘" : "Ctrl";

        JButton formatButton = new JButton("Format");
        formatButton.setToolTipText("Format content (" + modifierText + "+Shift+F)");
        formatButton.addActionListener(e -> formatContent());
        actionButtonsPanel.add(formatButton);

        JButton undoFormatButton = new JButton("Reset");
        undoFormatButton.setToolTipText("Reset to original content (" + modifierText + "+R)");
        undoFormatButton.addActionListener(e -> resetContent());
        actionButtonsPanel.add(undoFormatButton);

        JButton searchButton = new JButton("Search");
        searchButton.setToolTipText("Search content (" + modifierText + "+F)");
        searchButton.addActionListener(e -> showSearchDialog());
        actionButtonsPanel.add(searchButton);

        JButton saveButton = new JButton("Export");
        saveButton.setToolTipText("Save to file (" + modifierText + "+S)");
        saveButton.addActionListener(e -> saveContentToFile());
        actionButtonsPanel.add(saveButton);

        JButton closeButton = new JButton("Close");
        closeButton.setToolTipText("Close dialog (Escape)");
        closeButton.addActionListener(e -> dispose());
        actionButtonsPanel.add(closeButton);

        // Ensure all buttons have the same size
        Dimension buttonSize = new Dimension(90, 30);
        formatButton.setPreferredSize(buttonSize);
        undoFormatButton.setPreferredSize(buttonSize);
        searchButton.setPreferredSize(buttonSize);
        saveButton.setPreferredSize(buttonSize);
        closeButton.setPreferredSize(buttonSize);

        buttonPanel.add(actionButtonsPanel, BorderLayout.EAST);
        return buttonPanel;
    }

    private void setupKeyBindings() {
        // Detect platform for proper modifier keys
        int modifierKey = System.getProperty("os.name").toLowerCase().contains("mac")
                ? InputEvent.META_DOWN_MASK
                : InputEvent.CTRL_DOWN_MASK;

        // Ctrl/Cmd+F for search
        KeyStroke findKey = KeyStroke.getKeyStroke(KeyEvent.VK_F, modifierKey);
        messageContent.getInputMap(JComponent.WHEN_FOCUSED).put(findKey, "openSearch");
        messageContent.getActionMap().put("openSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showSearchDialog();
            }
        });

        // Ctrl/Cmd+S for save
        KeyStroke saveKey = KeyStroke.getKeyStroke(KeyEvent.VK_S, modifierKey);
        messageContent.getInputMap(JComponent.WHEN_FOCUSED).put(saveKey, "saveFile");
        messageContent.getActionMap().put("saveFile", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveContentToFile();
            }
        });

        // Ctrl/Cmd+Shift+F for format
        KeyStroke formatKey = KeyStroke.getKeyStroke(KeyEvent.VK_F, modifierKey | InputEvent.SHIFT_DOWN_MASK);
        messageContent.getInputMap(JComponent.WHEN_FOCUSED).put(formatKey, "formatContent");
        messageContent.getActionMap().put("formatContent", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                formatContent();
            }
        });

        // Ctrl/Cmd+R for reset/reload
        KeyStroke resetKey = KeyStroke.getKeyStroke(KeyEvent.VK_R, modifierKey);
        messageContent.getInputMap(JComponent.WHEN_FOCUSED).put(resetKey, "resetContent");
        messageContent.getActionMap().put("resetContent", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetContent();
            }
        });

        // Ctrl/Cmd+W for close (standard on macOS)
        KeyStroke closeKey = KeyStroke.getKeyStroke(KeyEvent.VK_W, modifierKey);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(closeKey, "closeDialog");
        getRootPane().getActionMap().put("closeDialog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        // Escape to close
        KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escape, "closeDialog");
        getRootPane().getActionMap().put("closeDialog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }

    private void showSearchDialog() {
        if (searchDialog == null) {
            searchDialog = new SearchDialog(this, messageContent);
        }
        searchDialog.setVisible(true);
    }

    private void formatContent() {
        String text = messageContent.getText();

        if (text.trim().isEmpty()) {
            showWarning("No content to format");
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        try {
            ContentFormatter.FormatResult result = formatter.format(text);

            if (result.isSuccessful()) {
                messageContent.setText(result.getFormattedContent());
                messageContent.setSyntaxEditingStyle(result.getSyntaxStyle());
                messageContent.setCaretPosition(0);
                isFormatted = true;
                currentFormat = result.getFormatType();
            } else {
                showWarning("Could not format content. Format not recognized or content is invalid.");
            }
        } finally {
            setCursor(Cursor.getDefaultCursor());
            updateStatus();
        }
    }

    private void resetContent() {
        messageContent.setText(originalContent);
        messageContent.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        messageContent.setCaretPosition(0);
        isFormatted = false;
        currentFormat = "NONE";
        updateStatus();
    }

    private void saveContentToFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Content");

        // Add file filters
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("JSON files (*.json)", "json"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("XML files (*.xml)", "xml"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("All files (*.*)", "*"));

        // Set default directory to user home
        chooser.setCurrentDirectory(new File(System.getProperty("user.home")));

        // Suggest filename based on format
        if (isFormatted) {
            String extension = currentFormat.equalsIgnoreCase("JSON") ? ".json"
                    : currentFormat.equalsIgnoreCase("XML") ? ".xml" : ".txt";
            chooser.setSelectedFile(new File("content" + extension));
        }

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();

            // Check for overwrite
            if (file.exists()) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "File '" + file.getName() + "' already exists.\nDo you want to replace it?",
                        "Confirm Save", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            // Save the file
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            try {
                try (OutputStreamWriter writer = new OutputStreamWriter(
                        new FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
                    writer.write(messageContent.getText());
                }

                showInfo("File saved successfully to:\n" + file.getAbsolutePath());
            } catch (IOException ex) {
                showError("Error saving file:\n" + ex.getMessage());
            } finally {
                setCursor(Cursor.getDefaultCursor());
            }
        }
    }

    private void updateStatus() {
        SwingUtilities.invokeLater(() -> {
            int caretPos = messageContent.getCaretPosition();
            try {
                int line = messageContent.getLineOfOffset(caretPos) + 1;
                int col = caretPos - messageContent.getLineStartOffset(line - 1) + 1;
                int totalLines = messageContent.getLineCount();
                int length = messageContent.getText().length();

                String status = String.format("Line %d, Col %d | %d lines | %d chars | Format: %s",
                        line, col, totalLines, length, currentFormat);

                if (isFormatted) {
                    status += " (Formatted)";
                }

                statusLabel.setText(status);
            } catch (Exception e) {
                statusLabel.setText("Ready");
            }
        });
    }

    private void centerDialog() {
        setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        pack();
        if (parent != null) {
            setLocationRelativeTo(parent);
        } else {
            setLocationRelativeTo(null);
        }
    }

    // Utility methods for messaging
    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void dispose() {
        if (searchDialog != null) {
            searchDialog.dispose();
            searchDialog = null;
        }
        messageContent.setText(""); // Free memory
        originalContent = null;
        super.dispose();
    }
}

/**
 * Separate class for handling content formatting
 */
class ContentFormatter {
    private final ObjectMapper jsonMapper;

    public ContentFormatter() {
        this.jsonMapper = new ObjectMapper();
    }

    public FormatResult format(String content) {
        if (!isLikelyText(content)) {
            return FormatResult.failure("Content appears to be binary");
        }

        // Try JSON first
        try {
            Object json = jsonMapper.readTree(content);
            ObjectWriter writer = jsonMapper.writerWithDefaultPrettyPrinter();
            String formatted = writer.writeValueAsString(json);
            return FormatResult.success(formatted, SyntaxConstants.SYNTAX_STYLE_JSON, "JSON");
        } catch (Exception e) {
            // Try XML
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new InputSource(new StringReader(content)));

                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

                StringWriter writer = new StringWriter();
                transformer.transform(new DOMSource(doc), new StreamResult(writer));

                return FormatResult.success(writer.toString(), SyntaxConstants.SYNTAX_STYLE_XML, "XML");
            } catch (Exception e2) {
                return FormatResult.failure("Unsupported format");
            }
        }
    }

    private boolean isLikelyText(String text) {
        if (text == null || text.isEmpty())
            return false;

        int controlChars = 0;
        int totalChars = Math.min(text.length(), 1000); // Sample first 1000 chars

        for (int i = 0; i < totalChars; i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                controlChars++;
            }
        }

        // If more than 5% control characters, probably binary
        return (controlChars / (double) totalChars) < 0.05;
    }

    public static class FormatResult {
        private final boolean successful;
        private final String formattedContent;
        private final String syntaxStyle;
        private final String formatType;
        private final String errorMessage;

        private FormatResult(boolean successful, String formattedContent,
                String syntaxStyle, String formatType, String errorMessage) {
            this.successful = successful;
            this.formattedContent = formattedContent;
            this.syntaxStyle = syntaxStyle;
            this.formatType = formatType;
            this.errorMessage = errorMessage;
        }

        public static FormatResult success(String content, String syntax, String type) {
            return new FormatResult(true, content, syntax, type, null);
        }

        public static FormatResult failure(String error) {
            return new FormatResult(false, null, null, null, error);
        }

        // Getters
        public boolean isSuccessful() {
            return successful;
        }

        public String getFormattedContent() {
            return formattedContent;
        }

        public String getSyntaxStyle() {
            return syntaxStyle;
        }

        public String getFormatType() {
            return formatType;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}

/**
 * Search dialog for read-only content viewing - matches Mirth standard layout
 */
class SearchDialog extends JDialog {
    private final RSyntaxTextArea textArea;
    private JTextField searchField;
    private JCheckBox matchCaseBox;
    private JCheckBox regexBox;
    private JCheckBox wholeWordBox;
    private JRadioButton forwardRadio;
    private JRadioButton backwardRadio;
    private JButton findButton;
    private JLabel resultLabel;
    private SearchContext currentContext;

    public SearchDialog(JDialog parent, RSyntaxTextArea textArea) {
        super(parent, "Find", false); // Non-modal
        this.textArea = textArea;
        initComponents();
        setupKeyBindings();
        pack();
        setResizable(false);
        positionDialog(parent);
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setTitle("Find");

        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Find text field
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(10, 10, 5, 5);
        JLabel searchLabel = new JLabel("Find text:");
        mainPanel.add(searchLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(10, 5, 5, 10);
        searchField = new JTextField(25);
        searchField.addActionListener(e -> findNext());
        mainPanel.add(searchField, gbc);

        // Direction panel
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 5, 10);
        JPanel directionPanel = new JPanel();
        directionPanel.setBorder(BorderFactory.createTitledBorder("Direction"));
        directionPanel.setLayout(new FlowLayout(FlowLayout.LEFT));

        ButtonGroup directionGroup = new ButtonGroup();
        forwardRadio = new JRadioButton("Forward", true);
        backwardRadio = new JRadioButton("Backward");
        directionGroup.add(forwardRadio);
        directionGroup.add(backwardRadio);
        directionPanel.add(forwardRadio);
        directionPanel.add(backwardRadio);
        mainPanel.add(directionPanel, gbc);

        // Options panel
        gbc.gridy = 2;
        gbc.insets = new Insets(5, 10, 10, 10);
        JPanel optionsPanel = new JPanel();
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Options"));
        optionsPanel.setLayout(new GridLayout(1, 3, 10, 5));

        regexBox = new JCheckBox("Regular Expression");
        matchCaseBox = new JCheckBox("Match Case");
        wholeWordBox = new JCheckBox("Whole Word");

        // Add listener to disable other options when regex is selected
        regexBox.addActionListener(e -> {
            boolean regexSelected = regexBox.isSelected();
            matchCaseBox.setEnabled(!regexSelected);
            wholeWordBox.setEnabled(!regexSelected);
            if (regexSelected) {
                matchCaseBox.setSelected(false);
                wholeWordBox.setSelected(false);
            }
        });

        optionsPanel.add(regexBox);
        optionsPanel.add(matchCaseBox);
        optionsPanel.add(wholeWordBox);
        mainPanel.add(optionsPanel, gbc);

        // Buttons panel with result label
        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 10));

        findButton = new JButton("Find");
        findButton.setPreferredSize(new Dimension(80, 25));
        findButton.addActionListener(e -> {
            if (backwardRadio.isSelected()) {
                findPrevious();
            } else {
                findNext();
            }
        });
        buttonPanel.add(findButton);

        JButton closeButton = new JButton("Close");
        closeButton.setPreferredSize(new Dimension(80, 25));
        closeButton.addActionListener(e -> setVisible(false));
        buttonPanel.add(closeButton);

        // Result label (small status at bottom)
        resultLabel = new JLabel(" ");
        resultLabel.setFont(resultLabel.getFont().deriveFont(Font.ITALIC, 11f));
        resultLabel.setHorizontalAlignment(SwingConstants.CENTER);
        resultLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        bottomPanel.add(resultLabel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setupKeyBindings() {
        // Enter to find next
        KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        searchField.getInputMap().put(enter, "findNext");
        searchField.getActionMap().put("findNext", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                findNext();
            }
        });

        // Escape to close
        KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escape, "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });
    }

    private void findNext() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) {
            resultLabel.setText("Enter text to find");
            return;
        }

        updateSearchContext();
        currentContext.setSearchForward(true);
        SearchResult result = SearchEngine.find(textArea, currentContext);
        handleSearchResult(result);
    }

    private void findPrevious() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) {
            resultLabel.setText("Enter text to find");
            return;
        }

        updateSearchContext();
        currentContext.setSearchForward(false);
        SearchResult result = SearchEngine.find(textArea, currentContext);
        handleSearchResult(result);
    }

    private void updateSearchContext() {
        currentContext = new SearchContext(searchField.getText());
        currentContext.setMatchCase(matchCaseBox.isSelected());
        currentContext.setRegularExpression(regexBox.isSelected());
        currentContext.setWholeWord(wholeWordBox.isSelected());
    }

    private void handleSearchResult(SearchResult result) {
        if (result.wasFound()) {
            resultLabel.setText("");
        } else {
            resultLabel.setText("\"" + searchField.getText() + "\" not found");
        }
    }

    private void positionDialog(JDialog parent) {
        if (parent != null) {
            Point parentLocation = parent.getLocationOnScreen();
            setLocation(parentLocation.x + parent.getWidth() - getWidth() - 20,
                    parentLocation.y + 50);
        } else {
            setLocationRelativeTo(null);
        }
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            searchField.requestFocus();
            searchField.selectAll();
        }
        super.setVisible(visible);
    }
}