package pl.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;

/**
 * A flat, light look for the whole app, built on Metal with a handful of custom UI delegates
 * (buttons, combo boxes, spinners, tabs, scroll bars, progress bars). Install once before any UI is created.
 */
public final class Look {
    private Look() { }

    /** Client property on a JButton: "primary" (filled purple) or "onDark" (for the purple header). */
    public static final String VARIANT = "pl.variant";

    public static void install() {
        System.setProperty("swing.boldMetal", "false");
        try {
            MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception ignored) { }

        UIManager.put("ClassLoader", Look.class.getClassLoader());
        String pkg = Look.class.getName();
        UIManager.put("ButtonUI", pkg + "$ButtonUI");
        UIManager.put("ComboBoxUI", pkg + "$ComboUI");
        UIManager.put("SpinnerUI", pkg + "$SpinnerUI");
        UIManager.put("TabbedPaneUI", pkg + "$TabsUI");
        UIManager.put("ScrollBarUI", pkg + "$ScrollBarUI");
        UIManager.put("ProgressBarUI", pkg + "$ProgressUI");
        UIManager.put("SplitPaneUI", "javax.swing.plaf.basic.BasicSplitPaneUI");

        FontUIResource base = new FontUIResource(Theme.BASE);
        for (String k : new String[]{"Label", "Button", "ToggleButton", "CheckBox", "RadioButton", "ComboBox", "List",
                "TextField", "FormattedTextField", "Spinner", "Table", "TableHeader", "TabbedPane", "ProgressBar",
                "OptionPane", "Menu", "MenuItem", "PopupMenu", "TitledBorder"}) {
            UIManager.put(k + ".font", base);
        }
        UIManager.put("ToolTip.font", new FontUIResource(Theme.SMALL));
        UIManager.put("ProgressBar.font", new FontUIResource(Theme.SMALL.deriveFont(Font.BOLD)));
        UIManager.put("TabbedPane.font", new FontUIResource(Theme.SEMIBOLD));

        color("Panel.background", Theme.PAGE);
        color("OptionPane.background", Theme.PAGE);
        color("Label.foreground", Theme.TEXT);
        color("CheckBox.background", Theme.SURFACE);
        color("CheckBox.foreground", Theme.TEXT);
        color("Viewport.background", Theme.SURFACE);
        color("ScrollPane.background", Theme.SURFACE);
        color("SplitPane.background", Theme.PAGE);
        UIManager.put("SplitPane.dividerSize", 10);
        UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder());

        color("ToolTip.background", Theme.TEXT);
        color("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(5, 8, 5, 8));

        color("List.selectionBackground", Theme.SELECTION);
        color("List.selectionForeground", Theme.TEXT);
        color("ComboBox.selectionBackground", Theme.SELECTION);
        color("ComboBox.selectionForeground", Theme.TEXT);
        color("ComboBox.background", Theme.SURFACE);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(Theme.BORDER));
        UIManager.put("ComboBox.squareButton", false);

        Border field = BorderFactory.createCompoundBorder(new RoundBorder(Theme.BORDER, 8),
                BorderFactory.createEmptyBorder(4, 8, 4, 8));
        UIManager.put("TextField.border", field);
        UIManager.put("FormattedTextField.border", field);
        color("TextField.selectionBackground", Theme.SELECTION);
        color("TextField.selectionForeground", Theme.TEXT);
        color("TextField.caretForeground", Theme.TEXT);
        color("TextField.inactiveBackground", Theme.SURFACE);

        UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder());
        color("Table.background", Theme.SURFACE);
        color("TableHeader.background", Theme.SURFACE);
        UIManager.put("TableHeader.cellBorder", BorderFactory.createEmptyBorder());
    }

    private static void color(String key, Color c) { UIManager.put(key, new ColorUIResource(c)); }

    private static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.antialias(g2);
        return g2;
    }

    // ---------------------------------------------------------------- borders

    /** A 1px rounded outline. */
    public static class RoundBorder extends AbstractBorder {
        private final Color color;
        private final int arc;
        public RoundBorder(Color color, int arc) { this.color = color; this.arc = arc; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = smooth(g);
            Color col = c.hasFocus() && c instanceof javax.swing.text.JTextComponent ? Theme.ACCENT : color;
            g2.setColor(col);
            g2.drawRoundRect(x, y, w - 1, h - 1, arc, arc);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(1, 1, 1, 1); }
        @Override public Insets getBorderInsets(Component c, Insets i) { i.set(1, 1, 1, 1); return i; }
    }

    // ---------------------------------------------------------------- buttons

    public static class ButtonUI extends BasicButtonUI {
        private static final ButtonUI INSTANCE = new ButtonUI();
        public static ComponentUI createUI(JComponent c) { return INSTANCE; }

        @Override protected void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            b.setOpaque(false);
            b.setRolloverEnabled(true);
            b.setFocusPainted(false);
            b.setContentAreaFilled(false);
            b.setFont(Theme.SEMIBOLD);
            b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
            b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }

        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel m = b.getModel();
            Object variant = b.getClientProperty(VARIANT);
            boolean hover = m.isRollover() && b.isEnabled(), down = m.isArmed() && m.isPressed();
            Color fill, line, ink;
            if ("primary".equals(variant)) {
                fill = down ? Theme.HEADER.darker() : hover ? Theme.ACCENT_HOVER : Theme.HEADER;
                line = null;
                ink = Color.WHITE;
            } else if ("onDark".equals(variant)) {
                fill = new Color(255, 255, 255, down ? 70 : hover ? 50 : 30);
                line = new Color(255, 255, 255, 60);
                ink = Color.WHITE;
            } else {
                fill = down ? Theme.HOVER.darker() : hover ? Theme.HOVER : Theme.SURFACE;
                line = hover ? Theme.ACCENT_SOFT : Theme.BORDER;
                ink = Theme.TEXT;
            }
            if (!b.isEnabled()) {
                fill = withAlpha(fill, fill.getAlpha() / 2);
                ink = withAlpha(ink, 120);
            }
            Graphics2D g2 = smooth(g);
            int w = c.getWidth(), h = c.getHeight();
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
            if (line != null) {
                g2.setColor(line);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
            }
            if (b.isFocusOwner() && !"onDark".equals(variant)) {
                g2.setColor(Theme.ACCENT_SOFT);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 9, 9);
            }
            g2.setFont(b.getFont());
            g2.setColor(ink);
            FontMetrics fm = g2.getFontMetrics();
            String text = b.getText() == null ? "" : b.getText();
            g2.drawString(text, (w - fm.stringWidth(text)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    private static Color withAlpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a))); }

    // ---------------------------------------------------------------- combo boxes

    private static void chevron(Graphics2D g2, int cx, int cy, boolean up, Color color) {
        Path2D p = new Path2D.Double();
        int d = up ? -1 : 1;
        p.moveTo(cx - 4, cy - 2 * d);
        p.lineTo(cx, cy + 2 * d);
        p.lineTo(cx + 4, cy - 2 * d);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(p);
    }

    private static class ChevronButton extends JButton {
        private final boolean up;
        private final int width;
        ChevronButton(boolean up, int width) {
            this.up = up;
            this.width = width;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder());
            setContentAreaFilled(false);
            setFocusable(false);
            setRolloverEnabled(true);
        }
        @Override public void updateUI() { setUI(new BasicButtonUI()); }
        @Override public Dimension getPreferredSize() { return new Dimension(width, 12); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            if (getModel().isRollover()) {
                g2.setColor(Theme.HOVER);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            chevron(g2, getWidth() / 2, getHeight() / 2, up, isEnabled() ? Theme.TEXT_SECONDARY : Theme.TEXT_MUTED);
            g2.dispose();
        }
    }

    public static class ComboUI extends BasicComboBoxUI {
        public static ComponentUI createUI(JComponent c) { return new ComboUI(); }

        @Override protected void installDefaults() {
            super.installDefaults();
            comboBox.setOpaque(false);
            comboBox.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
            comboBox.setBackground(Theme.SURFACE);
            comboBox.setForeground(Theme.TEXT);
            comboBox.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            padding = new InsetsUIResource(4, 9, 4, 4);
            comboBox.addMouseListener(hover);
        }
        @Override protected void uninstallDefaults() {
            comboBox.removeMouseListener(hover);
            super.uninstallDefaults();
        }

        private boolean over;
        private final MouseAdapter hover = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { over = true; comboBox.repaint(); }
            @Override public void mouseExited(MouseEvent e) { over = false; comboBox.repaint(); }
        };

        @Override protected JButton createArrowButton() {
            JButton b = new ChevronButton(false, 24) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = smooth(g);
                    chevron(g2, getWidth() / 2 - 2, getHeight() / 2, false,
                            isEnabled() ? Theme.TEXT_SECONDARY : Theme.TEXT_MUTED);
                    g2.dispose();
                }
            };
            b.setName("ComboBox.arrowButton");
            return b;
        }

        @Override public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = smooth(g);
            int w = c.getWidth(), h = c.getHeight();
            g2.setColor(over && c.isEnabled() ? Theme.HOVER : Theme.SURFACE);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.setColor(comboBox.isPopupVisible() || comboBox.hasFocus() ? Theme.ACCENT_SOFT : Theme.BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.dispose();
            if (!comboBox.isEditable()) paintCurrentValue(g, rectangleForCurrentValue(), false);
        }

        @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) { }

        @Override public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
            Component r = comboBox.getRenderer().getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false);
            r.setFont(comboBox.getFont());
            r.setForeground(comboBox.isEnabled() ? Theme.TEXT : Theme.TEXT_MUTED);
            if (r instanceof JComponent jc) jc.setOpaque(false);
            currentValuePane.paintComponent(g, r, comboBox, bounds.x, bounds.y, bounds.width, bounds.height,
                    r instanceof JComponent);
        }

        @Override protected Dimension getDefaultSize() {
            Dimension d = super.getDefaultSize();
            return new Dimension(d.width, Math.max(d.height, 20));
        }
    }

    // ---------------------------------------------------------------- spinners

    public static class SpinnerUI extends BasicSpinnerUI {
        public static ComponentUI createUI(JComponent c) { return new SpinnerUI(); }

        @Override protected void installDefaults() {
            super.installDefaults();
            spinner.setOpaque(false);
            spinner.setBorder(new RoundBorder(Theme.BORDER, 10));
        }

        @Override protected Component createNextButton() {
            ChevronButton b = new ChevronButton(true, 20);
            b.setName("Spinner.nextButton");
            installNextButtonListeners(b);
            return b;
        }

        @Override protected Component createPreviousButton() {
            ChevronButton b = new ChevronButton(false, 20);
            b.setName("Spinner.previousButton");
            installPreviousButtonListeners(b);
            return b;
        }

        @Override protected javax.swing.JComponent createEditor() {
            JComponent editor = super.createEditor();
            styleEditor(editor);
            return editor;
        }

        @Override protected void replaceEditor(JComponent oldEditor, JComponent newEditor) {
            styleEditor(newEditor);
            super.replaceEditor(oldEditor, newEditor);
        }

        private static void styleEditor(JComponent editor) {
            editor.setOpaque(false);
            if (editor instanceof JSpinner.DefaultEditor de) {
                JFormattedTextField tf = de.getTextField();
                tf.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 4));
                tf.setOpaque(false);
            }
        }

        @Override public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = smooth(g);
            g2.setColor(Theme.SURFACE);
            g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 10, 10);
            g2.dispose();
            super.paint(g, c);
        }
    }

    // ---------------------------------------------------------------- tabs

    /** Flat tab strip that sits on the purple header, with a bright underline under the selected tab. */
    public static class TabsUI extends BasicTabbedPaneUI {
        public static ComponentUI createUI(JComponent c) { return new TabsUI(); }
        private int hoverTab = -1;

        @Override protected void installDefaults() {
            super.installDefaults();
            tabInsets = new Insets(9, 14, 11, 14);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            tabAreaInsets = new Insets(0, 10, 0, 10);
            contentBorderInsets = new Insets(0, 0, 0, 0);
            tabRunOverlay = 0;
            tabPane.setOpaque(true);
            tabPane.setBackground(Theme.PAGE);
            tabPane.setFont(Theme.SEMIBOLD);
        }

        @Override protected void installListeners() {
            super.installListeners();
            tabPane.addMouseMotionListener(motion);
            tabPane.addMouseListener(motion);
        }

        private final MouseAdapter motion = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { setHover(tabForCoordinate(tabPane, e.getX(), e.getY())); }
            @Override public void mouseExited(MouseEvent e) { setHover(-1); }
        };

        private void setHover(int i) { if (i != hoverTab) { hoverTab = i; tabPane.repaint(); } }

        @Override public void paint(Graphics g, JComponent c) {
            int h = calculateTabAreaHeight(JTabbedPane.TOP, runCount, maxTabHeight);
            g.setColor(Theme.HEADER);
            g.fillRect(0, 0, c.getWidth(), h);
            super.paint(g, c);
        }

        @Override protected int calculateTabWidth(int placement, int tabIndex, FontMetrics metrics) {
            return super.calculateTabWidth(placement, tabIndex, metrics) + 2;
        }

        @Override protected void paintTabBackground(Graphics g, int p, int i, int x, int y, int w, int h, boolean sel) {
            if (i == hoverTab && !sel && tabPane.isEnabled()) {
                Graphics2D g2 = smooth(g);
                g2.setColor(new Color(255, 255, 255, 18));
                g2.fillRoundRect(x + 2, y + 4, w - 4, h - 8, 8, 8);
                g2.dispose();
            }
        }

        @Override protected void paintTabBorder(Graphics g, int p, int i, int x, int y, int w, int h, boolean sel) {
            if (sel) {
                Graphics2D g2 = smooth(g);
                g2.setColor(tabPane.isEnabled() ? Theme.HIGHLIGHT : withAlpha(Theme.HIGHLIGHT, 90));
                g2.fillRoundRect(x + 10, y + h - 3, w - 20, 3, 3, 3);
                g2.dispose();
            }
        }

        @Override protected void paintText(Graphics g, int p, Font font, FontMetrics fm, int i, String title,
                                           Rectangle textRect, boolean sel) {
            Graphics2D g2 = smooth(g);
            g2.setFont(font);
            int alpha = !tabPane.isEnabled() ? 90 : sel ? 255 : i == hoverTab ? 225 : 175;
            g2.setColor(new Color(255, 255, 255, alpha));
            g2.drawString(title, textRect.x, textRect.y + fm.getAscent());
            g2.dispose();
        }

        @Override protected void paintFocusIndicator(Graphics g, int p, Rectangle[] r, int i, Rectangle ir, Rectangle tr, boolean sel) { }
        @Override protected void paintContentBorder(Graphics g, int p, int i) { }
        @Override protected int getTabLabelShiftY(int p, int i, boolean sel) { return 0; }
        @Override protected int getTabLabelShiftX(int p, int i, boolean sel) { return 0; }
    }

    // ---------------------------------------------------------------- scroll bars

    public static class ScrollBarUI extends BasicScrollBarUI {
        public static ComponentUI createUI(JComponent c) { return new ScrollBarUI(); }

        @Override protected void configureScrollBarColors() {
            thumbColor = new Color(0xcac6cf);
            trackColor = Theme.SURFACE;
        }

        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }

        private static JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override public Dimension getPreferredSize(JComponent c) {
            return scrollbar.getOrientation() == JScrollBar.VERTICAL ? new Dimension(11, 48) : new Dimension(48, 11);
        }

        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = smooth(g);
            g2.setColor(isDragging || isThumbRollover() ? new Color(0x9d97a3) : thumbColor);
            boolean v = scrollbar.getOrientation() == JScrollBar.VERTICAL;
            int pad = 3;
            if (v) g2.fillRoundRect(r.x + pad, r.y + 2, r.width - 2 * pad, r.height - 4, 6, 6);
            else g2.fillRoundRect(r.x + 2, r.y + pad, r.width - 4, r.height - 2 * pad, 6, 6);
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------- progress bars

    public static class ProgressUI extends BasicProgressBarUI {
        public static ComponentUI createUI(JComponent c) { return new ProgressUI(); }

        @Override protected void installDefaults() {
            super.installDefaults();
            progressBar.setBorder(BorderFactory.createEmptyBorder());
            progressBar.setOpaque(false);
            progressBar.setForeground(Theme.HEADER);
        }

        @Override protected Color getSelectionForeground() { return Color.WHITE; }
        @Override protected Color getSelectionBackground() { return Theme.TEXT_SECONDARY; }

        @Override protected void paintDeterminate(Graphics g, JComponent c) {
            JProgressBar pb = (JProgressBar) c;
            Graphics2D g2 = smooth(g);
            int w = c.getWidth(), h = c.getHeight();
            g2.setColor(Theme.TRACK);
            g2.fillRoundRect(0, 0, w, h, h, h);
            int amount = getAmountFull(new Insets(0, 0, 0, 0), w, h);
            if (amount > 0) {
                g2.setColor(Theme.HEADER);
                g2.fillRoundRect(0, 0, Math.max(amount, h), h, h, h);
            }
            g2.dispose();
            if (pb.isStringPainted()) paintString(g, 0, 0, w, h, amount, new Insets(0, 0, 0, 0));
        }

        @Override protected Dimension getPreferredInnerHorizontal() { return new Dimension(160, 22); }
    }

    /** Marks a button as the main action of its screen. */
    public static <T extends AbstractButton> T primary(T b) {
        b.putClientProperty(VARIANT, "primary");
        return b;
    }
}
