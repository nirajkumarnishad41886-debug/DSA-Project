
import java.io.*;
import java.util.Scanner;

public class ExamSeatingSystem {

    static final int ROOMS = 2, ROWS = 2, COLS = 3;
    static final String DATA_FILE = "exam_seating.txt";
    static final int FIRST_ID = 101;

   
    static class Student {
        String id, name;
        Student(String id, String name) { this.id = id; this.name = name; }
        int idNumber() { return Integer.parseInt(id.substring(1)); }
    }

    // ------------------------------------------- Queue (linked list, FIFO)
    static class StudentQueue {
        private static class Node { Student data; Node next; Node(Student s) { data = s; } }
        private Node head, tail;
        private int size;

        void enqueue(Student s) {
            Node n = new Node(s);
            if (tail == null) head = tail = n; else { tail.next = n; tail = n; }
            size++;
        }
        Student dequeue() {
            if (head == null) return null;
            Student s = head.data;
            head = head.next;
            if (head == null) tail = null;
            size--;
            return s;
        }
        Student peek() { return head == null ? null : head.data; }
        boolean isEmpty() { return size == 0; }
        int size() { return size; }
        /** linear search; returns 1-based position or -1 */
        int positionOf(String id) {
            int pos = 1;
            for (Node c = head; c != null; c = c.next, pos++)
                if (c.data.id.equals(id)) return pos;
            return -1;
        }
        Student get(int position) {
            Node c = head;
            for (int i = 1; i < position && c != null; i++) c = c.next;
            return c == null ? null : c.data;
        }
        /** removes a student by id (used when a waiting student withdraws) */
        boolean remove(String id) {
            Node prev = null;
            for (Node c = head; c != null; prev = c, c = c.next) {
                if (c.data.id.equals(id)) {
                    if (prev == null) head = c.next; else prev.next = c.next;
                    if (c == tail) tail = prev;
                    size--;
                    return true;
                }
            }
            return false;
        }
    }

    // --------------------------------------- Graph (adjacency list of rooms)
    static class RoomGraph {
        private static class Edge { int to; Edge next; Edge(int t, Edge n) { to = t; next = n; } }
        private final Edge[] adj;
        private final int n;
        RoomGraph(int n) { this.n = n; adj = new Edge[n]; }

        void addEdge(int a, int b) {           // undirected
            adj[a] = new Edge(b, adj[a]);
            adj[b] = new Edge(a, adj[b]);
        }
        boolean connected(int a, int b) {
            for (Edge e = adj[a]; e != null; e = e.next) if (e.to == b) return true;
            return false;
        }
        /** BFS order from start */
        int[] bfsOrder(int start) {
            int[] order = new int[n]; boolean[] seen = new boolean[n];
            int[] q = new int[n]; int h = 0, t = 0, cnt = 0;
            q[t++] = start; seen[start] = true;
            while (h < t) {
                int u = q[h++]; order[cnt++] = u;
                for (Edge e = adj[u]; e != null; e = e.next)
                    if (!seen[e.to]) { seen[e.to] = true; q[t++] = e.to; }
            }
            int[] res = new int[cnt];
            System.arraycopy(order, 0, res, 0, cnt);
            return res;
        }
        /** DFS order from start */
        int[] dfsOrder(int start) {
            int[] order = new int[n]; boolean[] seen = new boolean[n];
            int[] cnt = {0};
            dfs(start, seen, order, cnt);
            int[] res = new int[cnt[0]];
            System.arraycopy(order, 0, res, 0, cnt[0]);
            return res;
        }
        private void dfs(int u, boolean[] seen, int[] order, int[] cnt) {
            seen[u] = true; order[cnt[0]++] = u;
            for (Edge e = adj[u]; e != null; e = e.next)
                if (!seen[e.to]) dfs(e.to, seen, order, cnt);
        }
        /** nearest room (by BFS distance) from start that still has a free seat; -1 if none */
        int nearestWithFree(int start, int[] free) {
            int[] order = bfsOrder(start);
            for (int i = 0; i < order.length; i++) if (free[order[i]] > 0) return order[i];
            for (int r = 0; r < n; r++) if (free[r] > 0) return r;   // disconnected rooms
            return -1;
        }
        /** shortest path (fewest hops) using BFS with parent array; null if unreachable */
        int[] shortestPath(int s, int d) {
            int[] parent = new int[n]; boolean[] seen = new boolean[n];
            for (int i = 0; i < n; i++) parent[i] = -1;
            int[] q = new int[n]; int h = 0, t = 0;
            q[t++] = s; seen[s] = true;
            while (h < t) {
                int u = q[h++];
                if (u == d) break;
                for (Edge e = adj[u]; e != null; e = e.next)
                    if (!seen[e.to]) { seen[e.to] = true; parent[e.to] = u; q[t++] = e.to; }
            }
            if (!seen[d]) return null;
            int len = 0;
            for (int v = d; v != -1; v = parent[v]) len++;
            int[] path = new int[len];
            int v = d;
            for (int i = len - 1; i >= 0; i--) { path[i] = v; v = parent[v]; }
            return path;
        }
        void print() {
            for (int i = 0; i < n; i++) {
                StringBuilder sb = new StringBuilder("  Room " + (i + 1) + " -> ");
                boolean any = false;
                for (Edge e = adj[i]; e != null; e = e.next) {
                    sb.append(any ? ", " : "").append("Room ").append(e.to + 1);
                    any = true;
                }
                if (!any) sb.append("(no connections)");
                System.out.println(sb);
            }
        }
    }

