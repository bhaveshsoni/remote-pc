package io.orbion.ui;

import io.orbion.core.QrCodeService;
import io.orbion.core.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/**
 * System tray icon and desktop dashboard window showing the QR code,
 * local IP, session token, connected devices and recent commands.
 */
@org.springframework.stereotype.Component
public class TrayManager {

    private static final Logger log = LoggerFactory.getLogger(TrayManager.class);

    private static final Color BG = new Color(0x0A0A0C);
    private static final Color CARD = new Color(0x16161A);
    private static final Color TEXT = new Color(0xEDEDEF);
    private static final Color MUTED = new Color(0x8A8A93);
    private static final Color ACCENT = new Color(0x8B5CF6);

    private final SessionService sessionService;
    private final QrCodeService qrCodeService;
    private final io.orbion.core.SslCustomizer sslCustomizer;

    private JFrame frame;
    private JLabel devicesLabel;
    private JTextArea commandsArea;

    public TrayManager(SessionService sessionService, QrCodeService qrCodeService,
                       io.orbion.core.SslCustomizer sslCustomizer) {
        this.sessionService = sessionService;
        this.qrCodeService = qrCodeService;
        this.sslCustomizer = sslCustomizer;
    }

    @EventListener
    public void onServerStarted(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        boolean ssl = sslCustomizer.isSslEnabled();
        String pairingUrl = sessionService.pairingUrl(ssl ? "https" : "http", port);
        log.info("========================================");
        log.info("Orbion is running");
        log.info("Pairing URL: {}", pairingUrl);
        log.info("Session token: {}", sessionService.getToken());
        log.info("========================================");

        if (GraphicsEnvironment.isHeadless()) {
            log.warn("Headless environment: tray icon and dashboard are disabled.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            buildDashboard(pairingUrl);
            installTrayIcon();
            sessionService.addListener(() -> SwingUtilities.invokeLater(this::refresh));
        });
    }

    private void buildDashboard(String pairingUrl) {
        frame = new JFrame("Orbion");
        frame.setDefaultCloseOperation(SystemTray.isSupported()
                ? JFrame.HIDE_ON_CLOSE : JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(BG);
        frame.setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        panel.setBackground(BG);
        panel.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = label("Orbion", 26, Font.BOLD, TEXT);
        JLabel subtitle = label("Scan with your phone to connect", 13, Font.PLAIN, MUTED);

        JLabel qrLabel = new JLabel(new ImageIcon(qrCodeService.generate(pairingUrl, 220)));
        qrLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        qrLabel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel ipLabel = label("IP  " + sessionService.getLocalIp(), 13, Font.PLAIN, TEXT);
        JLabel tokenLabel = label("Token  " + sessionService.getToken(), 12, Font.PLAIN, MUTED);
        devicesLabel = label("Connected devices: 0", 13, Font.BOLD, ACCENT);

        commandsArea = new JTextArea(8, 36);
        commandsArea.setEditable(false);
        commandsArea.setBackground(CARD);
        commandsArea.setForeground(TEXT);
        commandsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        commandsArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(commandsArea);
        scroll.setBorder(BorderFactory.createLineBorder(CARD));
        scroll.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(title);
        panel.add(vspace(4));
        panel.add(subtitle);
        panel.add(vspace(12));
        panel.add(qrLabel);
        panel.add(ipLabel);
        panel.add(vspace(4));
        panel.add(tokenLabel);
        panel.add(vspace(10));
        panel.add(devicesLabel);
        panel.add(vspace(12));
        panel.add(label("Recent commands", 13, Font.BOLD, TEXT));
        panel.add(vspace(6));
        panel.add(scroll);

        frame.add(panel, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void installTrayIcon() {
        if (!SystemTray.isSupported()) {
            log.warn("System tray not supported on this platform.");
            return;
        }
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Open Dashboard");
            open.addActionListener(e -> SwingUtilities.invokeLater(() -> {
                frame.setVisible(true);
                frame.setState(JFrame.NORMAL);
                frame.toFront();
            }));
            MenuItem quit = new MenuItem("Quit Orbion");
            quit.addActionListener(e -> System.exit(0));
            menu.add(open);
            menu.addSeparator();
            menu.add(quit);

            TrayIcon trayIcon = new TrayIcon(trayImage(), "Orbion", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> SwingUtilities.invokeLater(() -> frame.setVisible(true)));
            SystemTray.getSystemTray().add(trayIcon);
        } catch (Exception e) {
            log.warn("Could not install tray icon: {}", e.getMessage());
        }
    }

    private void refresh() {
        if (devicesLabel != null) {
            devicesLabel.setText("Connected devices: " + sessionService.getDeviceCount());
        }
        if (commandsArea != null) {
            commandsArea.setText(String.join("\n", sessionService.getRecentCommands()));
            commandsArea.setCaretPosition(0);
        }
    }

    private static JLabel label(String text, int size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", style, size));
        l.setForeground(color);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private static Component vspace(int height) {
        return javax.swing.Box.createVerticalStrut(height);
    }

    private static BufferedImage trayImage() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(ACCENT);
        g.fillOval(1, 1, 14, 14);
        g.setColor(Color.WHITE);
        g.fillOval(6, 6, 4, 4);
        g.dispose();
        return img;
    }
}
