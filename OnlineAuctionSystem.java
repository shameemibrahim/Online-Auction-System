import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OnlineAuctionSystem {

    // ---------- USER CLASS ----------
    static class User {
        private static long NEXT = 1;
        private final long id;
        private final String username;
        private final String password;
        private boolean admin = false;
        private String displayName = null;

        public User(String username, String password) {
            this.id = NEXT++;
            this.username = username;
            this.password = password;
        }

        public String getDisplayName() { return displayName != null ? displayName : username; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public long getId() { return id; }
        public String getUsername() { return username; }
        public boolean isAdmin() { return admin; }
        public void setAdmin(boolean admin) { this.admin = admin; }
        public boolean checkPassword(String pw) { return password.equals(pw); }

        @Override
        public String toString() {
            return "User{" + id + ":" + username + (admin ? ",admin" : "") + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof User)) return false;
            return ((User)o).id == id;
        }

        @Override
        public int hashCode() { return Long.hashCode(id); }
    }

    // ---------- BID CLASS ----------
    static class Bid {
        private final User bidder;
        private final double amount;
        private final Instant time;

        public Bid(User bidder, double amount) {
            this.bidder = bidder;
            this.amount = amount;
            this.time = Instant.now();
        }

        public User getBidder() { return bidder; }
        public double getAmount() { return amount; }
        public Instant getTime() { return time; }

        @Override
        public String toString() {
            return String.format("%s bid %.2f at %s", bidder.getUsername(), amount, time);
        }
    }

    // ---------- AUCTION CLASS ----------
    static class Auction {
        private static long NEXT = 1;
        private final long id;
        private final String title;
        private final String description;
        private final double startingPrice;
        private final User owner;
        private final Instant createdAt;
        private final Instant endsAt;
        private boolean closed = false;
        private final List<Bid> bids = new ArrayList<>();

        public Auction(String title, String description, double startingPrice, Instant endsAt, User owner) {
            this.id = NEXT++;
            this.title = title;
            this.description = description;
            this.startingPrice = startingPrice;
            this.owner = owner;
            this.createdAt = Instant.now();
            this.endsAt = endsAt;
        }

        public long getId() { return id; }
        public String getTitle() { return title; }
        public User getOwner() { return owner; }
        public Instant getEndsAt() { return endsAt; }
        public boolean isClosed() { return closed || Instant.now().isAfter(endsAt); }
        public void setClosed(boolean c) { closed = c; }

        public double getCurrentPrice() {
            if (bids.isEmpty()) return startingPrice;
            return bids.get(bids.size() - 1).getAmount();
        }

        // Return the highest (most recent) bidder, or null if none
        public User getHighestBidder() {
            if (bids.isEmpty()) return null;
            return bids.get(bids.size() - 1).getBidder();
        }

        // Number of bids placed on this auction
        public int getBidCount() { return bids.size(); }

        public boolean placeBid(Bid b) {
            if (isClosed()) return false;
            double current = getCurrentPrice();
            if (b.getAmount() <= current) return false;
            bids.add(b);
            return true;
        }

        public String fullString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Auction ID: ").append(id).append("\n");
            sb.append("Title: ").append(title).append("\n");
            sb.append("Description: ").append(description).append("\n");
            sb.append("Owner: ").append(owner.getUsername()).append("\n");
            sb.append("Starting price: ").append(String.format("%.2f", startingPrice)).append("\n");
            sb.append("Current price: ").append(String.format("%.2f", getCurrentPrice())).append("\n");
            sb.append("Ends at: ").append(endsAt).append("\n");
            sb.append("Status: ").append(isClosed() ? "CLOSED" : "OPEN").append("\n");
            sb.append("Bids:\n");
            for (Bid bid : bids) sb.append("  - ").append(bid).append("\n");
            return sb.toString();
        }
    }

    // ---------- SERVICE CLASS ----------
    static class AuctionService {
        private final Map<Long, Auction> auctions = new ConcurrentHashMap<>();
        private final Map<String, User> users = new ConcurrentHashMap<>();

        // User methods
        public User registerUser(String username, String password) {
            User u = new User(username, password);
            users.put(username, u);
            return u;
        }
        public User login(String username, String password) {
            User u = users.get(username);
            if (u == null) return null;
            return u.checkPassword(password) ? u : null;
        }
        public User getUserByName(String username) { return users.get(username); }

        // Auction methods
        public Auction createAuction(String title, String desc, double start, long durationSec, User owner) {
            // End times vary per auction: random future date starting from tomorrow
            java.util.Random rnd = new java.util.Random();
            long daysAhead = 1 + rnd.nextInt(30); // between 1 and 30 days from now
            long extraSecondsInDay = rnd.nextInt(24 * 60 * 60); // random time within the chosen day
            Instant ends = Instant.now().plusSeconds(daysAhead * 24L * 60L * 60L + extraSecondsInDay);
            Auction a = new Auction(title, desc, start, ends, owner);
            auctions.put(a.getId(), a);
            return a;
        }

        public boolean placeBid(long auctionId, User bidder, double amount) {
            Auction a = auctions.get(auctionId);
            if (a == null) return false;
            Bid b = new Bid(bidder, amount);
            return a.placeBid(b);
        }

        public List<Auction> listActiveAuctions() {
            expireAuctions();
            return auctions.values().stream()
                    .filter(a -> !a.isClosed())
                    .collect(Collectors.toList());
        }

        public Auction getAuction(long id) { return auctions.get(id); }

        public void expireAuctions() {
            Instant now = Instant.now();
            for (Auction a : auctions.values()) {
                if (!a.isClosed() && now.isAfter(a.getEndsAt())) a.setClosed(true);
            }
        }

        public boolean closeAuction(long id) {
            Auction a = auctions.get(id);
            if (a == null || a.isClosed()) return false;
            a.setClosed(true);
            return true;
        }
    }

    // ---------- MAIN PROGRAM ----------
    private static final AuctionService service = new AuctionService();
    private static final Scanner scanner = new Scanner(System.in);
    private static User currentUser = null;

    public static void main(String[] args) {
        seedDemoData();
        System.out.println("=== Simple Online Auction System (Single File Demo) ===");

        while (true) {
            service.expireAuctions();
            showMenu();
            String cmd = scanner.nextLine().trim();

            switch (cmd) {
                case "1" -> registerUser();
                case "2" -> loginUser();
                case "3" -> createAuction();
                case "4" -> listAuctions();
                case "5" -> viewAuction();
                case "6" -> placeBid();
                case "7" -> closeAuction();
                case "8" -> { System.out.println("Goodbye!"); return; }
                default -> System.out.println("Unknown option.");
            }
        }
    }

    private static void showMenu() {
        System.out.println("\nMenu:");
        System.out.println("1) Register user");
        System.out.println("2) Login");
        System.out.println("3) Create auction");
        System.out.println("4) List active auctions");
        System.out.println("5) View auction details");
        System.out.println("6) Place bid");
        System.out.println("7) Close auction");
        System.out.println("8) Exit");
        System.out.print("Choose: ");
    }

    private static void registerUser() {
        System.out.print("Username: ");
        String uname = scanner.nextLine().trim();
        if (service.getUserByName(uname) != null) {
            System.out.println("Username taken.");
            return;
        }
        System.out.print("Password: ");
        String pw = scanner.nextLine().trim();
        User u = service.registerUser(uname, pw);
        System.out.println("Registered: " + u);
    }

    private static void loginUser() {
        System.out.print("Username: ");
        String uname = scanner.nextLine().trim();
        System.out.print("Password: ");
        String pw = scanner.nextLine().trim();
        User u = service.login(uname, pw);
        if (u == null) System.out.println("Invalid credentials.");
        else {
            currentUser = u;
            System.out.println("Logged in as " + currentUser.getUsername());
        }
    }

    private static void createAuction() {
        if (!requireLogin()) return;
        System.out.print("Title: ");
        String title = scanner.nextLine().trim();
        System.out.print("Description: ");
        String desc = scanner.nextLine().trim();
        System.out.print("Starting price: ");
        double start = Double.parseDouble(scanner.nextLine().trim());
        System.out.print("Duration (seconds): ");
        long dur = Long.parseLong(scanner.nextLine().trim());
        Auction a = service.createAuction(title, desc, start, dur, currentUser);
        System.out.println("Created auction: ID=" + a.getId() + " (" + a.getTitle() + ")");
    }

    private static void listAuctions() {
        List<Auction> list = service.listActiveAuctions();
        if (list.isEmpty()) { System.out.println("No active auctions."); return; }
        System.out.println("Active Auctions:");
        for (Auction a : list) {
            System.out.printf("ID=%d | %s | Current=%.2f | Ends=%s%n",
                    a.getId(), a.getTitle(), a.getCurrentPrice(), a.getEndsAt());
        }
    }

    private static void viewAuction() {
        System.out.print("Auction ID: ");
        long id = Long.parseLong(scanner.nextLine().trim());
        Auction a = service.getAuction(id);
        if (a == null) { System.out.println("Not found."); return; }
        System.out.println(a.fullString());
    }

    private static void placeBid() {
        if (!requireLogin()) return;
        System.out.print("Auction ID: ");
        long id = Long.parseLong(scanner.nextLine().trim());
        System.out.print("Your bid amount: ");
        double amt = Double.parseDouble(scanner.nextLine().trim());
        boolean ok = service.placeBid(id, currentUser, amt);
        System.out.println(ok ? "Bid accepted." : "Bid rejected.");
    }

    private static void closeAuction() {
        System.out.print("Auction ID: ");
        long id = Long.parseLong(scanner.nextLine().trim());
        Auction a = service.getAuction(id);
        if (a == null) { System.out.println("Not found."); return; }
        if (currentUser == null || (!a.getOwner().equals(currentUser) && !currentUser.isAdmin())) {
            System.out.println("Only owner or admin can close.");
            return;
        }
        boolean ok = service.closeAuction(id);
        System.out.println(ok ? "Auction closed." : "Unable to close.");
    }

    private static boolean requireLogin() {
        if (currentUser == null) {
            System.out.println("Please login first.");
            return false;
        }
        return true;
    }

    private static void seedDemoData() {
        User admin = service.registerUser("admin", "admin");
        admin.setAdmin(true);
        User alice = service.registerUser("alice", "a");
        User bob = service.registerUser("bob", "b");
        service.createAuction("Vintage Camera", "Old film camera, working", 50.0, 120, alice);
        service.createAuction("Mountain Bike", "Good condition", 100.0, 300, bob);
    }
}