    // ----------------------------------------------------- Seating system
    static class SeatingSystem {
        final Student[][][] seats = new Student[ROOMS][ROWS][COLS];   // 2D seating per room
        final StudentQueue waiting = new StudentQueue();
        final RoomGraph graph = new RoomGraph(ROOMS);
        int nextId = FIRST_ID;
        Student lastStudent;     // last student registered (for extra info in menu)

        SeatingSystem() {
            for (int r = 0; r + 1 < ROOMS; r++) graph.addEdge(r, r + 1);   // Room1 <-> Room2 ...
        }

        static String seatLabel(int row, int col) { return "" + (char) ('A' + row) + (col + 1); }

        int freeSeats(int room) {
            int c = 0;
            for (int r = 0; r < ROWS; r++) for (int s = 0; s < COLS; s++) if (seats[room][r][s] == null) c++;
            return c;
        }
        int[] freeArray() {
            int[] f = new int[ROOMS];
            for (int i = 0; i < ROOMS; i++) f[i] = freeSeats(i);
            return f;
        }
        int totalFree() { int t = 0; for (int i = 0; i < ROOMS; i++) t += freeSeats(i); return t; }

        private Student newStudent(String name) { return new Student("E" + (nextId++), name); }

        private String place(Student s, int room) {
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++)
                    if (seats[room][r][c] == null) {          // never overwrite an occupied seat
                        seats[room][r][c] = s;
                        return "Allocated: " + s.id + " | " + s.name + " | Room " + (room + 1)
                                + " | Seat " + seatLabel(r, c);
                    }
            return null;
        }

        /** Allocate to next available seat (Room 1 first, nearest connected room next). */
        String allocate(String name) {
            lastStudent = newStudent(name);
            int room = graph.nearestWithFree(0, freeArray());
            if (room == -1) {
                waiting.enqueue(lastStudent);
                return "No seats available. Student added to waiting queue.";
            }
            return place(lastStudent, room);
        }

