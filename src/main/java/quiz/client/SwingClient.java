package quiz.client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import quiz.protocol.Msg;

/**
 * A window over the same {@link ClientCore} the console client uses.
 *
 * <p>Callbacks arrive on the core's reader thread and Swing may only be touched
 * on the event dispatch thread, so each one hands its work to
 * {@link SwingUtilities#invokeLater}. Same discipline as the server: state lives
 * on one thread and everything else posts to it.
 */
public final class SwingClient implements ClientListener {

    private static final Color CREAM = new Color(0xF3E9DD);
    private static final Color FOAM = new Color(0xFBF6F0);
    private static final Color LATTE = new Color(0xDCC7AE);
    private static final Color CARAMEL = new Color(0xC0855A);
    private static final Color MOCHA = new Color(0x8B5E3C);
    private static final Color ESPRESSO = new Color(0x40291F);
    private static final Color MATCHA = new Color(0x6E8B4E);
    private static final Color BERRY = new Color(0xA8524B);

    private static final String CONNECT = "connect";
    private static final String LOBBY = "lobby";
    private static final String QUESTION = "question";

    private static final int TICK_MS = 50;

    private final JFrame frame = new JFrame("Multiplayer Quiz");
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);

    private final JTextField hostField = field("localhost");
    private final JTextField portField = field("5001");
    private final JTextField nameField = field("");
    private final JLabel connectError = new JLabel(" ", SwingConstants.CENTER);

    private final DefaultListModel<String> lobbyModel = new DefaultListModel<>();
    private final JButton startButton = new CoffeeButton("Start the round", CARAMEL);
    private final JLabel lobbyStatus = new JLabel("Waiting for players", SwingConstants.CENTER);

    private final JLabel questionLabel = new JLabel();
    private final JLabel progressLabel = new JLabel();
    private final JLabel timeLabel = new JLabel();
    private final BrewBar clock = new BrewBar();
    private final JPanel optionsPanel = new JPanel(new GridLayout(0, 2, 12, 12));
    private final JLabel feedback = new JLabel(" ");

    private final DefaultListModel<String> scoreModel = new DefaultListModel<>();
    private final JLabel status = new JLabel("Not connected");

    private ClientCore core;
    private Timer clockTimer;
    private int currentQuestion = -1;
    private long deadlineMs;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SwingClient().show());
    }

    // Layout ----------------------------------------------------------------

    private void show() {
        content.setOpaque(false);
        content.add(buildConnectPanel(), CONNECT);
        content.add(buildLobbyPanel(), LOBBY);
        content.add(buildQuestionPanel(), QUESTION);

        JPanel root = new JPanel(new BorderLayout(18, 0));
        root.setBackground(CREAM);
        root.setBorder(BorderFactory.createEmptyBorder(20, 22, 14, 22));
        root.add(content, BorderLayout.CENTER);
        root.add(buildScorePanel(), BorderLayout.EAST);

        status.setFont(body(12f));
        status.setForeground(MOCHA);
        status.setBorder(BorderFactory.createEmptyBorder(12, 4, 0, 4));
        root.add(status, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(760, 480));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel buildConnectPanel() {
        JLabel title = new JLabel("Multiplayer Quiz", SwingConstants.CENTER);
        title.setFont(display(34f));
        title.setForeground(ESPRESSO);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel tagline = new JLabel("Pour in, answer quickly.", SwingConstants.CENTER);
        tagline.setFont(body(14f));
        tagline.setForeground(MOCHA);
        tagline.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel fields = new JPanel(new GridLayout(3, 2, 10, 10));
        fields.setOpaque(false);
        fields.setMaximumSize(new Dimension(380, 120));
        fields.add(caption("Host"));
        fields.add(hostField);
        fields.add(caption("Port"));
        fields.add(portField);
        fields.add(caption("Name"));
        fields.add(nameField);

        JButton join = new CoffeeButton("Join the table", CARAMEL);
        join.setAlignmentX(Component.CENTER_ALIGNMENT);
        join.addActionListener(event -> connect());
        nameField.addActionListener(event -> connect());

        connectError.setFont(body(13f));
        connectError.setForeground(BERRY);
        connectError.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);
        inner.add(Box.createVerticalGlue());
        inner.add(title);
        inner.add(Box.createVerticalStrut(6));
        inner.add(tagline);
        inner.add(Box.createVerticalStrut(28));
        inner.add(fields);
        inner.add(Box.createVerticalStrut(20));
        inner.add(join);
        inner.add(Box.createVerticalStrut(14));
        inner.add(connectError);
        inner.add(Box.createVerticalGlue());

        return card(inner);
    }

    private JPanel buildLobbyPanel() {
        JLabel heading = new JLabel("At the table");
        heading.setFont(display(24f));
        heading.setForeground(ESPRESSO);

        JList<String> players = new JList<>(lobbyModel);
        players.setCellRenderer(new SoftRow());
        players.setOpaque(false);
        players.setFixedCellHeight(34);

        JScrollPane scroll = new JScrollPane(players);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        lobbyStatus.setFont(body(14f));
        lobbyStatus.setForeground(MOCHA);

        startButton.setEnabled(false);
        startButton.addActionListener(event -> core.send(new Msg.Start()));

        JPanel south = new JPanel();
        south.setOpaque(false);
        south.add(lobbyStatus);
        south.add(Box.createHorizontalStrut(14));
        south.add(startButton);

        JPanel inner = new JPanel(new BorderLayout(0, 14));
        inner.setOpaque(false);
        inner.add(heading, BorderLayout.NORTH);
        inner.add(scroll, BorderLayout.CENTER);
        inner.add(south, BorderLayout.SOUTH);

        return card(inner);
    }

    private JPanel buildQuestionPanel() {
        progressLabel.setFont(caps(12f));
        progressLabel.setForeground(MOCHA);

        // Seconds sit beside the bar, not inside it, so the fill stays readable.
        timeLabel.setFont(display(20f));
        timeLabel.setForeground(ESPRESSO);
        timeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        timeLabel.setPreferredSize(new Dimension(64, 24));

        questionLabel.setFont(display(24f));
        questionLabel.setForeground(ESPRESSO);

        optionsPanel.setOpaque(false);

        feedback.setFont(body(14f));
        feedback.setForeground(MOCHA);

        JPanel clockRow = new JPanel(new BorderLayout(14, 0));
        clockRow.setOpaque(false);
        clockRow.add(clock, BorderLayout.CENTER);
        clockRow.add(timeLabel, BorderLayout.EAST);
        clockRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);

        for (JComponent part : List.of(progressLabel, questionLabel, clockRow, optionsPanel,
                feedback)) {
            part.setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        inner.add(progressLabel);
        inner.add(Box.createVerticalStrut(10));
        inner.add(questionLabel);
        inner.add(Box.createVerticalStrut(16));
        inner.add(clockRow);
        inner.add(Box.createVerticalStrut(22));
        inner.add(optionsPanel);
        inner.add(Box.createVerticalStrut(16));
        inner.add(feedback);
        inner.add(Box.createVerticalGlue());

        return card(inner);
    }

    private JPanel buildScorePanel() {
        JLabel heading = new JLabel("Scores");
        heading.setFont(caps(12f));
        heading.setForeground(MOCHA);

        JList<String> scores = new JList<>(scoreModel);
        scores.setCellRenderer(new SoftRow());
        scores.setOpaque(false);
        scores.setFixedCellHeight(32);

        JScrollPane scroll = new JScrollPane(scores);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        JPanel inner = new JPanel(new BorderLayout(0, 10));
        inner.setOpaque(false);
        inner.add(heading, BorderLayout.NORTH);
        inner.add(scroll, BorderLayout.CENTER);

        JPanel card = card(inner);
        card.setPreferredSize(new Dimension(232, 0));
        return card;
    }

    // Small builders --------------------------------------------------------

    /** Wraps content in a rounded, slightly lighter panel. */
    private static JPanel card(JComponent inner) {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(FOAM);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 28, 28));
                g2.setColor(LATTE);
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1,
                        28, 28));
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));
        panel.add(inner, BorderLayout.CENTER);
        return panel;
    }

    private static JTextField field(String initial) {
        JTextField text = new JTextField(initial);
        text.setFont(body(14f));
        text.setForeground(ESPRESSO);
        text.setBackground(CREAM);
        text.setCaretColor(MOCHA);
        text.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LATTE, 1, true),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        return text;
    }

    private static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(caps(12f));
        label.setForeground(MOCHA);
        return label;
    }

    private static Font display(float size) {
        return pick(size, Font.PLAIN, "Georgia", "Palatino", "Serif");
    }

    private static Font body(float size) {
        return pick(size, Font.PLAIN, "Avenir Next", "Helvetica Neue", "SansSerif");
    }

    private static Font caps(float size) {
        return pick(size, Font.BOLD, "Avenir Next", "Helvetica Neue", "SansSerif");
    }

    private static Font pick(float size, int style, String... names) {
        for (String name : names) {
            Font font = new Font(name, style, (int) size);
            if (font.getFamily().equalsIgnoreCase(name)) {
                return font.deriveFont(style, size);
            }
        }
        return new Font(names[names.length - 1], style, (int) size);
    }

    // Behaviour -------------------------------------------------------------

    private void connect() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            connectError.setText("Pick a name first");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            connectError.setText("Port must be a number");
            return;
        }

        core = new ClientCore(hostField.getText().trim(), port, this);
        try {
            core.connect();
        } catch (IOException e) {
            connectError.setText("Could not connect: " + e.getMessage());
            return;
        }

        connectError.setText(" ");
        core.send(new Msg.Join(name));
    }

    private void stopClock() {
        if (clockTimer != null) {
            clockTimer.stop();
            clockTimer = null;
        }
    }

    private void setOptionsEnabled(boolean enabled) {
        for (Component option : optionsPanel.getComponents()) {
            option.setEnabled(enabled);
        }
    }

    // Listener --------------------------------------------------------------

    @Override
    public void onWelcome(String playerId, boolean host) {
        SwingUtilities.invokeLater(() -> {
            startButton.setEnabled(host);
            lobbyStatus.setText(host ? "You are pouring" : "Waiting for the host");
            status.setText("Connected");
            cards.show(content, LOBBY);
        });
    }

    @Override
    public void onLobby(List<String> players) {
        SwingUtilities.invokeLater(() -> {
            lobbyModel.clear();
            players.forEach(lobbyModel::addElement);
        });
    }

    @Override
    public void onQuestion(Msg.Question question) {
        SwingUtilities.invokeLater(() -> {
            currentQuestion = question.index();
            deadlineMs = System.currentTimeMillis() + question.limitMs();

            progressLabel.setText("QUESTION " + (question.index() + 1) + " OF " + question.total());
            questionLabel.setText("<html>" + escape(question.text()) + "</html>");
            feedback.setText(" ");

            optionsPanel.removeAll();
            List<String> options = question.options();
            for (int i = 0; i < options.size(); i++) {
                int choice = i;
                JButton button = new CoffeeButton(options.get(i), LATTE);
                button.addActionListener(event -> {
                    core.send(new Msg.Answer(currentQuestion, choice));
                    setOptionsEnabled(false);
                    feedback.setText("Answer poured.");
                });
                optionsPanel.add(button);
            }
            optionsPanel.revalidate();
            optionsPanel.repaint();

            clock.setFraction(1f);
            timeLabel.setText(question.limitMs() / 1000 + "s");

            stopClock();
            // A Swing timer fires on the event dispatch thread, unlike the
            // server's scheduler, so it may touch components directly.
            clockTimer = new Timer(TICK_MS, event -> {
                long left = Math.max(0, deadlineMs - System.currentTimeMillis());
                clock.setFraction(left / (float) question.limitMs());
                timeLabel.setText(Math.round(left / 1000f) + "s");
                if (left == 0) {
                    stopClock();
                }
            });
            clockTimer.start();

            cards.show(content, QUESTION);
        });
    }

    @Override
    public void onAnswerAck(int questionIndex) {
        SwingUtilities.invokeLater(() -> feedback.setText("Answer poured."));
    }

    @Override
    public void onReveal(Msg.Reveal reveal) {
        SwingUtilities.invokeLater(() -> {
            stopClock();
            setOptionsEnabled(false);
            currentQuestion = -1;
            clock.setFraction(0f);
            timeLabel.setText("0s");

            Component[] options = optionsPanel.getComponents();
            if (reveal.correctIndex() < options.length
                    && options[reveal.correctIndex()] instanceof CoffeeButton correct) {
                correct.setTone(MATCHA);
            }

            feedback.setForeground(reveal.points() > 0 ? MATCHA : BERRY);
            feedback.setText(reveal.points() > 0
                    ? "+" + reveal.points() + " points   ·   total " + reveal.total()
                    : "No points this round   ·   total " + reveal.total());
        });
    }

    @Override
    public void onScores(List<Msg.Scores.Row> rows) {
        SwingUtilities.invokeLater(() -> {
            scoreModel.clear();
            for (Msg.Scores.Row row : rows) {
                scoreModel.addElement(row.rank() + ".  " + row.name() + "   " + row.score());
            }
        });
    }

    @Override
    public void onGameOver() {
        SwingUtilities.invokeLater(() -> {
            stopClock();
            optionsPanel.removeAll();
            optionsPanel.revalidate();
            optionsPanel.repaint();
            clock.setFraction(0f);
            timeLabel.setText(" ");
            progressLabel.setText("THE LAST DROP");
            questionLabel.setText("Game over");
            feedback.setForeground(MOCHA);
            feedback.setText("Final scores are on the right.");
            status.setText("Game over");
        });
    }

    @Override
    public void onError(String code, String message) {
        SwingUtilities.invokeLater(() -> {
            if (lobbyModel.isEmpty()) {
                connectError.setText(message);
            }
            status.setText(message);
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            stopClock();
            setOptionsEnabled(false);
            status.setText("Disconnected: " + reason);
        });
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // Painted parts ---------------------------------------------------------

    /** A rounded, flat button that darkens on hover and press. */
    private static final class CoffeeButton extends JButton {

        private Color tone;

        CoffeeButton(String text, Color tone) {
            super(text);
            this.tone = tone;
            setFont(body(15f));
            setForeground(tone == LATTE ? ESPRESSO : FOAM);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        }

        void setTone(Color newTone) {
            this.tone = newTone;
            setForeground(FOAM);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            Color fill = tone;
            if (!isEnabled()) {
                fill = blend(tone, CREAM, 0.55f);
            } else if (getModel().isPressed()) {
                fill = blend(tone, ESPRESSO, 0.25f);
            } else if (getModel().isRollover()) {
                fill = blend(tone, ESPRESSO, 0.12f);
            }

            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 22, 22));
            g2.dispose();

            super.paintComponent(g);
        }
    }

    /** The countdown, drawn as a rounded bar that drains from the right. */
    private static final class BrewBar extends JComponent {

        private float fraction = 1f;

        BrewBar() {
            setPreferredSize(new Dimension(10, 14));
        }

        void setFraction(float value) {
            fraction = Math.clamp(value, 0f, 1f);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int height = getHeight();
            g2.setColor(blend(LATTE, FOAM, 0.4f));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), height, height, height));

            float filled = getWidth() * fraction;
            if (filled > 1) {
                // Warms towards red as the time runs out.
                Color left = fraction < 0.25f ? BERRY : MOCHA;
                g2.setPaint(new GradientPaint(0, 0, left, filled, 0, CARAMEL));
                g2.fill(new RoundRectangle2D.Float(0, 0, filled, height, height, height));
            }
            g2.dispose();
        }
    }

    /** Rounded list rows, so the lists match the cards. */
    private static final class SoftRow extends JLabel implements ListCellRenderer<String> {

        private boolean selected;

        SoftRow() {
            setOpaque(false);
            setFont(body(14f));
            setForeground(ESPRESSO);
            setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value,
                int index, boolean isSelected, boolean hasFocus) {
            setText(value);
            selected = isSelected;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(selected ? blend(LATTE, FOAM, 0.3f) : blend(CREAM, FOAM, 0.5f));
            g2.fill(new RoundRectangle2D.Float(0, 1, getWidth(), getHeight() - 4f, 16, 16));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static Color blend(Color base, Color towards, float amount) {
        return new Color(
                Math.round(base.getRed() + (towards.getRed() - base.getRed()) * amount),
                Math.round(base.getGreen() + (towards.getGreen() - base.getGreen()) * amount),
                Math.round(base.getBlue() + (towards.getBlue() - base.getBlue()) * amount));
    }
}
