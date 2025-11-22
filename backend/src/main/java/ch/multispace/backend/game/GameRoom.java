package ch.multispace.backend.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameRoom {
    protected static final int MAX_PLAYERS = 2;
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ObjectMapper mapper = new ObjectMapper();

    private final List<Map<String,Object>> invaders = Collections.synchronizedList(new ArrayList<>());
    private int cols = 11, rows = 5;
    private int width = 480, height = 640;
    private int invaderDir = 1;
    private double invaderSpeed = 18.0;
    private int level = 1;

    public GameRoom() {
        initInvaders();
        GameLoop.registerRoom(this);
    }

    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    public GameRoom getRoom() { return this; }

    public void addPlayer(String userId, String username, WebSocketSession session) {
        if (isFull()) return;
        sessions.add(session);
        players.putIfAbsent(userId, new Player(userId, username, session, width / 2.0 + players.size() * 30));
    }

    public void removeSession(WebSocketSession s) {
        sessions.remove(s);
        players.values().removeIf(p -> p.session == s);
    }

    public boolean hasSession(WebSocketSession s) {
        return sessions.contains(s);
    }

    public void handleInput(String userId, boolean left, boolean right, boolean fire) {
        Player p = players.get(userId);
        if (p == null) return;
        p.inputLeft = left;
        p.inputRight = right;
        if (fire) p.requestFire = true;
    }

    public void update(double dt) {
        // Player movement
        for (Player p : players.values()) {
            double vx = 0;
            if (p.inputLeft) vx -= p.speed;
            if (p.inputRight) vx += p.speed;
            p.x = Math.max(16, Math.min(width - p.w - 16, p.x + vx * dt));

            if (p.requestFire && p.canShoot && p.shot == null) {
                p.fire();
            }
            p.updateShot(dt);
        }

        updateInvaders(dt);
        handleCollisions();
        broadcastState();
    }

    private void updateInvaders(double dt) {
        List<Map<String,Object>> alive = new ArrayList<>();
        for (var i : invaders) if ((boolean) i.get("alive")) alive.add(i);
        if (alive.isEmpty()) { initInvaders(); level++; return; }

        double minX = alive.stream().mapToDouble(i -> (double) i.get("x")).min().orElse(0);
        double maxX = alive.stream().mapToDouble(i -> (double) i.get("x") + (double) i.get("w")).max().orElse(width);
        boolean stepDown = (invaderDir > 0 && maxX + invaderSpeed * dt >= width - 16)
                || (invaderDir < 0 && minX - invaderSpeed * dt <= 16);

        if (stepDown) {
            for (var i : alive) i.put("y", (double) i.get("y") + 14);
            invaderDir *= -1;
        } else {
            for (var i : alive)
                i.put("x", (double) i.get("x") + invaderDir * invaderSpeed * dt);
        }
    }

    private void handleCollisions() {
        for (Player p : players.values()) {
            if (p.shot == null) continue;
            double sx = (double) p.shot.get("x");
            double sy = (double) p.shot.get("y");
            double sw = (double) p.shot.get("w");
            double sh = (double) p.shot.get("h");

            for (var inv : invaders) {
                if (!(boolean) inv.get("alive")) continue;
                double ix = (double) inv.get("x"), iy = (double) inv.get("y"),
                        iw = (double) inv.get("w"), ih = (double) inv.get("h");
                if (sx < ix + iw && sx + sw > ix && sy < iy + ih && sy + sh > iy) {
                    inv.put("alive", false);
                    p.shot = null;
                    p.score += 10;
                    break;
                }
            }
        }
    }

    private void initInvaders() {
        invaders.clear();
        double startX = 60, startY = 80, spacingX = 36, spacingY = 28;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                invaders.add(new HashMap<>(Map.of(
                        "x", startX + c * spacingX,
                        "y", startY + r * spacingY,
                        "w", 24.0,
                        "h", 16.0,
                        "alive", true,
                        "type", 1
                )));
            }
        }
    }

    private void broadcastState() {
        try {
            Map<String, Object> state = new HashMap<>();
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
                pd.put("shot", p.shot);
                playersList.add(pd);
            }
            state.put("players", playersList);
            state.put("invaders", invaders);
            state.put("level", level);
            String msg = mapper.writeValueAsString(Map.of("type", "state", "payload", state));
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) s.sendMessage(new TextMessage(msg));
            }
        } catch (IOException ignored) {}
    }

    private static class Player {
        final String userId;
        final String username;
        final WebSocketSession session;
        double x, y = 560, w = 32, h = 16, speed = 180;
        boolean inputLeft, inputRight, requestFire, canShoot = true;
        Map<String, Object> shot;
        long score = 0;

        Player(String userId, String username, WebSocketSession session, double x) {
            this.userId = userId;
            this.username = username;
            this.session = session;
            this.x = x;
        }

        void fire() {
            shot = new HashMap<>(Map.of("x", x + w / 2 - 1, "y", y - 8, "w", 2, "h", 8, "vy", -360.0));
            canShoot = false;
            requestFire = false;
            new Timer().schedule(new TimerTask() {
                @Override public void run() { canShoot = true; }
            }, 500);
        }

        void updateShot(double dt) {
            if (shot == null) return;
            double ny = (double) shot.get("y") + (double) shot.get("vy") * dt;
            shot.put("y", ny);
            if (ny < -10) shot = null;
        }
    }
}
