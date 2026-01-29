import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class SmartSupermarketBillingWithLogin extends JFrame {

    // UI fields
    private JTextField itemNameField, quantityField, customerNameField, discountField, taxField;
    private JTextField barcodeField;
    private JLabel totalLabel, totalSalesLabel, billNoLabel, loggedInLabel;
    private JTable billTable;
    private DefaultTableModel tableModel;

    // Data structures
    private Map<String, Double> priceList = new HashMap<>();
    private Map<String, Integer> stockList = new HashMap<>();
    private Map<String, String> barcodeToItem = new HashMap<>(); // barcode -> item name

    private java.util.List<String> allBills = new ArrayList<>();

    private double totalBill = 0.0;
    private double totalSales = 0.0;
    private int billCounter = 1;

    // Persistence files
    private final String BILL_FILE = "bills.txt";
    private final String STOCK_FILE = "stock.dat";
    private final String USERS_FILE = "users.dat";

    // User management
    private Map<String, User> users = new HashMap<>(); // username -> User
    private User loggedInUser = null;

    // Roles
    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_CASHIER = "cashier";

    public SmartSupermarketBillingWithLogin() {
        // load persisted data
        loadUsers();
        loadStockFromFile();
        loadBillsFromFile();

        // show login dialog (blocks until successful or exit)
        boolean ok = showLoginDialog();
        if (!ok) {
            // user cancelled/closed login -> exit
            System.exit(0);
        }

        // Frame Setup
        setTitle("🛒 Smart Supermarket Billing System (with Login & Barcode)");
        setSize(1100, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}

        // Input Panel
        JPanel inputPanel = new JPanel(new GridLayout(4, 6, 8, 8));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Add Item / Customer"));

        customerNameField = new JTextField();
        discountField = new JTextField("0");
        taxField = new JTextField("0");

        itemNameField = new JTextField();
        quantityField = new JTextField("1");
        barcodeField = new JTextField();

        inputPanel.add(new JLabel("Customer Name:"));
        inputPanel.add(customerNameField);
        inputPanel.add(new JLabel("Bill No:"));
        billNoLabel = new JLabel(generateBillNo());
        inputPanel.add(billNoLabel);
        inputPanel.add(new JLabel("Logged In:"));
        loggedInLabel = new JLabel(); // set later
        inputPanel.add(loggedInLabel);

        inputPanel.add(new JLabel("Item Name:"));
        inputPanel.add(itemNameField);
        inputPanel.add(new JLabel("Quantity:"));
        inputPanel.add(quantityField);
        inputPanel.add(new JLabel("Barcode:"));
        inputPanel.add(barcodeField);

        inputPanel.add(new JLabel("Discount (%):"));
        inputPanel.add(discountField);
        inputPanel.add(new JLabel("GST Tax (%):"));
        inputPanel.add(taxField);

        JButton addButton = new JButton(" Add Item (Enter)");
        JButton clearButton = new JButton(" New Bill");
        JButton removeButton = new JButton(" Remove Selected");
        JButton searchButton = new JButton(" Search Item");

        inputPanel.add(addButton);
        inputPanel.add(clearButton);
        inputPanel.add(removeButton);
        inputPanel.add(searchButton);

        // Table
        String[] columns = {"Item", "Qty", "Price", "Total"};
        tableModel = new DefaultTableModel(columns, 0);
        billTable = new JTable(tableModel);
        billTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        billTable.setRowHeight(25);
        JScrollPane scrollPane = new JScrollPane(billTable);

        // Side Panel buttons
        JPanel sidePanel = new JPanel(new GridLayout(9, 1, 10, 10));
        sidePanel.setBorder(BorderFactory.createTitledBorder("Options"));

        JButton stockButton = new JButton("Edit Stock");
        JButton checkStockButton = new JButton(" Check Stock");
        JButton printButton = new JButton(" Print Receipt");
        JButton viewBillsButton = new JButton(" View All Bills");
        JButton saveButton = new JButton(" Save Bills");
        JButton loadButton = new JButton(" Load Bills");
        JButton manageUsersButton = new JButton(" Manage Users");
        JButton saveStockButton = new JButton(" Save Stock");
        JButton exitButton = new JButton(" Exit");

        sidePanel.add(stockButton);
        sidePanel.add(checkStockButton);
        sidePanel.add(printButton);
        sidePanel.add(viewBillsButton);
        sidePanel.add(saveButton);
        sidePanel.add(loadButton);
        sidePanel.add(manageUsersButton);
        sidePanel.add(saveStockButton);
        sidePanel.add(exitButton);

        // Bottom Panel
        JPanel bottomPanel = new JPanel(new GridLayout(2, 1));
        totalLabel = new JLabel("Bill Total: ₹0.00", SwingConstants.CENTER);
        totalLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        totalSalesLabel = new JLabel("Total Sales: ₹0.00", SwingConstants.CENTER);
        totalSalesLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        bottomPanel.add(totalLabel);
        bottomPanel.add(totalSalesLabel);

        // Add Panels to frame
        add(inputPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(sidePanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // Setup defaults if empty
        ensureDefaultStock();

        // Action wiring
        addButton.addActionListener(e -> addItem());
        clearButton.addActionListener(e -> newBill());
        removeButton.addActionListener(e -> removeSelectedItem());
        checkStockButton.addActionListener(e -> showStock());
        stockButton.addActionListener(e -> {
            if (!isAdmin()) {
                JOptionPane.showMessageDialog(this, "Only admin can edit stock.");
                return;
            }
            editStock();
        });
        printButton.addActionListener(e -> printReceipt());
        viewBillsButton.addActionListener(e -> viewAllBills());
        saveButton.addActionListener(e -> saveBillsToFile());
        loadButton.addActionListener(e -> loadBillsFromFile());
        exitButton.addActionListener(e -> System.exit(0));
        manageUsersButton.addActionListener(e -> {
            if (!isAdmin()) {
                JOptionPane.showMessageDialog(this, "Only admin can manage users.");
                return;
            }
            manageUsersDialog();
        });
        saveStockButton.addActionListener(e -> saveStockToFile());

        searchButton.addActionListener(e -> searchItem());

        // Enter Key Support for item fields
        KeyAdapter enterKeyHandler = new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) addItem();
            }
        };
        itemNameField.addKeyListener(enterKeyHandler);
        quantityField.addKeyListener(enterKeyHandler);

        // Barcode field: when ENTER pressed, lookup barcode and auto-add quantity 1
        barcodeField.addActionListener(e -> barcodeScanned());

        // update UI labels for logged-in user
        updateLoggedInLabel();

        // Show frame
        getContentPane().setBackground(new Color(245, 248, 255));
        setVisible(true);
    }

    // ---------------------------
    // Login & User Management
    // ---------------------------
    private boolean showLoginDialog() {
        // If no users exist, create default admin
        if (users.isEmpty()) {
            User admin = new User("admin", "admin123", ROLE_ADMIN);
            users.put(admin.username, admin);
            saveUsers();
            JOptionPane.showMessageDialog(null,
                    "No users found. A default admin account is created:\nusername: admin\npassword: admin123\nPlease change it after login.");
        }

        JDialog dialog = new JDialog((Frame) null, "Login", true);
        dialog.setSize(420, 220);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(new JLabel("Password:"));
        panel.add(passwordField);

        dialog.add(panel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout());
        JButton loginBtn = new JButton("Login");
        JButton cancelBtn = new JButton("Cancel");
        btnPanel.add(loginBtn);
        btnPanel.add(cancelBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        final boolean[] success = {false};

        loginBtn.addActionListener(e -> {
            String user = usernameField.getText().trim();
            String pass = new String(passwordField.getPassword());
            if (authenticate(user, pass)) {
                success[0] = true;
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(dialog, "Invalid credentials!");
            }
        });

        cancelBtn.addActionListener(e -> {
            success[0] = false;
            dialog.dispose();
        });

        dialog.getRootPane().setDefaultButton(loginBtn);
        dialog.setVisible(true);

        return success[0];
    }

    private void manageUsersDialog() {
        JDialog dialog = new JDialog(this, "Manage Users", true);
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(8, 8));

        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> userList = new JList<>(listModel);
        refreshUserListModel(listModel);

        dialog.add(new JScrollPane(userList), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(1, 4, 8, 8));
        JButton addUserBtn = new JButton("Add User");
        JButton deleteUserBtn = new JButton("Delete User");
        JButton changePassBtn = new JButton("Change Password");
        JButton closeBtn = new JButton("Close");
        btnPanel.add(addUserBtn);
        btnPanel.add(deleteUserBtn);
        btnPanel.add(changePassBtn);
        btnPanel.add(closeBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        addUserBtn.addActionListener(e -> {
            JPanel p = new JPanel(new GridLayout(3, 2, 8, 8));
            JTextField uname = new JTextField();
            JPasswordField pass = new JPasswordField();
            JComboBox<String> role = new JComboBox<>(new String[]{ROLE_CASHIER, ROLE_ADMIN});
            p.add(new JLabel("Username:"));
            p.add(uname);
            p.add(new JLabel("Password:"));
            p.add(pass);
            p.add(new JLabel("Role:"));
            p.add(role);
            int res = JOptionPane.showConfirmDialog(dialog, p, "Add User", JOptionPane.OK_CANCEL_OPTION);
            if (res == JOptionPane.OK_OPTION) {
                String u = uname.getText().trim();
                String pw = new String(pass.getPassword());
                String r = (String) role.getSelectedItem();
                if (u.isEmpty() || pw.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "Enter username and password.");
                } else {
                    if (users.containsKey(u)) {
                        JOptionPane.showMessageDialog(dialog, "User already exists.");
                    } else {
                        users.put(u, new User(u, pw, r));
                        saveUsers();
                        refreshUserListModel(listModel);
                        JOptionPane.showMessageDialog(dialog, "User added.");
                    }
                }
            }
        });

        deleteUserBtn.addActionListener(e -> {
            String selected = userList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(dialog, "Select a user.");
                return;
            }
            if (selected.equals(loggedInUser.username)) {
                JOptionPane.showMessageDialog(dialog, "You cannot delete the logged-in user.");
                return;
            }
            int r = JOptionPane.showConfirmDialog(dialog, "Delete user " + selected + "?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) {
                users.remove(selected);
                saveUsers();
                refreshUserListModel(listModel);
            }
        });

        changePassBtn.addActionListener(e -> {
            String selected = userList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(dialog, "Select a user.");
                return;
            }
            JPasswordField pf = new JPasswordField();
            int r = JOptionPane.showConfirmDialog(dialog, pf, "New password for " + selected, JOptionPane.OK_CANCEL_OPTION);
            if (r == JOptionPane.OK_OPTION) {
                String np = new String(pf.getPassword());
                if (np.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "Password cannot be empty.");
                } else {
                    users.get(selected).password = np;
                    saveUsers();
                    JOptionPane.showMessageDialog(dialog, "Password changed.");
                }
            }
        });

        closeBtn.addActionListener(e -> dialog.dispose());

        dialog.setVisible(true);
    }

    private void refreshUserListModel(DefaultListModel<String> model) {
        model.clear();
        for (String uname : users.keySet()) model.addElement(uname);
    }

    private boolean authenticate(String username, String password) {
        if (!users.containsKey(username)) return false;
        User u = users.get(username);
        if (u.password.equals(password)) {
            loggedInUser = u;
            return true;
        }
        return false;
    }

    private void updateLoggedInLabel() {
        if (loggedInUser != null) {
            loggedInLabel.setText(loggedInUser.username + " (" + loggedInUser.role + ")");
        } else {
            loggedInLabel.setText("Not logged in");
        }
    }

    private boolean isAdmin() {
        return loggedInUser != null && ROLE_ADMIN.equals(loggedInUser.role);
    }

    // ---------------------------
    // Barcode handling
    // ---------------------------
    private void barcodeScanned() {
        String bc = barcodeField.getText().trim();
        if (bc.isEmpty()) return;
        if (!barcodeToItem.containsKey(bc)) {
            JOptionPane.showMessageDialog(this, "Barcode not found in system.");
            barcodeField.setText("");
            return;
        }
        String item = barcodeToItem.get(bc);
        // auto add quantity 1
        itemNameField.setText(item);
        quantityField.setText("1");
        addItem();
        // clear barcode field to accept next scan quickly
        barcodeField.setText("");
        barcodeField.requestFocusInWindow();
    }

    // ---------------------------
    // Billing logic
    // ---------------------------
    private String generateBillNo() {
        return String.format("BILL-%04d", billCounter);
    }

    private void addItem() {
        String item = itemNameField.getText().trim();
        String qtyText = quantityField.getText().trim();

        if (item.isEmpty() || qtyText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter item and quantity!");
            return;
        }

        if (!priceList.containsKey(item) || !stockList.containsKey(item)) {
            JOptionPane.showMessageDialog(this, "Item not found in stock!");
            return;
        }

        int qty;
        try {
            qty = Integer.parseInt(qtyText);
            if (qty <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid quantity.");
            return;
        }

        int available = stockList.get(item);

        if (qty > available) {
            JOptionPane.showMessageDialog(this, "Insufficient stock! Only " + available + " left.");
            return;
        }

        double price = priceList.get(item);
        double total = price * qty;
        totalBill += total;
        stockList.put(item, available - qty);

        tableModel.addRow(new Object[]{item, qty, "₹" + String.format("%.2f", price), "₹" + String.format("%.2f", total)});
        totalLabel.setText("Bill Total: ₹" + String.format("%.2f", totalBill));

        itemNameField.setText("");
        quantityField.setText("1");
        itemNameField.requestFocus();
    }

    private void removeSelectedItem() {
        int row = billTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Select an item to remove!");
            return;
        }

        String item = tableModel.getValueAt(row, 0).toString();
        int qty = Integer.parseInt(tableModel.getValueAt(row, 1).toString());
        double price = priceList.get(item);

        // restore stock
        stockList.put(item, stockList.get(item) + qty);

        // update total
        double itemTotal = price * qty;
        totalBill -= itemTotal;
        if (totalBill < 0) totalBill = 0;
        totalLabel.setText("Bill Total: ₹" + String.format("%.2f", totalBill));

        tableModel.removeRow(row);
    }

    private void showStock() {
        StringBuilder sb = new StringBuilder(" Current Stock:\n\n");
        for (String item : stockList.keySet()) {
            String bc = findBarcodeForItem(item);
            sb.append(String.format("%-12s : %4d pcs (₹%.2f each)  Barcode: %s\n",
                    item, stockList.get(item), priceList.getOrDefault(item, 0.0), (bc == null ? "-" : bc)));
        }
        JTextArea area = new JTextArea(sb.toString());
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setEditable(false);
        JScrollPane sp = new JScrollPane(area);
        sp.setPreferredSize(new Dimension(700, 400));
        JOptionPane.showMessageDialog(this, sp, "Stock Details", JOptionPane.INFORMATION_MESSAGE);
    }

    private String findBarcodeForItem(String item) {
        for (Map.Entry<String, String> e : barcodeToItem.entrySet()) {
            if (e.getValue().equals(item)) return e.getKey();
        }
        return null;
    }

    private void editStock() {
        // multi-field dialog: item, qty, price, barcode
        JTextField itemField = new JTextField();
        JTextField qtyField = new JTextField();
        JTextField priceField = new JTextField();
        JTextField barcodeFieldInput = new JTextField();

        Object[] fields = {
                "Item Name:", itemField,
                "Quantity:", qtyField,
                "Price (₹):", priceField,
                "Barcode (optional):", barcodeFieldInput
        };
        int result = JOptionPane.showConfirmDialog(this, fields, "Edit/Add Stock", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
                String item = itemField.getText().trim();
                if (item.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Item name is required.");
                    return;
                }
                int qty = Integer.parseInt(qtyField.getText().trim());
                double price = Double.parseDouble(priceField.getText().trim());
                String bc = barcodeFieldInput.getText().trim();

                // If barcode provided and maps to another item, alert/confirm
                if (!bc.isEmpty()) {
                    String existing = barcodeToItem.get(bc);
                    if (existing != null && !existing.equals(item)) {
                        int r = JOptionPane.showConfirmDialog(this, "Barcode already assigned to '" + existing + "'. Overwrite?", "Confirm", JOptionPane.YES_NO_OPTION);
                        if (r != JOptionPane.YES_OPTION) return;
                    }
                    barcodeToItem.put(bc, item);
                }

                stockList.put(item, qty);
                priceList.put(item, price);
                JOptionPane.showMessageDialog(this, " Stock updated for " + item);

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid number format for qty/price.");
            }
        }
    }

    private void printReceipt() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No items in bill!");
            return;
        }

        String customer = customerNameField.getText().trim();
        if (customer.isEmpty()) customer = "Guest";

        double discount;
        double tax;
        try {
            discount = Double.parseDouble(discountField.getText().trim());
            tax = Double.parseDouble(taxField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid discount or tax values.");
            return;
        }

        double discountedTotal = totalBill - (totalBill * discount / 100);
        double finalTotal = discountedTotal + (discountedTotal * tax / 100);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
        StringBuilder receipt = new StringBuilder();
        receipt.append("🧾 Supermarket Bill\n---------------------------------\n");
        receipt.append("Bill No: ").append(billNoLabel.getText()).append("\n");
        receipt.append("Customer: ").append(customer).append("\n");
        receipt.append("Cashier: ").append(loggedInUser.username).append("\n");
        receipt.append("Date: ").append(timestamp).append("\n\n");

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            receipt.append(tableModel.getValueAt(i, 0)).append(" x ")
                    .append(tableModel.getValueAt(i, 1)).append(" = ")
                    .append(tableModel.getValueAt(i, 3)).append("\n");
        }

        receipt.append("---------------------------------\n");
        receipt.append(String.format("Subtotal: ₹%.2f\n", totalBill));
        receipt.append(String.format("Discount: %.1f%%\n", discount));
        receipt.append(String.format("GST: %.1f%%\n", tax));
        receipt.append(String.format("Total: ₹%.2f\n", finalTotal));
        receipt.append("---------------------------------\nThank You! Visit Again!\n\n");

        allBills.add(receipt.toString());

        JTextArea area = new JTextArea(receipt.toString());
        area.setEditable(false);
        area.setFont(new Font("Consolas", Font.PLAIN, 13));
        JOptionPane.showMessageDialog(this, new JScrollPane(area), " Receipt", JOptionPane.INFORMATION_MESSAGE);

        totalSales += finalTotal;
        totalSalesLabel.setText("Total Sales: ₹" + String.format("%.2f", totalSales));

        // persist bill immediately
        saveBillsToFile();

        // Prepare new bill
        newBill();
    }

    private void viewAllBills() {
        if (allBills.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No bills recorded yet.");
            return;
        }
        JTextArea area = new JTextArea();
        int i = 1;
        for (String bill : allBills) area.append("🧾 BILL #" + i++ + "\n" + bill + "\n");
        area.setEditable(false);
        area.setFont(new Font("Consolas", Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(800, 500));
        JOptionPane.showMessageDialog(this, scroll, "📑 All Bills", JOptionPane.INFORMATION_MESSAGE);
    }

    private void saveBillsToFile() {
        try (FileWriter writer = new FileWriter(BILL_FILE)) {
            for (String bill : allBills) writer.write(bill + "###END###\n");
            JOptionPane.showMessageDialog(this, " Bills saved successfully!");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error saving bills: " + e.getMessage());
        }
    }

    private void loadBillsFromFile() {
        File file = new File(BILL_FILE);
        if (!file.exists()) return;

        try (Scanner sc = new Scanner(file)) {
            StringBuilder bill = new StringBuilder();
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.equals("###END###")) {
                    allBills.add(bill.toString());
                    bill.setLength(0);
                } else {
                    bill.append(line).append("\n");
                }
            }
            // no alert on load to avoid popup at startup
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error loading bills!");
        }
    }

    private void newBill() {
        totalBill = 0.0;
        tableModel.setRowCount(0);
        totalLabel.setText("Bill Total: ₹0.00");
        customerNameField.setText("");
        discountField.setText("0");
        taxField.setText("0");
        billCounter++;
        billNoLabel.setText(generateBillNo());
        // no popup to keep workflow fast
    }

    private void searchItem() {
        String name = JOptionPane.showInputDialog(this, "Enter item name to search:");
        if (name == null) return;

        if (stockList.containsKey(name)) {
            JOptionPane.showMessageDialog(this,
                    name + " - Stock: " + stockList.get(name) + " Price: ₹" + priceList.get(name) +
                            " Barcode: " + (findBarcodeForItem(name) == null ? "-" : findBarcodeForItem(name)));
        } else {
            JOptionPane.showMessageDialog(this, "Item not found!");
        }
    }

    // ---------------------------
    // Persistence: stock & users
    // ---------------------------
    private void saveStockToFile() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(STOCK_FILE))) {
            out.writeObject(stockList);
            out.writeObject(priceList);
            out.writeObject(barcodeToItem);
            JOptionPane.showMessageDialog(this, " Stock saved to " + STOCK_FILE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error saving stock: " + e.getMessage());
        }
    }

    private void loadStockFromFile() {
        File f = new File(STOCK_FILE);
        if (!f.exists()) return;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            Object s = in.readObject();
            if (s instanceof Map) {
                //noinspection unchecked
                stockList = (Map<String, Integer>) s;
            }
            Object p = in.readObject();
            if (p instanceof Map) {
                //noinspection unchecked
                priceList = (Map<String, Double>) p;
            }
            Object b = in.readObject();
            if (b instanceof Map) {
                //noinspection unchecked
                barcodeToItem = (Map<String, String>) b;
            }
        } catch (Exception e) {
            // ignore; we'll use default stock
        }
    }

    private void saveUsers() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(USERS_FILE))) {
            out.writeObject(users);
        } catch (Exception e) {
            // non-fatal; show message
            JOptionPane.showMessageDialog(this, "Error saving users: " + e.getMessage());
        }
    }

    private void loadUsers() {
        File f = new File(USERS_FILE);
        if (!f.exists()) return;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            Object o = in.readObject();
            if (o instanceof Map) {
                //noinspection unchecked
                users = (Map<String, User>) o;
            }
        } catch (Exception e) {
            // ignore
        }
    }

    // ---------------------------
    // Helpers & defaults
    // ---------------------------
    private void ensureDefaultStock() {
        if (priceList.isEmpty() || stockList.isEmpty()) {
            priceList.putIfAbsent("Apple", 50.0);
            priceList.putIfAbsent("Banana", 20.0);
            priceList.putIfAbsent("Milk", 30.0);
            priceList.putIfAbsent("Bread", 25.0);
            priceList.putIfAbsent("Soap", 40.0);

            stockList.putIfAbsent("Apple", 20);
            stockList.putIfAbsent("Banana", 50);
            stockList.putIfAbsent("Milk", 30);
            stockList.putIfAbsent("Bread", 25);
            stockList.putIfAbsent("Soap", 40);

            // default barcodes (optional)
            barcodeToItem.putIfAbsent("111000111", "Apple");
            barcodeToItem.putIfAbsent("111000112", "Banana");
            barcodeToItem.putIfAbsent("111000113", "Milk");
        }
    }

    // ---------------------------
    // User class for simple persistence
    // ---------------------------
    private static class User implements Serializable {
        String username;
        String password; // plaintext for simplicity here. Replace with hashed in production.
        String role;

        User(String username, String password, String role) {
            this.username = username;
            this.password = password;
            this.role = role;
        }
    }

    // ---------------------------
    // Main
    // ---------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SmartSupermarketBillingWithLogin app = new SmartSupermarketBillingWithLogin();
            app.getContentPane().setBackground(new Color(245, 248, 255));
        });
    }
}
