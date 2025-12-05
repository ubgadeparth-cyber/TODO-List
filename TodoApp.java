import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.text.*;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.regex.Pattern;

public class TodoApp extends JFrame {
    // UI components
    private JTable table;
    private DefaultTableModel model;
    private JTextField tfTask, tfSearch;
    private JLabel status;
    private JSpinner spinnerDue;
    private JComboBox<String> cbPriority;
    private JComboBox<String> cbFilter;
    private JCheckBox cbDarkMode;
    private JSpinner spinnerNotifyAhead; // minutes before due to notify

    private final Path saveFile = Paths.get("todo.csv");
    private final SimpleDateFormat dtFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    // color theme 
    private Color bg = new Color(250, 245, 255);
    private Color headerStart = new Color(190, 0, 255);
    private Color headerEnd = new Color(255, 0, 150);
    private Color statusBarColor = new Color(190,0,255);

    // Timer for notifications
    private Timer notifyTimer;

    public TodoApp() {
        super("Todo List App");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 680);
        setLocationRelativeTo(null);
        initUI();
        loadTasks();
        startNotificationChecker();
    }

    private void initUI() {
        // Layout and background
        getContentPane().setBackground(bg);
        setLayout(new BorderLayout(10,10));

        // Header gradient panel
        JPanel header = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                GradientPaint gp = new GradientPaint(0,0,headerStart, getWidth(), getHeight(), headerEnd);
                g2.setPaint(gp);
                g2.fillRect(0,0,getWidth(),getHeight());
            }
        };
        header.setPreferredSize(new Dimension(900,100));
        header.setLayout(new BorderLayout());
        JLabel title = new JLabel("My Practical Todo List");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 32));
        title.setBorder(new EmptyBorder(22,20,22,20));
        header.add(title, BorderLayout.WEST);

        // Controls panel (task input, priority, due date, actions)
        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBackground(bg);
        controls.setBorder(new EmptyBorder(10,16,10,16));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;

        tfTask = new JTextField();
        tfTask.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        tfTask.setColumns(36);

        cbPriority = new JComboBox<>(new String[]{"Medium","High","Low"});
        cbPriority.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // Due date
        SpinnerDateModel dateModel = new SpinnerDateModel(new Date(), null, null, Calendar.MINUTE);
        spinnerDue = new JSpinner(dateModel);
        JSpinner.DateEditor de = new JSpinner.DateEditor(spinnerDue, "yyyy-MM-dd HH:mm");
        spinnerDue.setEditor(de);

        // Notification lead time
        spinnerNotifyAhead = new JSpinner(new SpinnerNumberModel(10, 0, 1440, 5)); // default 10 minutes

        // Buttons 
        JButton btnAdd = gradientButton("➕ Add Task", new Color(45,140,255), new Color(30,110,220));
        JButton btnEdit = gradientButton("✏ Edit", new Color(106,90,205), new Color(80,60,170));
        JButton btnDelete = gradientButton("🗑 Delete", new Color(255,75,95), new Color(200,45,60));
        JButton btnDone = gradientButton("✔ Mark Done", new Color(0,180,110), new Color(0,150,90));
        JButton btnSave = gradientButton("💾 Save", new Color(70,200,160), new Color(40,170,140));
        JButton btnLoad = gradientButton("🔄 Load", new Color(130,100,255), new Color(100,70,220));

        // Row 0 
        c.gridx=0; c.gridy=0; c.gridwidth=6; controls.add(tfTask, c);
        c.gridwidth=1;

        // Row 1 - Priority, Due, Notify, Dark Mode
        c.gridx=0; c.gridy=1; controls.add(new JLabel("Priority:"), c);
        c.gridx=1; controls.add(cbPriority, c);
        c.gridx=2; controls.add(new JLabel("Due (yyyy-MM-dd HH:mm):"), c);
        c.gridx=3; controls.add(spinnerDue, c);
        c.gridx=4; controls.add(new JLabel("Notify (min ahead):"), c);
        c.gridx=5; controls.add(spinnerNotifyAhead, c);

        // Row 2 - action buttons
        c.gridx=0; c.gridy=2; controls.add(btnAdd, c);
        c.gridx=1; controls.add(btnEdit, c);
        c.gridx=2; controls.add(btnDelete, c);
        c.gridx=3; controls.add(btnDone, c);
        c.gridx=4; controls.add(btnSave, c);
        c.gridx=5; controls.add(btnLoad, c);

        // Middle: search + filter + dark mode toggle
        JPanel midPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,10,8));
        midPanel.setBackground(bg);

        tfSearch = new JTextField(22);
        tfSearch.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tfSearch.setToolTipText("Search tasks live...");

        cbFilter = new JComboBox<>(new String[]{"All","Pending","Done","High","Medium","Low"});
        cbFilter.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        cbDarkMode = new JCheckBox("Dark Mode");
        cbDarkMode.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        cbDarkMode.setBackground(bg);

        midPanel.add(new JLabel("Search:"));
        midPanel.add(tfSearch);
        midPanel.add(new JLabel("Filter:"));
        midPanel.add(cbFilter);
        midPanel.add(cbDarkMode);

        // Table setup
        String[] cols = {"Task","Status","Priority","Due"};
        model = new DefaultTableModel(cols,0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        table.setRowHeight(34);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new TaskCellRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new EmptyBorder(10,16,10,16));

        // Status bar
        status = new JLabel(" Ready");
        status.setOpaque(true);
        status.setBackground(statusBarColor);
        status.setForeground(Color.WHITE);
        status.setBorder(new EmptyBorder(8,12,8,12));

        
        JPanel centerStack = new JPanel();
        centerStack.setLayout(new BoxLayout(centerStack, BoxLayout.Y_AXIS));
        centerStack.setBackground(bg);

        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerStack.add(controls);

        centerStack.add(Box.createRigidArea(new Dimension(0, 12)));

        midPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerStack.add(midPanel);

        centerStack.add(Box.createRigidArea(new Dimension(0, 12)));

        JPanel tableWrapper = new JPanel(new BorderLayout());
        tableWrapper.setBackground(bg);
        tableWrapper.setBorder(new EmptyBorder(10,16,10,16));
        tableWrapper.add(scroll, BorderLayout.CENTER);
        tableWrapper.setPreferredSize(new Dimension(920, 380));

        centerStack.add(tableWrapper);

        // Add components to frame
        add(header, BorderLayout.NORTH);
        add(centerStack, BorderLayout.CENTER);
        add(status, BorderLayout.PAGE_END);

        // Action listeners
        btnAdd.addActionListener(e -> addTask());
        btnEdit.addActionListener(e -> editTask());
        btnDelete.addActionListener(e -> deleteTask());
        btnDone.addActionListener(e -> markDone());
        btnSave.addActionListener(e -> saveTasks());
        btnLoad.addActionListener(e -> loadTasks());

        // Live search & filter listeners
        tfSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilters(); }
            public void removeUpdate(DocumentEvent e) { applyFilters(); }
            public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });
        cbFilter.addActionListener(e -> applyFilters());
        cbDarkMode.addActionListener(e -> toggleDarkMode(cbDarkMode.isSelected()));

        
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount()==2) {
                    int r = table.getSelectedRow();
                    if (r>=0) {
                        tfTask.setText((String)model.getValueAt(r,0));
                        cbPriority.setSelectedItem((String)model.getValueAt(r,2));
                        String due = (String)model.getValueAt(r,3);
                        try { Date d = dtFormat.parse(due); spinnerDue.setValue(d); } catch (Exception ex) {}
                    }
                }
            }
        });
    }

    // UI help
    private JButton gradientButton(String text, Color a, Color b) {
        JButton btn = new JButton(text) {
            private boolean hover=false;
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                int w=getWidth(), h=getHeight();
                Color start = hover ? a.brighter() : a;
                Color end = hover ? b.brighter() : b;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0,0,start,w,h,end);
                g2.setPaint(gp);
                g2.fillRoundRect(0,0,w,h,16,16);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(getText()))/2;
                int ty = (h - fm.getHeight())/2 + fm.getAscent();
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
            {
                setContentAreaFilled(false);
                setFocusPainted(false);
                setBorderPainted(false);
                setForeground(Color.WHITE);
                setFont(new Font("Segoe UI", Font.BOLD, 13));
                setPreferredSize(new Dimension(140,38));
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e){ hover=true; repaint(); }
                    public void mouseExited(MouseEvent e){ hover=false; repaint(); }
                });
            }
        };
        return btn;
    }

    
    private class TaskCellRenderer extends DefaultTableCellRenderer {
        private final Color high = new Color(255, 230, 230);
        private final Color medium = new Color(245, 245, 235);
        private final Color low = new Color(230, 255, 240);
        private final Color doneBg = new Color(225, 240, 255);

        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String statusVal = (String)model.getValueAt(row,1);
            String priorityVal = (String)model.getValueAt(row,2);

            Color bgc = Color.WHITE;
            if ("✔ Done".equals(statusVal)) bgc = doneBg;
            else {
                if ("High".equalsIgnoreCase(priorityVal)) bgc = high;
                else if ("Medium".equalsIgnoreCase(priorityVal)) bgc = medium;
                else if ("Low".equalsIgnoreCase(priorityVal)) bgc = low;
            }

            if (isSelected) {
                comp.setBackground(new Color(180,210,255));
                comp.setForeground(Color.BLACK);
            } else {
                comp.setBackground(bgc);
                comp.setForeground(Color.BLACK);
            }

            // overdue highlight: red text if overdue and not done
            try {
                String dueStr = (String)model.getValueAt(row,3);
                Date due = dtFormat.parse(dueStr);
                if (due.before(new Date()) && !"✔ Done".equals(statusVal)) {
                    comp.setForeground(Color.RED.darker());
                }
            } catch (Exception ex) { }

            return comp;
        }
    }

    // ---------- Application logic ----------
    private void addTask() {
        String task = tfTask.getText().trim();
        if (task.isEmpty()) { showMsg("Enter a task description"); return; }
        String priority = (String)cbPriority.getSelectedItem();
        Date due = (Date)spinnerDue.getValue();
        String dueStr = dtFormat.format(due);
        model.addRow(new String[]{task, "Pending", priority, dueStr});
        tfTask.setText("");
        status("Task added");
        applyFilters();
    }

    private void editTask() {
        int r = table.getSelectedRow();
        if (r<0) { showMsg("Select task to edit"); return; }
        String task = tfTask.getText().trim();
        if (task.isEmpty()) { showMsg("Task cannot be empty"); return; }
        String priority = (String)cbPriority.getSelectedItem();
        Date due = (Date)spinnerDue.getValue();
        String dueStr = dtFormat.format(due);
        model.setValueAt(task, r, 0);
        model.setValueAt(priority, r, 2);
        model.setValueAt(dueStr, r, 3);
        status("Task updated");
        applyFilters();
    }

    private void deleteTask() {
        int r = table.getSelectedRow();
        if (r<0) { showMsg("Select task to delete"); return; }
        int opt = JOptionPane.showConfirmDialog(this, "Delete selected task?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (opt==JOptionPane.YES_OPTION) {
            model.removeRow(r);
            status("Task deleted");
            applyFilters();
        }
    }

    private void markDone() {
        int r = table.getSelectedRow();
        if (r<0) { showMsg("Select task to mark done"); return; }
        model.setValueAt("✔ Done", r, 1);
        status("Task marked done");
        applyFilters();
    }

    private void saveTasks() {
        try (BufferedWriter w = Files.newBufferedWriter(saveFile)) {
            w.write("task,status,priority,due\n");
            for (int i=0;i<model.getRowCount();i++) {
                String task = escapeCSV((String)model.getValueAt(i,0));
                String statusVal = escapeCSV((String)model.getValueAt(i,1));
                String pr = escapeCSV((String)model.getValueAt(i,2));
                String due = escapeCSV((String)model.getValueAt(i,3));
                w.write(String.join(",", task, statusVal, pr, due));
                w.write("\n");
            }
            status("Saved to " + saveFile.getFileName());
        } catch (IOException ex) {
            ex.printStackTrace();
            showMsg("Save failed: " + ex.getMessage());
        }
    }

    private void loadTasks() {
        model.setRowCount(0);
        if (!Files.exists(saveFile)) {
            status("No saved file (starting fresh)");
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(saveFile)) {
            String header = r.readLine(); // skip
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = parseCSVLine(line);
                if (parts.length >= 4) {
                    model.addRow(new String[]{parts[0], parts[1], parts[2], parts[3]});
                }
            }
            status("Loaded " + model.getRowCount() + " tasks");
            applyFilters();
        } catch (IOException ex) {
            ex.printStackTrace();
            showMsg("Load failed: " + ex.getMessage());
        }
    }

    // ------------ Filtering & Search --------------
    private void applyFilters() {
        String q = tfSearch.getText().trim().toLowerCase();
        String filter = (String)cbFilter.getSelectedItem();

        TableRowSorter<TableModel> sorter = new TableRowSorter<>(model);
        List<RowFilter<Object,Object>> filters = new ArrayList<>();

        if (!q.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(q), 0)); // search in task column
        }

        if (filter != null && !"All".equals(filter)) {
            if ("Pending".equals(filter)) {
                filters.add(RowFilter.regexFilter("^Pending$", 1));
            } else if ("Done".equals(filter)) {
                filters.add(RowFilter.regexFilter("^✔ Done$", 1));
            } else if ("High".equals(filter) || "Medium".equals(filter) || "Low".equals(filter)) {
                filters.add(RowFilter.regexFilter("^" + filter + "$", 2));
            }
        }

        RowFilter<Object,Object> rf = filters.isEmpty() ? null : RowFilter.andFilter(filters);
        sorter.setRowFilter(rf);
        table.setRowSorter(sorter);
    }

    // ------------ Notifications --------------
    private void startNotificationChecker() {
        notifyTimer = new Timer(true);
        notifyTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() { checkForNotifications(); }
        }, 30_000, 30_000); // run every 30 seconds (first run after 30s)
    }

    private void checkForNotifications() {
        SwingUtilities.invokeLater(() -> {
            int aheadMinutes = (Integer)spinnerNotifyAhead.getValue();
            Date now = new Date();
            Date until = new Date(now.getTime() + aheadMinutes * 60L * 1000L);
            for (int i=0;i<model.getRowCount();i++) {
                String statusVal = (String)model.getValueAt(i,1);
                if ("✔ Done".equals(statusVal)) continue;
                String dueStr = (String)model.getValueAt(i,3);
                try {
                    Date due = dtFormat.parse(dueStr);
                    if (due.before(now)) {
                        String task = (String)model.getValueAt(i,0);
                        showNotification("Overdue: " + task, "Task is overdue since " + dueStr);
                    } else if (!due.after(until)) {
                        String task = (String)model.getValueAt(i,0);
                        showNotification("Upcoming: " + task, "Due at " + dueStr);
                    }
                } catch (ParseException ex) { }
            }
        });
    }

    private void showNotification(String title, String message) {
        JOptionPane optionPane = new JOptionPane(message, JOptionPane.INFORMATION_MESSAGE);
        JDialog dialog = optionPane.createDialog(this, title);
        dialog.setModal(false);
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);
        new Timer().schedule(new TimerTask() {
            public void run() { dialog.dispose(); }
        }, 6000);
    }

    // ------------- Utilities ---------------
    private void toggleDarkMode(boolean dark) {
        if (dark) {
            bg = new Color(34,34,38);
            headerStart = new Color(58, 0, 105);
            headerEnd = new Color(120, 20, 160);
            statusBarColor = new Color(80, 0, 110);
            getContentPane().setBackground(bg);
            status.setBackground(statusBarColor);
            status.setForeground(Color.WHITE);
            table.setBackground(new Color(48,48,52));
            table.setForeground(Color.WHITE);
            table.getTableHeader().setForeground(Color.WHITE);
            table.getTableHeader().setBackground(new Color(60,60,64));
        } else {
            bg = new Color(250,245,255);
            headerStart = new Color(190, 0, 255);
            headerEnd = new Color(255, 0, 150);
            statusBarColor = new Color(190,0,255);
            getContentPane().setBackground(bg);
            status.setBackground(statusBarColor);
            status.setForeground(Color.WHITE);
            table.setBackground(Color.WHITE);
            table.setForeground(Color.BLACK);
            table.getTableHeader().setForeground(Color.BLACK);
            table.getTableHeader().setBackground(null);
        }
        SwingUtilities.updateComponentTreeUI(this);
    }

    private void showMsg(String m){ JOptionPane.showMessageDialog(this, m); }
    private void status(String s){ status.setText(" " + s); }

    private String escapeCSV(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }

    private String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i=0;i<line.length();i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i+1 < line.length() && line.charAt(i+1) == '"') {
                        cur.append('"'); i++;
                    } else {
                        inQuotes = false;
                    }
                } else cur.append(ch);
            } else {
                if (ch == '"') inQuotes = true;
                else if (ch == ',') { fields.add(cur.toString()); cur.setLength(0); }
                else cur.append(ch);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    // ------------ Entry point ---------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            TodoApp app = new TodoApp();
            app.setVisible(true);
        });
    }
}
