package app;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

final class ProcessPickerWindow {
    private final JFrame frame = new JFrame("Minecraft GUI Injector");
    private final DefaultListModel<JavaProcess> processModel = new DefaultListModel<>();
    private final JList<JavaProcess> processList = new JList<>(processModel);
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton injectButton = new JButton("Open GUI");
    // Update switches off the versions already in the game, then opens this one.
    private final JButton updateButton = new JButton("Update");
    private final JButton unloadButton = new JButton("Unload");
    // Lists the versions loaded in the game and whether each one's loop is still running.
    private final JButton statusButton = new JButton("Status");
    private final JLabel statusLabel = new JLabel(" ");
    private final Path agentJar = locateAgentJar();

    ProcessPickerWindow() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(700, 340));
        frame.setSize(800, 420);
        frame.setLocationByPlatform(true);

        processList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        processList.addListSelectionListener(event -> setCommandButtonsEnabled(true));

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 12, 14));
        content.add(new JLabel("Java processes"), BorderLayout.NORTH);
        content.add(new JScrollPane(processList), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(statusLabel);
        actions.add(refreshButton);
        actions.add(statusButton);
        actions.add(unloadButton);
        actions.add(updateButton);
        actions.add(injectButton);
        content.add(actions, BorderLayout.SOUTH);

        refreshButton.addActionListener(event -> refreshProcesses());
        injectButton.addActionListener(event -> runCommand("open", "Opening GUI in"));
        updateButton.addActionListener(event -> runCommand("update", "Updating"));
        unloadButton.addActionListener(event -> runCommand("unload", "Unloading from"));
        statusButton.addActionListener(event -> runCommand("status", "Checking"));
        setCommandButtonsEnabled(true);
        frame.setContentPane(content);
    }

    void show() {
        frame.setVisible(true);
        refreshProcesses();
    }

    private void refreshProcesses() {
        refreshButton.setEnabled(false);
        statusLabel.setText("Refreshing...");
        new SwingWorker<List<JavaProcess>, Void>() {
            @Override
            protected List<JavaProcess> doInBackground() {
                return MinecraftProcessService.listProcesses();
            }

            @Override
            protected void done() {
                try {
                    processModel.clear();
                    for (JavaProcess process : get()) {
                        processModel.addElement(process);
                    }
                    statusLabel.setText(processModel.isEmpty()
                            ? "No Java processes found"
                            : processModel.size() + " processes");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Refresh interrupted");
                } catch (ExecutionException exception) {
                    statusLabel.setText("Could not list Java processes");
                } finally {
                    refreshButton.setEnabled(true);
                }
            }
        }.execute();
    }

    /** Enabled only when a process is selected (and nothing is running). */
    private void setCommandButtonsEnabled(boolean enabled) {
        boolean usable = enabled && processList.getSelectedValue() != null;
        injectButton.setEnabled(usable);
        updateButton.setEnabled(usable);
        unloadButton.setEnabled(usable);
        statusButton.setEnabled(usable);
    }

    private void runCommand(String command, String working) {
        JavaProcess selectedProcess = processList.getSelectedValue();
        if (selectedProcess == null) {
            statusLabel.setText("Select a process first");
            return;
        }
        if (!Files.isRegularFile(agentJar)) {
            statusLabel.setText("Build the agent JAR first");
            return;
        }

        refreshButton.setEnabled(false);
        setCommandButtonsEnabled(false);
        statusLabel.setText(working + " PID " + selectedProcess.pid() + "...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return MinecraftProcessService.attach(selectedProcess, agentJar, command);
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    statusLabel.setText(result.lines().findFirst().orElse(result));
                    if (command.equals("status")) {
                        JOptionPane.showMessageDialog(frame, result,
                                "Versions in PID " + selectedProcess.pid(), JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Interrupted");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    String message = cause.getMessage();
                    statusLabel.setText(message == null ? "Failed" : message);
                } finally {
                    refreshButton.setEnabled(true);
                    setCommandButtonsEnabled(true);
                }
            }
        }.execute();
    }

    private static Path locateAgentJar() {
        try {
            Path location = Path.of(ProcessPickerWindow.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                return location;
            }
        } catch (URISyntaxException | NullPointerException ignored) {
        }
        // Running from the IDE: use the newest built injector jar.
        Path fallback = Path.of("build", "libs", "minecraft-gui-injector.jar").toAbsolutePath();
        try (var jars = Files.list(Path.of("build", "libs"))) {
            return jars.filter(jar -> jar.getFileName().toString().startsWith("minecraft-gui-injector-"))
                    .max(Comparator.comparingLong(jar -> jar.toFile().lastModified()))
                    .map(Path::toAbsolutePath)
                    .orElse(fallback);
        } catch (IOException exception) {
            return fallback;
        }
    }
}