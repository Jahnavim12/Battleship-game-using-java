import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Battleship Game — Java Swing UI
 * Compile:  javac Battleship.java
 * Run:      java Battleship
 */
public class Battleship extends JFrame {

    // ── Constants ──────────────────────────────────────────────────────────────
    static final int GRID = 10;
    static final Color BG        = new Color(10,  22,  40);
    static final Color PANEL     = new Color(15,  42,  64);
    static final Color GRID_CLR  = new Color(26,  58,  85);
    static final Color ACCENT    = new Color(0,  212, 255);
    static final Color HIT_CLR   = new Color(255, 68,  68);
    static final Color MISS_CLR  = new Color(51, 102, 153);
    static final Color SHIP_CLR  = new Color(74, 158, 255);
    static final Color TEXT_CLR  = new Color(200, 230, 240);
    static final Color GOLD      = new Color(255, 215,   0);

    static final String[] SHIP_NAMES = {"Carrier","Battleship","Cruiser","Submarine","Destroyer"};
    static final int[]    SHIP_SIZES = {5, 4, 3, 3, 2};

    // ── State ──────────────────────────────────────────────────────────────────
    enum Phase { SETUP, BATTLE, OVER }
    Phase phase = Phase.SETUP;
    boolean horizontal = true;
    int placingShip = 0;

    int[][] playerGrid = new int[GRID][GRID]; // 0=empty, N=ship id, -1=hit, -2=miss
    int[][] enemyGrid  = new int[GRID][GRID];
    int[] playerHits   = new int[SHIP_NAMES.length];
    int[] enemyHits    = new int[SHIP_NAMES.length];
    boolean[] playerSunk = new boolean[SHIP_NAMES.length];
    boolean[] enemySunk  = new boolean[SHIP_NAMES.length];
    List<int[]>[] playerShipCells;
    List<int[]>[] enemyShipCells;

    int totalShots = 0, playerHitCount = 0;

    // CPU hunt/target AI
    String cpuMode = "hunt";
    int[] cpuTarget = null, cpuStart = null, cpuDir = null;
    List<int[]> cpuDirTried = new ArrayList<>();
    int[][] DIRS = {{0,1},{0,-1},{1,0},{-1,0}};
    Random rng = new Random();

    // ── UI Components ──────────────────────────────────────────────────────────
    JButton[][] playerCells = new JButton[GRID][GRID];
    JButton[][] enemyCells  = new JButton[GRID][GRID];
    JLabel messageLabel, shotsLabel, hitsLabel, accLabel, enemyFleetLabel;
    JLabel[] playerShipStatus = new JLabel[SHIP_NAMES.length];
    JLabel[] enemyShipStatus  = new JLabel[SHIP_NAMES.length];
    JButton startBtn, orientBtn, autoBtn, newGameBtn;
    JLabel setupInfo;