        /** Locate seated student; returns {room,row,col} or null. Uses merge sort + binary search. */
        int[] locate(String id) {
            Student[] sorted = sortedSeated();
            int lo = 0, hi = sorted.length - 1, target = idNum(id);
            while (lo <= hi && target >= 0) {
                int mid = (lo + hi) >>> 1, v = sorted[mid].idNumber();
                if (v == target) return findSeat(sorted[mid].id);
                if (v < target) lo = mid + 1; else hi = mid - 1;
            }
            return null;
        }
        private int[] findSeat(String id) {
            for (int a = 0; a < ROOMS; a++) for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++)
                if (seats[a][r][c] != null && seats[a][r][c].id.equals(id)) return new int[]{a, r, c};
            return null;
        }
        static int idNum(String id) {
            if (id == null || id.length() < 2 || (id.charAt(0) != 'E')) return -1;
            try { return Integer.parseInt(id.substring(1)); } catch (NumberFormatException e) { return -1; }
        }

        /** Search result text */
        String search(String rawId) {
            String id = rawId.trim().toUpperCase();
            int[] loc = locate(id);
            if (loc != null) {
                Student s = seats[loc[0]][loc[1]][loc[2]];
                return "Student " + s.id + " found: " + s.name + " | Room " + (loc[0] + 1)
                        + " | Seat " + seatLabel(loc[1], loc[2]);
            }
            int pos = waiting.positionOf(id);
            if (pos != -1) return "Student " + id + " found: " + waiting.get(pos).name
                    + " | In waiting queue (position " + pos + ")";
            return "Student " + id + " not found.";
        }

        /** Cancel allocation (or waiting entry). Promotes first waiting student into freed seat. */
        String cancel(String rawId) {
            String id = rawId.trim().toUpperCase();
            int[] loc = locate(id);
            if (loc != null) {
                Student gone = seats[loc[0]][loc[1]][loc[2]];
                seats[loc[0]][loc[1]][loc[2]] = null;
                String msg = "Cancelled: " + gone.id + " | " + gone.name + " released Room " + (loc[0] + 1)
                        + " Seat " + seatLabel(loc[1], loc[2]) + ".";
                if (!waiting.isEmpty()) {
                    Student next = waiting.dequeue();
                    seats[loc[0]][loc[1]][loc[2]] = next;
                    msg += "\nWaiting queue -> " + "Allocated: " + next.id + " | " + next.name
                            + " | Room " + (loc[0] + 1) + " | Seat " + seatLabel(loc[1], loc[2]);
                }
                return msg;
            }
            if (waiting.remove(id)) return "Removed " + id + " from the waiting queue.";
            return "Student " + id + " not found.";
        }

        /** One row of a room, e.g. "Room 1: A1 Aman | A2 Riya | A3 Dev" */
        String rowLine(int room, int row) {
            StringBuilder sb = new StringBuilder(row == 0 ? "Room " + (room + 1) + ": " : "        ");
            for (int c = 0; c < COLS; c++) {
                Student s = seats[room][row][c];
                sb.append(seatLabel(row, c)).append(' ').append(s == null ? "[Empty]" : s.name);
                if (c < COLS - 1) sb.append(" | ");
            }
            return sb.toString();
        }
        void displayRoom(int room) {
            for (int r = 0; r < ROWS; r++) System.out.println(rowLine(room, r));
        }

        /** all seated students sorted by id (merge sort) */
        Student[] sortedSeated() {
            Student[] tmp = new Student[ROOMS * ROWS * COLS]; int n = 0;
            for (int a = 0; a < ROOMS; a++) for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++)
                if (seats[a][r][c] != null) tmp[n++] = seats[a][r][c];
            Student[] arr = new Student[n];
            System.arraycopy(tmp, 0, arr, 0, n);
            mergeSort(arr, 0, n - 1, new Student[n]);
            return arr;
        }
        private static void mergeSort(Student[] a, int lo, int hi, Student[] buf) {
            if (lo >= hi) return;
            int mid = (lo + hi) >>> 1;
            mergeSort(a, lo, mid, buf); mergeSort(a, mid + 1, hi, buf);
            int i = lo, j = mid + 1, k = lo;
            while (i <= mid && j <= hi) buf[k++] = a[i].idNumber() <= a[j].idNumber() ? a[i++] : a[j++];
            while (i <= mid) buf[k++] = a[i++];
            while (j <= hi) buf[k++] = a[j++];
            for (k = lo; k <= hi; k++) a[k] = buf[k];
        }

        // ------------------------------------------------ File handling
        void save(String file) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.println("NEXTID|" + nextId);
                for (int a = 0; a < ROOMS; a++) for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
                    Student s = seats[a][r][c];
                    if (s != null) pw.println("SEAT|" + a + "|" + r + "|" + c + "|" + s.id + "|" + s.name);
                }
                for (int i = 1; i <= waiting.size(); i++) {
                    Student s = waiting.get(i);
                    pw.println("WAIT|" + s.id + "|" + s.name);
                }
                System.out.println("Data saved to " + file);
            } catch (IOException e) {
                System.out.println("Error: could not save data (" + e.getMessage() + ")");
            }
        }
        void load(String file) {
            File f = new File(file);
            if (!f.exists()) { System.out.println("No saved data found. Starting fresh (first ID E" + FIRST_ID + ")."); return; }
            int loaded = 0;
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.split("\\|", -1);
                    try {
                        if (p[0].equals("NEXTID")) nextId = Integer.parseInt(p[1]);
                        else if (p[0].equals("SEAT")) {
                            int a = Integer.parseInt(p[1]), r = Integer.parseInt(p[2]), c = Integer.parseInt(p[3]);
                            if (a < ROOMS && r < ROWS && c < COLS && seats[a][r][c] == null) {
                                seats[a][r][c] = new Student(p[4], p[5]); loaded++;
                            }
                        } else if (p[0].equals("WAIT")) { waiting.enqueue(new Student(p[1], p[2])); loaded++; }
                    } catch (RuntimeException ex) { /* skip malformed line */ }
                }
                System.out.println("Loaded " + loaded + " record(s) from " + file);
            } catch (IOException e) {
                System.out.println("Error: could not read data file (" + e.getMessage() + ")");
            }
        }
    }

    // --------------------------------------------------------- Test cases
    static int passed = 0, failed = 0;

    static void check(String label, String expected, String actual) {
        boolean ok = expected.equals(actual);
        if (ok) passed++; else failed++;
        System.out.println("  Input/Action : " + label);
        System.out.println("  Expected     : " + expected);
        System.out.println("  Actual       : " + actual);
        System.out.println("  Result       : " + (ok ? "PASS" : "FAIL") + "\n");
    }

    static void runTests() {
        passed = failed = 0;
        System.out.println("\n================ RUNNING TEST CASES ================");
        System.out.println("(uses a fresh in-memory system; saved data is not touched)\n");

        System.out.println("Test Case 1: Normal Input");
        SeatingSystem s1 = new SeatingSystem();
        check("Allocate Aman to the next available seat",
                "Allocated: E101 | Aman | Room 1 | Seat A1", s1.allocate("Aman"));

        System.out.println("Test Case 2: Boundary / Minimum Input");
        SeatingSystem s2 = new SeatingSystem();
        for (int i = 1; i <= ROOMS * ROWS * COLS; i++) s2.allocate("Student" + i);
        check("All seats are occupied; request one more seat",
                "No seats available. Student added to waiting queue.", s2.allocate("Extra"));

        System.out.println("Test Case 3: Invalid Input");
        SeatingSystem s3 = new SeatingSystem();
        check("Search for student E999", "Student E999 not found.", s3.search("E999"));

        System.out.println("Test Case 4: Different Valid Scenario");
        SeatingSystem s4 = new SeatingSystem();
        s4.allocate("Aman");
        check("Allocate Riya (a)", "Allocated: E102 | Riya | Room 1 | Seat A2", s4.allocate("Riya"));
        check("Allocate Dev (b)", "Allocated: E103 | Dev | Room 1 | Seat A3", s4.allocate("Dev"));
        check("Display Room 1 seating (c)", "Room 1: A1 Aman | A2 Riya | A3 Dev", s4.rowLine(0, 0));

        System.out.println("Bonus: Waiting queue promotion");
        SeatingSystem s5 = s2;     // full system with 1 waiting student
        String r = s5.cancel("E101");
        check("Cancel E101, waiting student should get the freed seat",
                "Cancelled: E101 | Student1 released Room 1 Seat A1.\n"
                        + "Waiting queue -> Allocated: E113 | Extra | Room 1 | Seat A1", r);

        System.out.println("====================================================");
        System.out.println("Summary: " + passed + " passed, " + failed + " failed.");
        System.out.println("====================================================\n");
    }

    // ---------------------------------------------------------- Console UI
    static Scanner in;

    static String ask(String prompt) {
        System.out.print(prompt);
        if (!in.hasNextLine()) return null;
        return in.nextLine().trim();
    }

    static void banner() {
        System.out.println("==============================================================");
        System.out.println("      RUNGTA INTERNATIONAL SKILLS UNIVERSITY");
        System.out.println("      EXAM SEATING & ROOM ALLOCATION SYSTEM  (PS No. 53)");
        System.out.println("      Developed by : Niraj Nishad   |   ERP ID : RU-25-10894");
        System.out.println("==============================================================");
    }

    static void menu() {
        System.out.println("\n-------------------- MAIN MENU --------------------");
        System.out.println(" 1. Allocate seat to a student");
        System.out.println(" 2. Display seating (all rooms)");
        System.out.println(" 3. Search student by ID");
        System.out.println(" 4. View waiting queue");
        System.out.println(" 5. Cancel allocation / withdraw (promotes waiting student)");
        System.out.println(" 6. Allocation summary");
        System.out.println(" 7. Room connections (graph: adjacency, BFS, DFS, path)");
        System.out.println(" 8. Sorted student report (merge sort)");
        System.out.println(" 9. Run test cases");
        System.out.println(" 0. Save & Exit");
        System.out.println("---------------------------------------------------");
    }

    static void displayAll(SeatingSystem sys) {
        System.out.println("\n----------- SEATING PLAN -----------");
        for (int r = 0; r < ROOMS; r++) { sys.displayRoom(r); System.out.println(); }
    }

    static void summary(SeatingSystem sys) {
        System.out.println("\n----------- ALLOCATION SUMMARY -----------");
        int occ = 0, cap = ROOMS * ROWS * COLS;
        for (int r = 0; r < ROOMS; r++) {
            int free = sys.freeSeats(r), used = ROWS * COLS - free;
            occ += used;
            System.out.println("Room " + (r + 1) + ": " + used + " occupied, " + free + " free (capacity " + ROWS * COLS + ")");
        }
        System.out.println("Total seats      : " + cap);
        System.out.println("Occupied seats   : " + occ);
        System.out.println("Free seats       : " + (cap - occ));
        System.out.println("Waiting students : " + sys.waiting.size());
        System.out.println("Next student ID  : E" + sys.nextId);
    }

    static void graphMenu(SeatingSystem sys) {
        System.out.println("\n----------- ROOM CONNECTIONS (Graph) -----------");
        sys.graph.print();
        printOrder("BFS from Room 1", sys.graph.bfsOrder(0));
        printOrder("DFS from Room 1", sys.graph.dfsOrder(0));
        String a = ask("Find shortest path - from room (1-" + ROOMS + ", Enter to skip): ");
        if (a == null || a.isEmpty()) return;
        String b = ask("                    to room (1-" + ROOMS + "): ");
        try {
            int x = Integer.parseInt(a) - 1, y = Integer.parseInt(b) - 1;
            if (x < 0 || y < 0 || x >= ROOMS || y >= ROOMS) { System.out.println("Invalid room number."); return; }
            int[] p = sys.graph.shortestPath(x, y);
            if (p == null) System.out.println("No path between those rooms.");
            else printOrder("Shortest path (" + (p.length - 1) + " hop(s))", p);
        } catch (Exception e) { System.out.println("Invalid input."); }
    }

    static void printOrder(String title, int[] order) {
        StringBuilder sb = new StringBuilder(title + ": ");
        for (int i = 0; i < order.length; i++) sb.append(i > 0 ? " -> " : "").append("Room ").append(order[i] + 1);
        System.out.println(sb);
    }

    public static void main(String[] args) {
        in = new Scanner(System.in);
        banner();
        if (args.length > 0 && args[0].equalsIgnoreCase("--test")) { runTests(); return; }

        SeatingSystem sys = new SeatingSystem();
        sys.load(DATA_FILE);

        boolean running = true;
        while (running) {
            menu();
            String choice = ask("Enter your choice: ");
            if (choice == null) choice = "0";          // end of input -> save & exit
            switch (choice) {
                case "1": {
                    String name = ask("Enter student name: ");
                    if (name == null) break;
                    name = name.replace("|", " ").trim();
                    if (name.isEmpty()) { System.out.println("Invalid input: name cannot be empty."); break; }
                    System.out.println(sys.allocate(name));
                    if (sys.waiting.peek() == sys.lastStudent)
                        System.out.println("(Student ID: " + sys.lastStudent.id + ", waiting position: "
                                + sys.waiting.positionOf(sys.lastStudent.id) + ")");
                    break;
                }
                case "2": displayAll(sys); break;
                case "3": {
                    String id = ask("Enter student ID (e.g. E101): ");
                    if (id == null) break;
                    if (id.isEmpty()) System.out.println("Invalid input: ID cannot be empty.");
                    else System.out.println(sys.search(id));
                    break;
                }
                case "4": {
                    System.out.println("\n----------- WAITING QUEUE (front -> back) -----------");
                    if (sys.waiting.isEmpty()) System.out.println("Waiting queue is empty.");
                    for (int i = 1; i <= sys.waiting.size(); i++) {
                        Student s = sys.waiting.get(i);
                        System.out.println(i + ". " + s.id + " | " + s.name);
                    }
                    break;
                }
                case "5": {
                    String id = ask("Enter student ID to cancel: ");
                    if (id == null) break;
                    if (id.isEmpty()) System.out.println("Invalid input: ID cannot be empty.");
                    else System.out.println(sys.cancel(id));
                    break;
                }
                case "6": summary(sys); break;
                case "7": graphMenu(sys); break;
                case "8": {
                    Student[] sorted = sys.sortedSeated();
                    System.out.println("\n----------- SORTED STUDENT REPORT -----------");
                    if (sorted.length == 0) System.out.println("No students allocated yet.");
                    for (int i = 0; i < sorted.length; i++) {
                        int[] l = sys.locate(sorted[i].id);
                        System.out.println(sorted[i].id + " | " + sorted[i].name + " | Room " + (l[0] + 1)
                                + " | Seat " + SeatingSystem.seatLabel(l[1], l[2]));
                    }
                    break;
                }
                case "9": runTests(); break;
                case "0": running = false; break;
                default: System.out.println("Invalid choice. Please enter a number from 0 to 9.");
            }
        }
        sys.save(DATA_FILE);
        System.out.println("Thank you for using the Exam Seating & Room Allocation System. Goodbye!");
    }
}
