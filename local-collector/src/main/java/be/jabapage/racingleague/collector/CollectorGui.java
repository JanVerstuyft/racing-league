package be.jabapage.racingleague.collector;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.util.List;

@Component
public class CollectorGui extends JFrame {

    private final CollectorSettings settings;
    private final UdpForwarderService forwarderService;

    private JTextField tokenField;
    private JTextField listenPortField;
    private JCheckBox cloudForwardCheckbox;
    private JCheckBox udpForwardCheckbox;
    private JTextField forwardHostField;
    private JTextField forwardPortField;

    public CollectorGui(CollectorSettings settings, UdpForwarderService forwarderService) {
        this.settings = settings;
        this.forwarderService = forwarderService;
    }

    @PostConstruct
    public void init() {
        setTitle("Racing League Tools - Local Collector");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Top Panel: Instructions
        JPanel instructionsPanel = new JPanel();
        instructionsPanel.setLayout(new BoxLayout(instructionsPanel, BoxLayout.Y_AXIS));
        instructionsPanel.setBorder(BorderFactory.createTitledBorder("F1 25 Telemetry Setup"));
        
        JTextArea instructionsText = new JTextArea();
        instructionsText.setEditable(false);
        instructionsText.setLineWrap(true);
        instructionsText.setWrapStyleWord(true);
        instructionsText.setBackground(instructionsPanel.getBackground());
        instructionsText.setText("1. Open F1 25 game.\n" +
                "2. Go to Settings > Telemetry Settings.\n" +
                "3. Set UDP Telemetry to 'On'.\n" +
                "4. Set UDP IP Address to one of the Local IP addresses listed below.\n" +
                "5. Set UDP Port to 20777.\n" +
                "6. Set UDP Format to 2025.");
        instructionsPanel.add(instructionsText);
        add(instructionsPanel, BorderLayout.NORTH);

        // Center Panel: Info and Settings
        JPanel centerPanel = new JPanel(new GridLayout(2, 1));
        
        // IP Info
        JPanel ipPanel = new JPanel(new BorderLayout());
        ipPanel.setBorder(BorderFactory.createTitledBorder("Local IP Addresses"));
        JTextArea ipText = new JTextArea();
        ipText.setEditable(false);
        List<String> ips = forwarderService.getLocalIpAddresses();
        ipText.setText(String.join("\n", ips));
        ipPanel.add(new JScrollPane(ipText), BorderLayout.CENTER);
        centerPanel.add(ipPanel);

        // Configuration Form
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Collector Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        configPanel.add(new JLabel("Listening UDP Port:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        listenPortField = new JTextField(String.valueOf(settings.getPort()));
        configPanel.add(listenPortField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        configPanel.add(new JLabel("Cloud Telemetry Token:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        tokenField = new JTextField(settings.getCloudToken());
        configPanel.add(tokenField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        configPanel.add(new JLabel("Enable Cloud Sync:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0;
        cloudForwardCheckbox = new JCheckBox();
        cloudForwardCheckbox.setSelected(settings.isCloudForwardEnabled());
        configPanel.add(cloudForwardCheckbox, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0;
        configPanel.add(new JLabel("Enable Local UDP Forwarding:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 1.0;
        udpForwardCheckbox = new JCheckBox();
        udpForwardCheckbox.setSelected(settings.isForwardEnabled());
        configPanel.add(udpForwardCheckbox, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0;
        configPanel.add(new JLabel("UDP Forward Host:"), gbc);
        gbc.gridx = 1; gbc.gridy = 4; gbc.weightx = 1.0;
        forwardHostField = new JTextField(settings.getForwardHost());
        configPanel.add(forwardHostField, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0.0;
        configPanel.add(new JLabel("UDP Forward Port:"), gbc);
        gbc.gridx = 1; gbc.gridy = 5; gbc.weightx = 1.0;
        forwardPortField = new JTextField(String.valueOf(settings.getForwardPort()));
        configPanel.add(forwardPortField, gbc);

        centerPanel.add(configPanel);
        add(centerPanel, BorderLayout.CENTER);

        // Bottom Panel: Actions
        JPanel bottomPanel = new JPanel();
        JButton saveButton = new JButton("Save & Apply");
        saveButton.addActionListener(e -> applySettings());
        bottomPanel.add(saveButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // Make visible
        SwingUtilities.invokeLater(() -> setVisible(true));
    }

    private void applySettings() {
        try {
            settings.setPort(Integer.parseInt(listenPortField.getText().trim()));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid listening port number", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        settings.setCloudToken(tokenField.getText().trim());
        settings.setCloudForwardEnabled(cloudForwardCheckbox.isSelected());
        settings.setForwardEnabled(udpForwardCheckbox.isSelected());
        settings.setForwardHost(forwardHostField.getText().trim());
        
        try {
            settings.setForwardPort(Integer.parseInt(forwardPortField.getText().trim()));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid port number", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        settings.save();
        forwarderService.restart();
        
        JOptionPane.showMessageDialog(this, "Settings saved and service restarted.", "Success", JOptionPane.INFORMATION_MESSAGE);
    }
}