    // ── Main ───────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Battleship().setVisible(true));
    }

    @SuppressWarnings("unchecked")
    public Battleship() {
        super("⚓ BATTLESHIP — Naval Combat");
        playerShipCells = new List[SHIP_NAMES.length];
        enemyShipCells  = new List[SHIP_NAMES.length];
        for (int i = 0; i < SHIP_NAMES.length; i++) {
            playerShipCells[i] = new ArrayList<>();
            enemyShipCells[i]  = new ArrayList<>();
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBackground(BG);
        buildUI();
        pack();
        setMinimumSize(new Dimension(900, 640));
        setLocationRelativeTo(null);
        resetGame();
    }

    // ── UI Build ───────────────────────────────────────────────────────────────
    void buildUI() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        root.add(buildTopBar(),   BorderLayout.NORTH);
        root.add(buildCenter(),   BorderLayout.CENTER);
        root.add(buildControls(), BorderLayout.SOUTH);

        add(root);
    }

    JPanel buildTopBar() {
        JPanel top = new JPanel(new BorderLayout(6, 4));
        top.setBackground(BG);

        JLabel title = new JLabel("⚓  BATTLESHIP", SwingConstants.CENTER);
        title.setFont(new Font("Monospaced", Font.BOLD, 26));
        title.setForeground(ACCENT);

        JPanel stats = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 4));
        stats.setBackground(PANEL);
        stats.setBorder(BorderFactory.createLineBorder(GRID_CLR));

        shotsLabel      = statLabel("SHOTS: 0");
        hitsLabel       = statLabel("HITS: 0");
        accLabel        = statLabel("ACC: —");
        enemyFleetLabel = statLabel("ENEMY SHIPS: 5");
        hitsLabel.setForeground(HIT_CLR);
        enemyFleetLabel.setForeground(HIT_CLR);

        stats.add(shotsLabel);
        stats.add(sep());
        stats.add(hitsLabel);
        stats.add(sep());
        stats.add(accLabel);
        stats.add(sep());
        stats.add(enemyFleetLabel);

        messageLabel = new JLabel("Place your ships to begin battle", SwingConstants.CENTER);
        messageLabel.setFont(new Font("Monospaced", Font.PLAIN, 13));
        messageLabel.setForeground(ACCENT);
        messageLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0,212,255,80)),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        messageLabel.setOpaque(true);
        messageLabel.setBackground(new Color(0,212,255,20));

        top.add(title,        BorderLayout.NORTH);
        top.add(stats,        BorderLayout.CENTER);
        top.add(messageLabel, BorderLayout.SOUTH);
        return top;
    }

    JLabel statLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Monospaced", Font.BOLD, 14));
        l.setForeground(ACCENT);
        return l;
    }

    JLabel sep() {
        JLabel s = new JLabel("|");
        s.setForeground(GRID_CLR);
        return s;
    }

    JPanel buildCenter() {
        JPanel center = new JPanel(new GridLayout(1, 2, 12, 0));
        center.setBackground(BG);
        center.add(buildBoard("YOUR FLEET",    true));
        center.add(buildBoard("ENEMY WATERS",  false));
        return center;
    }

    JPanel buildBoard(String title, boolean isPlayer) {
        JPanel wrap = new JPanel(new BorderLayout(4, 4));
        wrap.setBackground(PANEL);
        wrap.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(GRID_CLR),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
        titleLabel.setForeground(isPlayer ? SHIP_CLR : HIT_CLR);
        wrap.add(titleLabel, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(GRID + 1, GRID + 1, 2, 2));
        grid.setBackground(BG);

        // Column headers
        grid.add(emptyLabel());
        for (char ch = 'A'; ch <= 'J'; ch++) {
            JLabel lbl = new JLabel(String.valueOf(ch), SwingConstants.CENTER);
            lbl.setFont(new Font("Monospaced", Font.PLAIN, 10));
            lbl.setForeground(GRID_CLR.brighter());
            grid.add(lbl);
        }

        for (int r = 0; r < GRID; r++) {
            JLabel rowLbl = new JLabel(String.valueOf(r + 1), SwingConstants.CENTER);
            rowLbl.setFont(new Font("Monospaced", Font.PLAIN, 10));
            rowLbl.setForeground(GRID_CLR.brighter());
            grid.add(rowLbl);

            for (int c = 0; c < GRID; c++) {
                JButton btn = new JButton();
                btn.setPreferredSize(new Dimension(32, 32));
                btn.setBackground(new Color(13, 33, 55));
                btn.setBorder(BorderFactory.createLineBorder(GRID_CLR, 1));
                btn.setFocusPainted(false);
                btn.setOpaque(true);
                btn.setFont(new Font("Monospaced", Font.BOLD, 14));
                btn.setForeground(Color.WHITE);

                final int row = r, col = c;
                if (isPlayer) {
                    btn.addMouseListener(new MouseAdapter() {
                        public void mouseEntered(MouseEvent e) { hoverPreview(row, col); }
                        public void mouseExited(MouseEvent e)  { clearPreview(); }
                    });
                    btn.addActionListener(e -> onPlayerGridClick(row, col));
                    playerCells[r][c] = btn;
                } else {
                    btn.addActionListener(e -> playerShot(row, col));
                    btn.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    enemyCells[r][c] = btn;
                }
                grid.add(btn);
            }
        }

        wrap.add(grid, BorderLayout.CENTER);
        wrap.add(buildFleetStatus(isPlayer), BorderLayout.SOUTH);
        return wrap;
    }

    JPanel buildFleetStatus(boolean isPlayer) {
        JPanel p = new JPanel(new GridLayout(SHIP_NAMES.length, 1, 0, 2));
        p.setBackground(PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        for (int i = 0; i < SHIP_NAMES.length; i++) {
            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.setBackground(PANEL);
            JLabel name = new JLabel(SHIP_NAMES[i]);
            name.setFont(new Font("SansSerif", Font.PLAIN, 11));
            name.setForeground(new Color(138, 184, 208));
            JLabel status = new JLabel(SHIP_SIZES[i] + "/" + SHIP_SIZES[i], SwingConstants.RIGHT);
            status.setFont(new Font("Monospaced", Font.BOLD, 11));
            status.setForeground(new Color(0, 255, 136));
            row.add(name,   BorderLayout.WEST);
            row.add(status, BorderLayout.EAST);
            if (isPlayer) playerShipStatus[i] = status;
            else           enemyShipStatus[i]  = status;
            p.add(row);
        }
        return p;
    }

    JPanel buildControls() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        p.setBackground(BG);

        setupInfo = new JLabel("Placing: Carrier (5)");
        setupInfo.setFont(new Font("Monospaced", Font.BOLD, 13));
        setupInfo.setForeground(GOLD);

        orientBtn = navyBtn("⟳ Horizontal", ACCENT);
        orientBtn.addActionListener(e -> {
            horizontal = !horizontal;
            orientBtn.setText(horizontal ? "⟳ Horizontal" : "⟳ Vertical");
        });

        autoBtn = navyBtn("Auto-Place", GRID_CLR.brighter());
        autoBtn.addActionListener(e -> autoPlace());

        startBtn = navyBtn("⚔ START BATTLE", ACCENT);
        startBtn.setVisible(false);
        startBtn.addActionListener(e -> startGame());

        newGameBtn = navyBtn("↺ New Game", GRID_CLR.brighter());
        newGameBtn.addActionListener(e -> resetGame());

        p.add(setupInfo);
        p.add(orientBtn);
        p.add(autoBtn);
        p.add(startBtn);
        p.add(newGameBtn);
        return p;
    }

    JButton navyBtn(String text, Color fg) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setForeground(fg);
        b.setBackground(PANEL);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(fg, 1),
            BorderFactory.createEmptyBorder(6, 16, 6, 16)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 40)); }
            public void mouseExited(MouseEvent e)  { b.setBackground(PANEL); }
        });
        return b;
    }

    JLabel emptyLabel() {
        JLabel l = new JLabel();
        l.setOpaque(false);
        return l;
    }

    // ── Placement ──────────────────────────────────────────────────────────────
    void onPlayerGridClick(int r, int c) {
        if (phase != Phase.SETUP || placingShip >= SHIP_NAMES.length) return;
        if (!canPlace(playerGrid, r, c, SHIP_SIZES[placingShip], horizontal)) return;
        List<int[]> cells = getShipCells(r, c, SHIP_SIZES[placingShip], horizontal);
        for (int[] pos : cells) {
            playerGrid[pos[0]][pos[1]] = placingShip + 1;
            playerShipCells[placingShip].add(pos);
            styleCell(playerCells[pos[0]][pos[1]], "ship");
        }
        placingShip++;
        updateSetupInfo();
        clearPreview();
    }

    void hoverPreview(int r, int c) {
        if (phase != Phase.SETUP || placingShip >= SHIP_NAMES.length) return;
        clearPreview();
        List<int[]> cells = getShipCells(r, c, SHIP_SIZES[placingShip], horizontal);
        if (cells == null) return;
        boolean valid = canPlace(playerGrid, r, c, SHIP_SIZES[placingShip], horizontal);
        Color col = valid ? new Color(74, 158, 255, 140) : new Color(255, 68, 68, 100);
        for (int[] pos : cells) {
            JButton btn = playerCells[pos[0]][pos[1]];
            if (btn != null && playerGrid[pos[0]][pos[1]] == 0)
                btn.setBackground(col);
        }
    }

    void clearPreview() {
        for (int r = 0; r < GRID; r++)
            for (int c = 0; c < GRID; c++)
                if (playerGrid[r][c] == 0)
                    playerCells[r][c].setBackground(new Color(13, 33, 55));
    }

    void autoPlace() {
        if (phase != Phase.SETUP) return;
        // Clear existing
        for (int r = 0; r < GRID; r++) for (int c = 0; c < GRID; c++) playerGrid[r][c] = 0;
        for (int i = 0; i < SHIP_NAMES.length; i++) playerShipCells[i].clear();
        for (int r = 0; r < GRID; r++) for (int c = 0; c < GRID; c++) styleCell(playerCells[r][c], "empty");
        placingShip = 0;

        for (int i = 0; i < SHIP_NAMES.length; i++) {
            boolean placed = false;
            while (!placed) {
                int r = rng.nextInt(GRID), c = rng.nextInt(GRID);
                boolean h = rng.nextBoolean();
                if (canPlace(playerGrid, r, c, SHIP_SIZES[i], h)) {
                    List<int[]> cells = getShipCells(r, c, SHIP_SIZES[i], h);
                    for (int[] pos : cells) {
                        playerGrid[pos[0]][pos[1]] = i + 1;
                        playerShipCells[i].add(pos);
                        styleCell(playerCells[pos[0]][pos[1]], "ship");
                    }
                    placed = true;
                }
            }
        }
        placingShip = SHIP_NAMES.length;
        updateSetupInfo();
    }

    void placeEnemyShips() {
        for (int i = 0; i < SHIP_NAMES.length; i++) {
            boolean placed = false;
            while (!placed) {
                int r = rng.nextInt(GRID), c = rng.nextInt(GRID);
                boolean h = rng.nextBoolean();
                if (canPlace(enemyGrid, r, c, SHIP_SIZES[i], h)) {
                    List<int[]> cells = getShipCells(r, c, SHIP_SIZES[i], h);
                    for (int[] pos : cells) {
                        enemyGrid[pos[0]][pos[1]] = i + 1;
                        enemyShipCells[i].add(pos);
                    }
                    placed = true;
                }
            }
        }
    }

    boolean canPlace(int[][] grid, int r, int c, int size, boolean horiz) {
        List<int[]> cells = getShipCells(r, c, size, horiz);
        if (cells == null) return false;
        for (int[] pos : cells) {
            if (pos[0] < 0 || pos[0] >= GRID || pos[1] < 0 || pos[1] >= GRID) return false;
            if (grid[pos[0]][pos[1]] != 0) return false;
        }
        return true;
    }

    List<int[]> getShipCells(int r, int c, int size, boolean horiz) {
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int rr = horiz ? r : r + i;
            int cc = horiz ? c + i : c;
            if (rr >= GRID || cc >= GRID) return null;
            cells.add(new int[]{rr, cc});
        }
        return cells;
    }

    // ── Game Logic ─────────────────────────────────────────────────────────────
    void startGame() {
        phase = Phase.BATTLE;
        placeEnemyShips();
        startBtn.setVisible(false);
        setupInfo.setVisible(false);
        orientBtn.setVisible(false);
        autoBtn.setVisible(false);
        // Disable player board clicks
        for (int r = 0; r < GRID; r++)
            for (int c = 0; c < GRID; c++) {
                for (ActionListener al : playerCells[r][c].getActionListeners())
                    playerCells[r][c].removeActionListener(al);
                for (MouseListener ml : playerCells[r][c].getMouseListeners())
                    playerCells[r][c].removeMouseListener(ml);
            }
        setMsg("🎯 Battle started! Click enemy waters to fire!");
        updateStats();
    }

    void playerShot(int r, int c) {
        if (phase != Phase.BATTLE) return;
        JButton btn = enemyCells[r][c];
        if (btn.getBackground().equals(HIT_CLR) || btn.getBackground().equals(MISS_CLR) ||
            btn.getBackground().equals(new Color(150, 30, 30))) return;
        if (enemyGrid[r][c] < 0) return; // already shot

        totalShots++;
        int shipId = enemyGrid[r][c];
        if (shipId > 0) {
            playerHitCount++;
            enemyGrid[r][c] = -1;
            int idx = shipId - 1;
            enemyHits[idx]++;
            styleCell(btn, "hit");
            if (enemyHits[idx] >= SHIP_SIZES[idx]) {
                enemySunk[idx] = true;
                for (int[] pos : enemyShipCells[idx]) styleCell(enemyCells[pos[0]][pos[1]], "sunk");
                setMsg("💥 You sank their " + SHIP_NAMES[idx] + "!");
                enemyShipStatus[idx].setText("SUNK");
                enemyShipStatus[idx].setForeground(HIT_CLR);
            } else {
                setMsg("🔥 Direct hit on " + SHIP_NAMES[idx] + "!");
            }
        } else {
            enemyGrid[r][c] = -2;
            styleCell(btn, "miss");
            setMsg("🌊 Miss! Enemy turn...");
        }
        updateStats();
        if (checkWin()) return;

        // Disable enemy board during CPU turn
        setEnemyBoardEnabled(false);
        Timer timer = new Timer(900, e -> { cpuTurn(); setEnemyBoardEnabled(phase == Phase.BATTLE); });
        timer.setRepeats(false);
        timer.start();
    }

    void cpuTurn() {
        int[] move;
        if (cpuMode.equals("target") && cpuTarget != null) {
            move = cpuGetTargetMove();
            if (move == null) {
                cpuMode = "hunt"; cpuTarget = null; cpuDir = null; cpuStart = null; cpuDirTried.clear();
                move = cpuHuntMove();
            }
        } else {
            move = cpuHuntMove();
        }
        int r = move[0], c = move[1];
        int shipId = playerGrid[r][c];
        JButton btn = playerCells[r][c];
        if (shipId > 0) {
            playerGrid[r][c] = -1;
            int idx = shipId - 1;
            playerHits[idx]++;
            styleCell(btn, "hit");
            if (cpuTarget == null) { cpuTarget = new int[]{r,c}; cpuStart = new int[]{r,c}; cpuMode = "target"; cpuDirTried.clear(); }
            if (playerHits[idx] >= SHIP_SIZES[idx]) {
                playerSunk[idx] = true;
                for (int[] pos : playerShipCells[idx]) styleCell(playerCells[pos[0]][pos[1]], "sunk");
                setMsg("💀 Enemy sank your " + SHIP_NAMES[idx] + "!");
                playerShipStatus[idx].setText("SUNK");
                playerShipStatus[idx].setForeground(HIT_CLR);
                cpuMode = "hunt"; cpuTarget = null; cpuDir = null; cpuStart = null; cpuDirTried.clear();
            } else {
                setMsg("⚠️ Your " + SHIP_NAMES[idx] + " was hit!");
            }
        } else {
            playerGrid[r][c] = -2;
            styleCell(btn, "miss");
            cpuDir = null;
        }
        checkWin();
    }

    int[] cpuHuntMove() {
        List<int[]> avail = new ArrayList<>();
        for (int r = 0; r < GRID; r++) for (int c = 0; c < GRID; c++)
            if (playerGrid[r][c] >= 0 && (r + c) % 2 == 0) avail.add(new int[]{r, c});
        if (avail.isEmpty())
            for (int r = 0; r < GRID; r++) for (int c = 0; c < GRID; c++)
                if (playerGrid[r][c] >= 0) avail.add(new int[]{r, c});
        return avail.get(rng.nextInt(avail.size()));
    }

    int[] cpuGetTargetMove() {
        if (cpuDir == null) {
            List<int[]> untried = new ArrayList<>();
            for (int[] d : DIRS) {
                boolean tried = false;
                for (int[] td : cpuDirTried) if (td[0]==d[0] && td[1]==d[1]) { tried=true; break; }
                if (!tried) untried.add(d);
            }
            if (untried.isEmpty()) return null;
            cpuDir = untried.get(rng.nextInt(untried.size()));
            cpuDirTried.add(cpuDir);
        }
        int[] base = cpuStart != null ? cpuStart : cpuTarget;
        int r = base[0] + cpuDir[0], c = base[1] + cpuDir[1];
        while (r >= 0 && r < GRID && c >= 0 && c < GRID) {
            if (playerGrid[r][c] >= 0) { cpuStart = new int[]{r - cpuDir[0], c - cpuDir[1]}; return new int[]{r, c}; }
            r += cpuDir[0]; c += cpuDir[1];
        }
        cpuDir = null;
        return cpuGetTargetMove();
    }

    boolean checkWin() {
        boolean enemyAllSunk = true, playerAllSunk = true;
        for (int i = 0; i < SHIP_NAMES.length; i++) {
            if (!enemySunk[i])  enemyAllSunk  = false;
            if (!playerSunk[i]) playerAllSunk = false;
        }
        if (enemyAllSunk)  { endGame(true);  return true; }
        if (playerAllSunk) { endGame(false); return true; }
        return false;
    }

    void endGame(boolean win) {
        phase = Phase.OVER;
        int acc = totalShots > 0 ? playerHitCount * 100 / totalShots : 0;
        String title = win ? "VICTORY!" : "DEFEAT";
        String msg   = win
            ? "You sank the entire enemy fleet!\nAccuracy: " + acc + "% (" + playerHitCount + "/" + totalShots + " shots)"
            : "Your fleet has been destroyed!\nAccuracy: " + acc + "% (" + playerHitCount + "/" + totalShots + " shots)";
        int opt = JOptionPane.showOptionDialog(this,
            msg, title,
            JOptionPane.DEFAULT_OPTION, win ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE,
            null, new String[]{"Play Again", "Quit"}, "Play Again");
        if (opt == 0) resetGame();
        else System.exit(0);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    void styleCell(JButton btn, String state) {
        switch (state) {
            case "empty" -> { btn.setBackground(new Color(13,33,55)); btn.setText(""); }
            case "ship"  -> btn.setBackground(new Color(30,70,120));
            case "hit"   -> { btn.setBackground(new Color(180,40,40)); btn.setText("✕"); btn.setForeground(HIT_CLR); }
            case "miss"  -> { btn.setBackground(new Color(30,55,80));  btn.setText("·"); btn.setForeground(MISS_CLR); }
            case "sunk"  -> { btn.setBackground(new Color(120,20,20)); btn.setText("✕"); btn.setForeground(new Color(255,120,120)); }
        }
    }

    void setMsg(String msg) { messageLabel.setText(msg); }

    void updateStats() {
        int acc = totalShots > 0 ? playerHitCount * 100 / totalShots : 0;
        int remaining = 0;
        for (boolean s : enemySunk) if (!s) remaining++;
        shotsLabel.setText("SHOTS: " + totalShots);
        hitsLabel.setText("HITS: " + playerHitCount);
        accLabel.setText("ACC: " + (totalShots > 0 ? acc + "%" : "—"));
        enemyFleetLabel.setText("ENEMY SHIPS: " + remaining);
    }

    void updateSetupInfo() {
        if (placingShip >= SHIP_NAMES.length) {
            setupInfo.setText("All ships placed — ready!");
            startBtn.setVisible(true);
        } else {
            setupInfo.setText("Placing: " + SHIP_NAMES[placingShip] + " (" + SHIP_SIZES[placingShip] + ")");
        }
        renderShipStatus();
    }

    void renderShipStatus() {
        for (int i = 0; i < SHIP_NAMES.length; i++) {
            int hp = SHIP_SIZES[i] - playerHits[i];
            if (playerSunk[i]) { playerShipStatus[i].setText("SUNK"); playerShipStatus[i].setForeground(HIT_CLR); }
            else if (i < placingShip) { playerShipStatus[i].setText(hp + "/" + SHIP_SIZES[i]); playerShipStatus[i].setForeground(new Color(0,255,136)); }
            else { playerShipStatus[i].setText("—"); playerShipStatus[i].setForeground(GRID_CLR.brighter()); }
            if (!enemySunk[i]) { enemyShipStatus[i].setText(SHIP_SIZES[i] + "/" + SHIP_SIZES[i]); enemyShipStatus[i].setForeground(new Color(0,255,136)); }
        }
    }

    void setEnemyBoardEnabled(boolean en) {
        for (int r = 0; r < GRID; r++)
            for (int c = 0; c < GRID; c++)
                enemyCells[r][c].setEnabled(en);
    }

    @SuppressWarnings("unchecked")
    void resetGame() {
        phase = Phase.SETUP; horizontal = true; placingShip = 0;
        totalShots = 0; playerHitCount = 0;
        cpuMode = "hunt"; cpuTarget = null; cpuDir = null; cpuStart = null; cpuDirTried.clear();
        playerGrid = new int[GRID][GRID]; enemyGrid = new int[GRID][GRID];
        playerHits = new int[SHIP_NAMES.length]; enemyHits = new int[SHIP_NAMES.length];
        playerSunk = new boolean[SHIP_NAMES.length]; enemySunk = new boolean[SHIP_NAMES.length];
        playerShipCells = new List[SHIP_NAMES.length]; enemyShipCells = new List[SHIP_NAMES.length];
        for (int i = 0; i < SHIP_NAMES.length; i++) {
            playerShipCells[i] = new ArrayList<>(); enemyShipCells[i] = new ArrayList<>();
        }

        if (playerCells[0][0] != null)
            for (int r = 0; r < GRID; r++)
                for (int c = 0; c < GRID; c++) { styleCell(playerCells[r][c], "empty"); styleCell(enemyCells[r][c], "empty"); }

        if (startBtn != null) { startBtn.setVisible(false); setupInfo.setVisible(true); orientBtn.setVisible(true); autoBtn.setVisible(true); }
        if (setupInfo != null) setupInfo.setText("Placing: " + SHIP_NAMES[0] + " (" + SHIP_SIZES[0] + ")");
        if (orientBtn != null) orientBtn.setText("⟳ Horizontal");
        if (messageLabel != null) setMsg("Place your ships to begin battle");
        updateStats();
        renderShipStatus();
        setEnemyBoardEnabled(false);

        // Re-attach player board listeners
        for (int r = 0; r < GRID; r++)
            for (int c = 0; c < GRID; c++) {
                for (ActionListener al : playerCells[r][c].getActionListeners())
                    playerCells[r][c].removeActionListener(al);
                for (MouseListener ml : playerCells[r][c].getMouseListeners())
                    playerCells[r][c].removeMouseListener(ml);
                final int row = r, col = c;
                playerCells[r][c].addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { hoverPreview(row, col); }
                    public void mouseExited(MouseEvent e)  { clearPreview(); }
                });
                playerCells[r][c].addActionListener(e -> onPlayerGridClick(row, col));
            }
    }
}
