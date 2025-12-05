package ch.multispace.backend.game;

import ch.multispace.backend.model.Invader;
import ch.multispace.backend.model.Shot;
import ch.multispace.backend.model.InvaderBullet;
import ch.multispace.backend.model.Ufo;
import ch.multispace.backend.model.ShieldCell;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GameRoom: server-side authoritative game state for one multiplayer room.
 * - players, invaders, shields, invader bullets, UFO
 * - level progression & difficulty scaling
 * - handles input, updates, collisions and state broadcast
 */
public class GameRoom {
    private final UUID id;
    protected static final int MAX_PLAYERS = 2;

    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Use an ObjectMapper configured to serialize fields (works with Lombok or plain POJOs)
    private final ObjectMapper mapper = new ObjectMapper()
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    // Game entities
    private final List<Invader> invaders = Collections.synchronizedList(new ArrayList<>());
    private final List<InvaderBullet> invaderBullets = Collections.synchronizedList(new ArrayList<>());
    private final List<ShieldCell> shields = Collections.synchronizedList(new ArrayList<>());

    private Ufo ufo = null;

    // layout & tuning
    private static final int COLS = 11;
    private static final int ROWS = 5;
    private static final int WIDTH = 480;
    private static final int HEIGHT = 600; // now used for bounds
    private int invaderDir = 1;
    private double invaderSpeed = 14.0;
    private int level = 1;

    // timers & rates (in seconds)
    private double invaderShootAccumulator = 0.0;
    private double invaderShootInterval = 2.5; // seconds - decreases with level
    private double ufoAccumulator = 0.0;
    private double nextUfoInSeconds = 20 + Math.random() * 20; // random 20-40s initial
    private boolean gameOver = false;

    private boolean closed = false;
    // track last active so external cleanup (if needed) can inspect idle time
    private Instant lastActiveAt = Instant.now();

    // Keep last known scores per userId so we can persist even after players disconnect
    private final Map<String, Long> scoreSnapshot = new ConcurrentHashMap<>();
    private volatile boolean scoresPersisted = false;

    // -------------------------
    // INIT
    // -------------------------
    public GameRoom() { this(UUID.randomUUID()); }

    public GameRoom(UUID id) {
        this.id = id;
        initInvaders();
        initShields();
        adjustInvaderSpeed();
        GameLoop.registerRoom(this);
    }

    private void markActive() {
        lastActiveAt = Instant.now();
    }
    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    // -------------------
    // Public helpers
    // -------------------
    public boolean isFull() { return players.size() >= MAX_PLAYERS; }
    public UUID getRoomId() { return id; }
    public boolean isClosed() { return closed; }
    public boolean isEmpty() { return players.isEmpty() && sessions.isEmpty(); }
    public boolean isScoresPersisted() { return scoresPersisted; }
    public void markScoresPersisted() { this.scoresPersisted = true; }

    /**
     * Return a copy of last known scores mapped by user UUID.
     */
    public Map<java.util.UUID, Long> getScoresSnapshotUuidMap() {
        Map<java.util.UUID, Long> out = new HashMap<>();
        for (Map.Entry<String, Long> e : scoreSnapshot.entrySet()) {
            try {
                out.put(java.util.UUID.fromString(e.getKey()), e.getValue());
            } catch (IllegalArgumentException ignored) { }
        }
        return out;
    }

    // -------------------------
    // ROOM LIFECYCLE
    // -------------------------
    private synchronized void closeRoom() {
        closed = true;

        GameLoop.unregisterRoom(this);

        players.clear();
        sessions.clear();
        invaders.clear();
        invaderBullets.clear();
        shields.clear();
        ufo = null;
    }

    // -------------------------
    // PLAYER / SESSION MGMT
    // -------------------------
    public synchronized void addPlayer(String userId, String username, WebSocketSession session) {
        if (closed || isFull()) return;

        sessions.add(session);
        players.putIfAbsent(userId, new Player(userId, username, session, WIDTH / 2.0 + players.size() * 30));
        markActive();
    }

    /**
     * Remove the Player object associated with the given userId.
     * Also removes the player's WebSocket session from sessions set.
     * If the room becomes empty after removal, resetRoom() is called.
     *
     * @param userId the id of the player to remove
     * @return true if a player was removed
     */
    public synchronized boolean removePlayer(String userId) {
        Player p = players.remove(userId);
        if (p == null) return false;

        sessions.remove(p.session);

        // snapshot score before fully removing
        scoreSnapshot.put(userId, p.score);

        markActive();

        if (isEmpty()) closeRoom();

        return true;
    }

