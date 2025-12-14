import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class AuctionDashboard {
    private final OnlineAuctionSystem.AuctionService service = new OnlineAuctionSystem.AuctionService();
    private OnlineAuctionSystem.User currentUser = null;

    private final JFrame frame = new JFrame("Auction Dashboard");
    // Theme colors (exposed to LoginWindow)
    private final Color THEME_BG = new Color(250, 252, 255);
    private final Color THEME_PRIMARY = new Color(41, 121, 255);
    private final Color THEME_ACCENT = new Color(255, 99, 71);
    private final Color THEME_HEADER_BG = new Color(30, 40, 60);
    private final DefaultTableModel tableModel = new DefaultTableModel(new String[]{"ID","Title","Owner","Current","Ends","Status"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable auctionTable = new JTable(tableModel);
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    // Announcement log model and table (right-side pane)
    private final DefaultTableModel logModel = new DefaultTableModel(new String[]{"Time","Announcement"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable logTable = new JTable(logModel);
    // Pagination and current dataset
    private java.util.List<OnlineAuctionSystem.Auction> currentList = new java.util.ArrayList<>();
    private int pageSize = 50;
    private int currentPage = 1;
    private JButton prevBtn, nextBtn;
    private JLabel pageLabel;
    private JComboBox<Integer> pageSizeCombo;

    public AuctionDashboard() {
        seedDemoData();
        initUI();
        // Show a dedicated login window (separate screen) before revealing the dashboard
        while (currentUser == null) {
            LoginWindow lw = new LoginWindow(frame);
            lw.setVisible(true); // blocks until disposed
            if (currentUser == null) {
                int c = JOptionPane.showConfirmDialog(null, "No login provided. Exit application?", "Exit?", JOptionPane.YES_NO_OPTION);
                if (c == JOptionPane.YES_OPTION) System.exit(0);
                // otherwise loop and show login again
            }
        }

        refreshTable();
        frame.setVisible(true);
    }

    private void initUI() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(980, 640);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(THEME_BG);

        JPanel top = new JPanel(new BorderLayout(8,8));
        top.setBackground(THEME_PRIMARY);
        top.setBorder(BorderFactory.createEmptyBorder(10,12,10,12));
        JLabel title = new JLabel("  Online Auction Dashboard");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(Color.white);
        // small accent dot to the left
        title.setIcon(new Icon() {
            public int getIconWidth(){ return 12; }
            public int getIconHeight(){ return 12; }
            public void paintIcon(Component c, Graphics g, int x, int y){
                g.setColor(THEME_ACCENT);
                g.fillOval(x, y, 12, 12);
            }
        });
        title.setIconTextGap(10);
        top.add(title, BorderLayout.WEST);

        JButton loginBtn = new JButton("Login/Register");
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setEnabled(false);
        JPanel auth = new JPanel();
        auth.add(loginBtn);
        auth.add(logoutBtn);
        auth.setOpaque(false);
        top.add(auth, BorderLayout.EAST);

        frame.add(top, BorderLayout.NORTH);

        auctionTable.setFillsViewportHeight(true);
        auctionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        auctionTable.setRowHeight(28);
        auctionTable.setAutoCreateRowSorter(true);
        // Header style
        auctionTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        auctionTable.getTableHeader().setBackground(THEME_HEADER_BG);
        auctionTable.getTableHeader().setForeground(Color.WHITE);
        auctionTable.setSelectionBackground(new Color(60,140,255));
        auctionTable.setSelectionForeground(Color.white);
        // Alternating row colors and status color
        auctionTable.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col){
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (isSelected) { c.setBackground(new Color(90, 150, 255)); c.setForeground(Color.white); }
                else {
                    c.setBackground((row % 2 == 0) ? Color.white : new Color(245,248,250));
                    c.setForeground(Color.black);
                }
                // status column coloring (last column)
                if (col == table.getColumnCount() - 1 && value != null) {
                    String s = value.toString();
                    if (s.equalsIgnoreCase("CLOSED")) { c.setForeground(new Color(180,20,20)); }
                    else if (s.equalsIgnoreCase("OPEN")) { c.setForeground(new Color(20,120,20)); }
                }
                return c;
            }
        });
        JScrollPane scroll = new JScrollPane(auctionTable);

        // Log table on right side
        logTable.setFillsViewportHeight(true);
        logTable.setRowHeight(22);
        logTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        JScrollPane logScroll = new JScrollPane(logTable);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll, logScroll);
        split.setResizeWeight(0.72);
        split.setBorder(null);
        frame.add(split, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8,8));
        controls.setBackground(new Color(245,248,255));
        JButton refresh = new JButton("Refresh");
        JButton view = new JButton("View Details");
        JButton bid = new JButton("Place Bid");
        JButton create = new JButton("Create Auction");
        JButton close = new JButton("Close Auction");
        JTextField search = new JTextField(18);
        search.setToolTipText("Search title...");

        // Style controls
        styleButton(refresh, new Color(66,133,244), Color.white);
        styleButton(view, new Color(76,175,80), Color.white);
        styleButton(bid, new Color(255,152,0), Color.white);
        styleButton(create, new Color(102,16,242), Color.white);
        styleButton(close, new Color(220,53,69), Color.white);
        search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(200,200,200)), BorderFactory.createEmptyBorder(6,8,6,8)));

        controls.add(new JLabel("Search:"));
        controls.add(search);
        controls.add(refresh);
        controls.add(view);
        controls.add(bid);
        controls.add(create);
        controls.add(close);

        // Pagination controls
        prevBtn = new JButton("◀ Prev");
        nextBtn = new JButton("Next ▶");
        pageLabel = new JLabel("1 / 1");
        pageSizeCombo = new JComboBox<>(new Integer[]{10,25,50,100});
        pageSizeCombo.setSelectedItem(pageSize);
        styleButton(prevBtn, new Color(200,200,200), Color.black);
        styleButton(nextBtn, new Color(200,200,200), Color.black);
        controls.add(prevBtn);
        controls.add(pageLabel);
        controls.add(nextBtn);
        controls.add(new JLabel("Page size:"));
        controls.add(pageSizeCombo);

        frame.add(controls, BorderLayout.SOUTH);

        // Actions
        refresh.addActionListener(e -> { currentPage = 1; refreshTable(); });
        view.addActionListener(e -> viewSelectedAuction());
        bid.addActionListener(e -> placeBidDialog());
        create.addActionListener(e -> createAuctionDialog());
        close.addActionListener(e -> closeAuctionAction());

        prevBtn.addActionListener(e -> { if (currentPage > 1) { currentPage--; displayPage(); } });
        nextBtn.addActionListener(e -> { currentPage++; displayPage(); });
        pageSizeCombo.addActionListener(e -> { pageSize = (Integer)pageSizeCombo.getSelectedItem(); currentPage = 1; displayPage(); });

        loginBtn.addActionListener(e -> {
            LoginResult res = showLoginDialog();
            if (res != null && res.loggedIn) {
                currentUser = res.user;
                String who = (currentUser.getDisplayName() != null && !currentUser.getDisplayName().trim().isEmpty()) ? currentUser.getDisplayName() : capitalize(currentUser.getUsername());
                JOptionPane.showMessageDialog(frame, "Logged in as " + who);
                loginBtn.setEnabled(false);
                logoutBtn.setEnabled(true);
            }
        });

        // style auth buttons
        styleButton(loginBtn, new Color(255,255,255,220), THEME_PRIMARY.darker());
        styleButton(logoutBtn, new Color(255,255,255,220), THEME_PRIMARY.darker());

        logoutBtn.addActionListener(e -> {
            currentUser = null;
            JOptionPane.showMessageDialog(frame, "Logged out");
            loginBtn.setEnabled(true);
            logoutBtn.setEnabled(false);
        });

        search.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) { filterTable(search.getText().trim()); }
        });

        // keyboard: Enter to view
        auctionTable.addKeyListener(new KeyAdapter(){ @Override public void keyPressed(KeyEvent e){ if (e.getKeyCode()==KeyEvent.VK_ENTER) viewSelectedAuction(); }});

        // don't set visible here; visibility will be handled after login
    }

    private void styleButton(JButton b, Color bg, Color fg) {
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(200,200,200), 1, true), BorderFactory.createEmptyBorder(6,10,6,10)));
        b.setOpaque(true);
        b.setFont(new Font("SansSerif", Font.PLAIN, 12));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(brighten(bg, 0.08f)); }
            @Override public void mouseExited(MouseEvent e) { b.setBackground(bg); }
        });
    }

    private Color brighten(Color c, float fraction) {
        int r = c.getRed(); int g = c.getGreen(); int b = c.getBlue();
        int i = (int)(1.0/(1.0-fraction));
        if ( r == 0 && g == 0 && b == 0) return new Color(i, i, i);
        if ( r > 0 && r < i ) r = i;
        if ( g > 0 && g < i ) g = i;
        if ( b > 0 && b < i ) b = i;
        return new Color(Math.min((int)(r/(1.0-fraction)),255), Math.min((int)(g/(1.0-fraction)),255), Math.min((int)(b/(1.0-fraction)),255));
    }

    private void appendAnnouncementLog(String msg) {
        String time = java.time.LocalDateTime.now().format(dtf);
        SwingUtilities.invokeLater(() -> {
            logModel.addRow(new Object[]{time, msg});
            int last = logModel.getRowCount() - 1;
            if (last >= 0) {
                logTable.scrollRectToVisible(logTable.getCellRect(last, 0, true));
            }
        });
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }

    private void seedDemoData() {
        // create an admin user
        OnlineAuctionSystem.User admin = service.registerUser("admin","admin");
        admin.setAdmin(true);

        // Generate up to 150 demo auctions with mostly-unique owners
        java.util.Random rnd = new java.util.Random();
        String[] categories = new String[]{"Collectible","Electronics","Luxury","Antique","Gadget","Vehicle","Accessory","Artwork","Jewelry","Instrument"};
        int total = 150;

        // Prepare name components for nicer item titles and user display names
        String[] firstNames = new String[]{"Alex","Maya","Liam","Noah","Olivia","Emma","Ava","Sophia","Isabella","Mia","Lucas","Ethan","James","Amelia","Harper","Evelyn","Charlotte","Henry","Logan","Ryan","Grace","Chloe","Zoe","Luna","Eli","Oliver","Jack","Aria","Nora","Leah"};
        String[] lastNames = new String[]{"Smith","Johnson","Brown","Taylor","Anderson"};

        // create a pool of unique users (one per auction) so most items have different owners
        java.util.List<OnlineAuctionSystem.User> owners = new java.util.ArrayList<>();
        for (int u = 1; u <= total; u++) {
            // derive a human-readable username from first+last (no numeric ids)
            String first = firstNames[(u-1) % firstNames.length];
            String last = lastNames[((u-1) / firstNames.length) % lastNames.length];
            String uname = (first + "." + last).toLowerCase();
            OnlineAuctionSystem.User nu = service.registerUser(uname, "p" + u);
            // set a readable display name (First Last)
            nu.setDisplayName(first + " " + last);
            owners.add(nu);
        }

        // create auctions, assign each a mostly-unique owner; occasionally reuse admin for variety
        for (int i = 1; i <= total; i++) {
            String cat = categories[rnd.nextInt(categories.length)];
            // nicer title without numeric code
            String title = String.format("%s %s", cat, new String[]{"Collection","Series","Edition","Set","Classic","Piece"}[rnd.nextInt(6)]);
            String desc = String.format("Demo %s item", cat.toLowerCase());
            // price between 100000 and 1000000 (inclusive)
            double price = 100000 + rnd.nextInt(900001);
            // owner: mostly unique from owners pool; occasionally assign admin (about 3 items)
            OnlineAuctionSystem.User owner = owners.get(i - 1);
            if (i % 50 == 0) owner = admin; // reuse admin for a few items
            // give owner a readable display name (First Last) based on position
            String display = firstNames[(i-1) % firstNames.length] + " " + lastNames[( (i-1) / firstNames.length) % lastNames.length];
            owner.setDisplayName(display);
            long duration = 3600 + rnd.nextInt(7) * 3600L; // random few hours
            service.createAuction(title, desc, price, duration, owner);
        }

        // Add a few initial bids from random users so winners appear sometimes
        java.util.List<OnlineAuctionSystem.Auction> list = new java.util.ArrayList<>(service.listActiveAuctions());
        for (int k = 0; k < 25 && k < list.size(); k++) {
            OnlineAuctionSystem.Auction a = list.get(rnd.nextInt(list.size()));
            OnlineAuctionSystem.User bidder = owners.get(rnd.nextInt(owners.size()));
            double bidAmt = a.getCurrentPrice() + 5000 + rnd.nextInt(20000);
            service.placeBid(a.getId(), bidder, bidAmt);
        }
    }

    private void refreshTable() {
        // load current list from service and display first page
        currentList = service.listActiveAuctions();
        currentPage = 1;
        displayPage();
    }

    private void displayPage() {
        tableModel.setRowCount(0);
        int total = currentList.size();
        int totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
        if (currentPage > totalPages) currentPage = totalPages;
        int start = (currentPage - 1) * pageSize;
        int end = Math.min(start + pageSize, total);
        for (int i = start; i < end; i++) {
            OnlineAuctionSystem.Auction a = currentList.get(i);
            String ends = a.getEndsAt().toString();
            String status = a.isClosed() ? "CLOSED" : "OPEN";
            String owner = "(unknown)";
            if (a.getOwner() != null) {
                String dn = a.getOwner().getDisplayName();
                owner = (dn != null && !dn.trim().isEmpty()) ? dn : capitalize(a.getOwner().getUsername());
            }
            tableModel.addRow(new Object[]{a.getId(), a.getTitle(), owner, String.format("%.2f", a.getCurrentPrice()), ends, status});
        }
        pageLabel.setText(String.format("%d / %d (%d)", currentPage, totalPages, total));
        prevBtn.setEnabled(currentPage > 1);
        nextBtn.setEnabled(currentPage < totalPages);
    }

    private void filterTable(String q) {
        if (q.isEmpty()) { refreshTable(); return; }
        java.util.List<OnlineAuctionSystem.Auction> list = service.listActiveAuctions();
        currentList = new java.util.ArrayList<>();
        for (OnlineAuctionSystem.Auction a : list) {
            if (a.getTitle().toLowerCase().contains(q.toLowerCase())) {
                currentList.add(a);
            }
        }
        currentPage = 1;
        displayPage();
    }

    private void viewSelectedAuction() {
        int sel = auctionTable.getSelectedRow();
        if (sel < 0) { JOptionPane.showMessageDialog(frame, "Select an auction first."); return; }
        long id = Long.parseLong(tableModel.getValueAt(sel,0).toString());
        OnlineAuctionSystem.Auction a = service.getAuction(id);
        if (a==null) { JOptionPane.showMessageDialog(frame, "Auction not found."); return; }
        JTextArea ta = new JTextArea(a.fullString());
        ta.setEditable(false);
        ta.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(600,300));
        JOptionPane.showMessageDialog(frame, sp, "Auction Details", JOptionPane.INFORMATION_MESSAGE);
    }

    private void placeBidDialog() {
        if (currentUser == null) { JOptionPane.showMessageDialog(frame, "Please login first."); return; }
        int sel = auctionTable.getSelectedRow();
        if (sel < 0) { JOptionPane.showMessageDialog(frame, "Select an auction first."); return; }
        long id = Long.parseLong(tableModel.getValueAt(sel,0).toString());
        String s = JOptionPane.showInputDialog(frame, "Enter your bid amount:");
        if (s==null) return;
        double amt;
        try { amt = Double.parseDouble(s.trim()); }
        catch (NumberFormatException ex) { JOptionPane.showMessageDialog(frame, "Invalid amount."); return; }
        boolean ok = service.placeBid(id, currentUser, amt);
        JOptionPane.showMessageDialog(frame, ok ? "Bid accepted." : "Bid rejected.");
        refreshTable();
    }

    private void createAuctionDialog() {
        if (currentUser == null) { JOptionPane.showMessageDialog(frame, "Please login first."); return; }
        JTextField title = new JTextField();
        JTextField desc = new JTextField();
        JTextField start = new JTextField("0");
        JTextField dur = new JTextField("3600");
        Object[] msg = {"Title:", title, "Description:", desc, "Starting price:", start, "Duration (seconds):", dur};
        int res = JOptionPane.showConfirmDialog(frame, msg, "Create Auction", JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) return;
        try {
            double s = Double.parseDouble(start.getText().trim());
            long d = Long.parseLong(dur.getText().trim());
            OnlineAuctionSystem.Auction a = service.createAuction(title.getText().trim(), desc.getText().trim(), s, d, currentUser);
            JOptionPane.showMessageDialog(frame, "Created auction ID="+a.getId());
            refreshTable();
        } catch (Exception ex) { JOptionPane.showMessageDialog(frame, "Invalid input: " + ex.getMessage()); }
    }

    private void closeAuctionAction() {
        int sel = auctionTable.getSelectedRow();
        if (sel < 0) { JOptionPane.showMessageDialog(frame, "Select an auction first."); return; }
        long id = Long.parseLong(tableModel.getValueAt(sel,0).toString());
        OnlineAuctionSystem.Auction a = service.getAuction(id);
        if (a == null) { JOptionPane.showMessageDialog(frame, "Not found."); return; }
        if (currentUser == null || (!a.getOwner().equals(currentUser) && !currentUser.isAdmin())) {
            JOptionPane.showMessageDialog(frame, "Only owner or admin can close.");
            return;
        }
        // Offer option to simply close or close and announce winner
        int opt = JOptionPane.showOptionDialog(frame,
                "Close auction now? You can also announce the winner.",
                "Close Auction",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"Close","Close & Announce","Cancel"},
                "Close");
        if (opt == 2 || opt == JOptionPane.CLOSED_OPTION) return;
        boolean ok = service.closeAuction(id);
        if (!ok) {
            JOptionPane.showMessageDialog(frame, "Unable to close.");
            return;
        }
        if (opt == 1) { // Close & Announce
            OnlineAuctionSystem.User winner = a.getHighestBidder();
            String announcement;
            if (winner == null) {
                announcement = "Auction '" + a.getTitle() + "' closed with no bids.";
            } else {
                String winnerName = (winner.getDisplayName() != null && !winner.getDisplayName().trim().isEmpty()) ? winner.getDisplayName() : capitalize(winner.getUsername());
                announcement = String.format("Auction '%s' won by %s (%.2f) with %d bids",
                    a.getTitle(), winnerName, a.getCurrentPrice(), a.getBidCount());
            }
            appendAnnouncementLog(announcement);
            JOptionPane.showMessageDialog(frame, "Auction closed.\n" + announcement);
        } else {
            JOptionPane.showMessageDialog(frame, "Auction closed.");
        }
        refreshTable();
    }

    private LoginResult showLoginDialog() {
        JTextField uname = new JTextField();
        JPasswordField pw = new JPasswordField();
        Object[] msg = {"Username:", uname, "Password:", pw};
        int option = JOptionPane.showOptionDialog(frame, msg, "Login / Register", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, new String[]{"Login","Register","Cancel"}, "Login");
        if (option == 2 || option == JOptionPane.CLOSED_OPTION) return null;
        String user = uname.getText().trim();
        String pass = new String(pw.getPassword());
        if (option == 0) { // Login
            OnlineAuctionSystem.User u = service.login(user, pass);
            if (u == null) { JOptionPane.showMessageDialog(frame, "Invalid credentials."); return new LoginResult(false, null); }
            return new LoginResult(true, u);
        } else { // Register
            if (service.getUserByName(user) != null) { JOptionPane.showMessageDialog(frame, "Username taken."); return new LoginResult(false, null); }
            OnlineAuctionSystem.User u = service.registerUser(user, pass);
            // set a readable display name derived from username (replace dots with space)
            String disp = user.replace('.', ' ');
            u.setDisplayName(capitalize(disp));
            JOptionPane.showMessageDialog(frame, "Registered: " + (u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()));
            return new LoginResult(true, u);
        }
    }

    private static class LoginResult { final boolean loggedIn; final OnlineAuctionSystem.User user; LoginResult(boolean l, OnlineAuctionSystem.User u){loggedIn=l;user=u;} }

    // Dedicated login window (separate screen) shown before dashboard
    private class LoginWindow extends JDialog {
        // image loaded from project folder if present
        private BufferedImage logoImage = null;
        private final File configFile = new File(System.getProperty("user.home"), ".auctiondashboard.properties");

        private String loadRememberedUser() {
            try {
                Properties p = new Properties();
                if (!configFile.exists()) return null;
                try (FileInputStream fis = new FileInputStream(configFile)) { p.load(fis); }
                return p.getProperty("rememberedUser");
            } catch (Exception ex) { return null; }
        }

        private void saveRememberedUser(String user) {
            try {
                Properties p = new Properties();
                if (user != null && !user.isEmpty()) p.setProperty("rememberedUser", user);
                try (FileOutputStream fos = new FileOutputStream(configFile)) { p.store(fos, "Auction Dashboard config"); }
            } catch (Exception ex) { /* ignore */ }
        }

        LoginWindow(Frame owner) {
            super(owner, "Login / Register", true);
                // use a compact, centered login dialog (restore original small size)
                setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                setResizable(false);
                setSize(520,240);
            // Use a gradient background panel as content pane
            JPanel main = new JPanel(new BorderLayout(8,8)) {
                @Override public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g;
                    int w = getWidth(), h = getHeight();
                    GradientPaint gp = new GradientPaint(0,0, THEME_BG, 0, h, Color.white);
                    g2.setPaint(gp);
                    g2.fillRect(0,0,w,h);
                }
            };
            setContentPane(main);

            // Header like dashboard
            JPanel hdr = new JPanel(new BorderLayout());
            hdr.setBackground(THEME_PRIMARY);
            hdr.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));
            JLabel htitle = new JLabel("Login to Auction Dashboard");
            htitle.setForeground(Color.white);
            htitle.setFont(new Font("SansSerif", Font.BOLD, 16));
            htitle.setIcon(new Icon() { public int getIconWidth(){ return 12; } public int getIconHeight(){ return 12; } public void paintIcon(Component c, Graphics g, int x, int y){ g.setColor(THEME_ACCENT); g.fillOval(x,y,12,12); } });
            hdr.add(htitle, BorderLayout.WEST);
            add(hdr, BorderLayout.NORTH);

            // Body: logo on left, form on right
            JPanel body = new JPanel(new BorderLayout(8,8));
            body.setOpaque(false);

            // Logo panel: either load an external image named 'logo.png' in the project folder, or draw emblem
            JPanel logo = new JPanel() {
                @Override public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    int w = getWidth(); int h = getHeight();
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    // try load external logo if not loaded yet
                    if (logoImage == null) {
                        try {
                            File f = new File("logo.png");
                            if (!f.exists()) f = new File("resources/logo.png");
                            if (f.exists()) logoImage = ImageIO.read(f);
                        } catch (Exception ex) { logoImage = null; }
                    }
                    if (logoImage != null) {
                        // draw centered scaled image
                        int iw = logoImage.getWidth();
                        int ih = logoImage.getHeight();
                        double scale = Math.min((double)(w-20)/iw, (double)(h-20)/ih);
                        int dw = (int)(iw * scale);
                        int dh = (int)(ih * scale);
                        int dx = (w - dw)/2;
                        int dy = (h - dh)/2;
                        g2.drawImage(logoImage, dx, dy, dw, dh, null);
                    } else {
                        // fallback drawn emblem
                        int diameter = Math.min(w, h) - 20;
                        g2.setColor(THEME_PRIMARY);
                        g2.fillOval((w - diameter)/2, 10, diameter, diameter);
                        g2.setColor(THEME_ACCENT);
                        g2.fillOval((w - diameter)/2 + diameter/4, 10 + diameter/4, diameter/2, diameter/2);
                        g2.setColor(Color.white);
                        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(18, diameter/4)));
                        String s = "AD";
                        FontMetrics fm = g2.getFontMetrics();
                        int sw = fm.stringWidth(s);
                        g2.drawString(s, (w - sw)/2, 10 + diameter/2 + fm.getAscent()/3);
                    }
                }
            };
            logo.setPreferredSize(new Dimension(140,120));
            logo.setOpaque(false);
            body.add(logo, BorderLayout.WEST);

            JPanel form = new JPanel(new BorderLayout(4,8));
            form.setOpaque(false);
            JLabel intro = new JLabel("Welcome — browse and manage auctions. Log in to continue.");
            intro.setFont(new Font("SansSerif", Font.ITALIC, 12));
            intro.setForeground(new Color(60,60,60));
            form.add(intro, BorderLayout.NORTH);

            JPanel p = new JPanel(new GridLayout(2,2,6,6));
            p.setOpaque(false);
            JTextField uname = new JTextField();
            JPasswordField pw = new JPasswordField();
            p.add(new JLabel("Username:")); p.add(uname);
            p.add(new JLabel("Password:")); p.add(pw);
            form.add(p, BorderLayout.CENTER);

            // remember me checkbox and subtle tips area
            JPanel southPanel = new JPanel(new BorderLayout());
            southPanel.setOpaque(false);
            JCheckBox remember = new JCheckBox("Remember me");
            remember.setOpaque(false);
            southPanel.add(remember, BorderLayout.WEST);
            JLabel tips = new JLabel("Tip: Use 'admin'/'admin' to login quickly. Page size defaults to 25.");
            tips.setFont(new Font("SansSerif", Font.PLAIN, 11));
            tips.setForeground(new Color(100,100,100));
            southPanel.add(tips, BorderLayout.SOUTH);
            form.add(southPanel, BorderLayout.SOUTH);

            body.add(form, BorderLayout.CENTER);
            add(body, BorderLayout.CENTER);

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton login = new JButton("Login");
            JButton register = new JButton("Register");
            JButton cancel = new JButton("Cancel");
            // Style buttons to match theme
            styleButton(login, THEME_PRIMARY, Color.white);
            styleButton(register, new Color(102,16,242), Color.white);
            styleButton(cancel, new Color(220,53,69), Color.white);
            btns.add(register); btns.add(login); btns.add(cancel);
            btns.setOpaque(false);
            add(btns, BorderLayout.SOUTH);

            // prefill remembered username if any
            String remembered = loadRememberedUser();
            if (remembered != null && !remembered.isEmpty()) { uname.setText(remembered); remember.setSelected(true); }

            login.addActionListener(e -> {
                String user = uname.getText().trim();
                String pass = new String(pw.getPassword());
                if (user.isEmpty() || pass.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter username and password"); return; }
                OnlineAuctionSystem.User u = service.login(user, pass);
                if (u == null) { JOptionPane.showMessageDialog(this, "Invalid credentials"); return; }
                currentUser = u;
                if (remember.isSelected()) saveRememberedUser(user); else saveRememberedUser("");
                dispose();
            });

            register.addActionListener(e -> {
                String user = uname.getText().trim();
                String pass = new String(pw.getPassword());
                if (user.isEmpty() || pass.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter username and password"); return; }
                if (service.getUserByName(user) != null) { JOptionPane.showMessageDialog(this, "Username already taken"); return; }
                OnlineAuctionSystem.User u = service.registerUser(user, pass);
                // derive a readable display name from username
                String disp = user.replace('.', ' ');
                u.setDisplayName(capitalize(disp));
                currentUser = u;
                if (remember.isSelected()) saveRememberedUser(user); else saveRememberedUser("");
                JOptionPane.showMessageDialog(this, "Registered: " + (u.getDisplayName() != null ? u.getDisplayName() : capitalize(u.getUsername())));
                dispose();
            });

            cancel.addActionListener(e -> { currentUser = null; dispose(); });

                // Allow ESC to cancel/close the login full-screen window
                getRootPane().registerKeyboardAction(ev -> { currentUser = null; dispose(); },
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

            setLocationRelativeTo(owner);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AuctionDashboard::new);
    }
}