    /**
     * Remove a session (called when WebSocket closes). Returns userId that was removed (or null).
     * This method removes the session from sessions set and any Player that referenced it.
     * If room becomes empty, resetRoom() is called.
     */
    public synchronized String removeSession(WebSocketSession s) {
        sessions.remove(s);

        String removedUserId = null;

        Iterator<Map.Entry<String, Player>> it = players.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Player> e = it.next();
            Player p = e.getValue();
            if (p.session == s) {
                removedUserId = e.getKey();
                // snapshot score
                scoreSnapshot.put(removedUserId, p.score);
                it.remove();
                break;
            }
        }

        markActive();

        if (isEmpty()) closeRoom();

        return removedUserId;
    }

    // -------------------------
    // INPUT
    // -------------------------
    public void handleInput(String userId, boolean left, boolean right, boolean fire) {
        if (closed || gameOver) return;

        Player p = players.get(userId);

        if (p == null) return;

        p.inputLeft = left;
        p.inputRight = right;
        if (fire) p.requestFire = true;

        markActive();
    }

    // -------------------------
    // UPDATE LOOP
    // -------------------------
    public void update(double dt) {
        if (closed) return;

        if (gameOver) {
            broadcastState();
            return;
        }

        updatePlayers(dt);
        updateInvaders(dt);
        updateInvaderShooting(dt);
        updateInvaderBullets(dt);
        updateUfo(dt);
        handleCollisions();
        checkPlayerLives();

        broadcastState();
    }

    // -------------------------
    // PLAYERS
    // -------------------------
    private void updatePlayers(double dt) {
        for (Player p : players.values()) {
            double vx = 0;
            if (p.inputLeft) vx -= p.speed;
            if (p.inputRight) vx += p.speed;

            p.x = Math.max(16, Math.min(WIDTH - p.w - 16, p.x + vx * dt));

            if (p.requestFire && p.canShoot) {
                p.fire();
            }
            p.updateShot(dt);
        }
    }

    // --------------------
    // Invaders
    // --------------------
    private void updateInvaders(double dt) {
        if (invaders.stream().noneMatch(i -> i.alive)) {
            level++;
            adjustInvaderSpeed();
            initInvaders();

            invaderBullets.clear();
            ufo = null;
            nextUfoInSeconds = 15 + Math.random() * 25;
            return;
        }

        var alive = invaders.stream().filter(i -> i.alive).toList();
        double minX = alive.stream().mapToDouble(i -> i.x).min().orElse(0);
        double maxX = alive.stream().mapToDouble(i -> i.x + i.w).max().orElse(WIDTH);

        boolean stepDown =
                (invaderDir > 0 && maxX + invaderSpeed * dt >= WIDTH - 16) ||
                        (invaderDir < 0 && minX - invaderSpeed * dt <= 16);

        if (stepDown) {
            for (Invader i : alive) i.y += 14;
            invaderDir *= -1;
        } else {
            for (Invader i : alive) i.x += invaderDir * invaderSpeed * dt;
        }

        // Check if any invader reached the ground (players' row)
        // Players stand at y ≈ 560 (see Player.y). If any invader bottom crosses this line, end game for all.
        boolean invaderReachedGround = alive.stream().anyMatch(i -> (i.y + i.h) >= 560);
        if (invaderReachedGround) {
            // Set all players to dead and flag game over
            for (Player p : players.values()) {
                p.lives = 0;
            }
            gameOver = true;
        }
    }

    private void adjustInvaderSpeed() {
        invaderSpeed = Math.min(120.0, 18.0 + (level - 1) * 3.5);
        invaderShootInterval = Math.max(0.4, 2.5 - (level - 1) * 0.12);
    }

    private void updateInvaderShooting(double dt) {
        invaderShootAccumulator += dt;
        double interval = Math.max(0.4, invaderShootInterval / (1 + (level - 1) * 0.08));

        if (invaderShootAccumulator >= interval) {
            invaderShootAccumulator = 0;
            spawnInvaderBullet();
        }
    }

    // -------------------------
    // BULLETS
    // -------------------------
    private void spawnInvaderBullet() {
        // choose random alive invaders from bottom of each column for more canonical behavior
        // build columns -> bottom-most invader per column
        Map<Integer, Invader> bottom = new HashMap<>();
        synchronized (invaders) {
            for (Invader inv : invaders) {
                if (!inv.alive) continue;
                int colIndex = (int) Math.round((inv.x - 60) / 36.0); // approximate column
                Invader current = bottom.get(colIndex);
                if (current == null || inv.y > current.y) bottom.put(colIndex, inv);
            }
        }
        if (bottom.isEmpty()) return;

        Invader shooter = new ArrayList<>(bottom.values())
                .get(new Random().nextInt(bottom.size()));

        invaderBullets.add(
                new InvaderBullet(
                        shooter.x + shooter.w / 2 - 1,
                        shooter.y + shooter.h,
                        2, 8, 200
                )
        );
    }

    private void updateInvaderBullets(double dt) {
        invaderBullets.removeIf(b -> {
            b.y += b.vy * dt;
            return b.y > HEIGHT + 50;
        });
    }

    // --------------------
    // Shields
    // --------------------
    private void initShields() {
        shields.clear();
        // Spawn 4 bunkers, each bunker is a grid of ShieldCell blocks
        int bunkers = 3;
        int blockSize = 8;
        int bunkerWidthBlocks = 6;
        int bunkerHeightBlocks = 2;

        double margin = 30;
        double usableWidth = WIDTH - 2 * margin;
        double spacing = usableWidth / (bunkers - 1);

        for (int b = 0; b < bunkers; b++) {
            double baseX = margin + b * spacing - (bunkerWidthBlocks * blockSize) / 2.0;
            double baseY = HEIGHT - 140;
            for (int bx = 0; bx < bunkerWidthBlocks; bx++) {
                for (int by = 0; by < bunkerHeightBlocks; by++) {
                    ShieldCell cell = new ShieldCell();
                    cell.setX(baseX + bx * blockSize);
                    cell.setY(baseY + by * blockSize);
                    cell.setW(blockSize);
                    cell.setH(blockSize);
                    cell.setHp(3);
                    shields.add(cell);
                }
            }
        }
    }

    // --------------------
    // UFO
    // --------------------
    private void updateUfo(double dt) {
        ufoAccumulator += dt;
        if (ufo == null) {
            if (ufoAccumulator >= nextUfoInSeconds) {
                boolean fromLeft = new Random().nextBoolean();
                ufo = new Ufo(
                        fromLeft ? -60 : WIDTH + 60,
                        40,
                        48, 20,
                        fromLeft ? 120 : -120,
                        200
                );
                ufoAccumulator = 0;
                nextUfoInSeconds = 25 + Math.random() * 30;
            }
        } else {
            ufo.x += ufo.vx * dt;
            if (ufo.x < -200 || ufo.x > WIDTH + 200) {
                ufo = null;
                ufoAccumulator = 0;
            }
        }
    }

    // --------------------
    // Collisions
    // --------------------
    private void handleCollisions() {
        // Player shots hit invaders, UFO, shields
        for (Player p : players.values()) {
            if (p.shot == null) continue;
            Shot s = p.shot;
            // check invaders
            boolean broke = false;
            synchronized (invaders) {
                for (Invader inv : invaders) {
                    if (!inv.alive) continue;
                    if (rectOverlap(s.x, s.y, s.w, s.h, inv.x, inv.y, inv.w, inv.h)) {
                        inv.alive = false;
                        p.shot = null;
                        p.score += 10L * Math.max(1, level);
                        broke = true;
                        break;
                    }
                }
            }
            if (broke) continue;

            // check UFO
            if (ufo != null && rectOverlap(s.x, s.y, s.w, s.h, ufo.x, ufo.y, ufo.w, ufo.h)) {
                p.shot = null;
                p.score += ufo.scoreValue;
                ufo = null;
                continue;
            }

            // check shields
            for (ShieldCell cell : shields) {
                if (cell.getHp() <= 0) continue;
                if (rectOverlap(s.x, s.y, s.w, s.h, cell.getX(), cell.getY(), cell.getW(), cell.getH())) {
                    // damage shield
                    cell.setHp(cell.getHp() - 1);
                    p.shot = null;
                    break;
                }
            }
        }

        // Invader bullets hit players or shields
        List<InvaderBullet> removeBullets = new ArrayList<>();
        synchronized (invaderBullets) {
            for (InvaderBullet b : invaderBullets) {
                // players
                for (Player p : players.values()) {
                    if (rectOverlap(b.x, b.y, b.w, b.h, p.x, p.y, p.w, p.h)) {
                        removeBullets.add(b);
                        p.lives--;
                        // optional respawn at center
                        p.x = WIDTH / 2.0;
                    }
                }
                // shields
                for (ShieldCell cell : shields) {
                    if (cell.getHp() <= 0) continue;
                    if (rectOverlap(b.x, b.y, b.w, b.h, cell.getX(), cell.getY(), cell.getW(), cell.getH())) {
                        removeBullets.add(b);
                        cell.setHp(cell.getHp() - 1);
                    }
                }
            }
            invaderBullets.removeAll(removeBullets);
        }
    }

    private boolean rectOverlap(double ax, double ay, double aw, double ah,
                                double bx, double by, double bw, double bh) {
        return ax < bx + bw && ax + aw > bx &&
                ay < by + bh && ay + ah > by;
    }

    private void checkPlayerLives() {
        if (players.values().stream().noneMatch(p -> p.lives > 0)) {
            gameOver = true;
        }
    }

    // --------------------
    // Broadcast state to clients
    // --------------------
    private void broadcastState() {
        if (sessions.isEmpty()) return;

        try {
            Map<String, Object> state = new HashMap<>();

            // Players
            List<Map<String, Object>> playersList = new ArrayList<>();
            for (Player p : players.values()) {
                Map<String, Object> pd = new HashMap<>();
                pd.put("userId", p.userId);
                pd.put("username", p.username);
                pd.put("x", p.x);
                pd.put("y", p.y);
                pd.put("w", p.w);
                pd.put("h", p.h);
                pd.put("score", p.score);
                pd.put("lives", p.lives);
                pd.put("shot", p.shot); // can be null
                playersList.add(pd);

                // keep score snapshot updated
                scoreSnapshot.put(p.userId, p.score);
            }

            state.put("players", playersList);
            state.put("invaders", invaders);
            state.put("invaderBullets", invaderBullets);
            state.put("shields", shields);
            state.put("ufo", ufo); // can be null
            state.put("level", level);
            state.put("gameOver", gameOver);

            Map<String, Object> msgMap = new HashMap<>();
            msgMap.put("type", "state");
            msgMap.put("payload", state);

            String msg = mapper.writeValueAsString(msgMap);

            for (WebSocketSession s : sessions) {
                if (s.isOpen()) s.sendMessage(new TextMessage(msg));
            }

        } catch (IOException _) {
            // No code
        }
    }

    // --------------------
    // Invaders / init
    // --------------------
    private void initInvaders() {
        invaders.clear();
        // keep spacing consistent with front-end
        double startX = 60;
        double startY = 80;
        double spacingX = 36;
        double spacingY = 28;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                invaders.add(new Invader(
                        startX + c * spacingX,
                        startY + r * spacingY,
                        24.0,
                        16.0,
                        1
                ));
            }
        }
    }

    // --------------------
    // Helper classes
    // --------------------
    private static class Player {
        final String userId;
        final String username;
        final WebSocketSession session;
        double x;
        double y = 560;
        double w = 32;
        double h = 16;
        double speed = 180;
        boolean inputLeft;
        boolean inputRight;
        boolean requestFire;
        boolean canShoot = true;
        Shot shot;
        long score = 0;
        int lives = 3;

        Player(String userId, String username, WebSocketSession session, double x) {
            this.userId = userId;
            this.username = username;
            this.session = session;
            this.x = x;
        }

        void fire() {
            if (!canShoot) return;
            shot = new Shot(x + w / 2 - 1, y - 8, 2, 8, -360.0);
            canShoot = false;
            requestFire = false;
            // re-enable shooting after 500 ms
            new Timer().schedule(new TimerTask() {
                @Override public void run() { canShoot = true; }
            }, 500);
        }

        void updateShot(double dt) {
            if (shot == null) return;
            shot.setY(shot.getY() + shot.getVy() * dt);
            if (shot.getY() < -10) shot = null;
        }
    }
}
