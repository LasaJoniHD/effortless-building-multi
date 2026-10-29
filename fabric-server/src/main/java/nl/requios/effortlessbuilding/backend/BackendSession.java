package nl.requios.effortlessbuilding.backend;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Connection-scoped opt-in state. Platforms call this only on the server thread. */
public final class BackendSession {
    // Packet encoding can inspect capabilities on Netty threads.
    private static final Map<UUID, BackendSession> SESSIONS = new ConcurrentHashMap<>();
    private long windowStart;
    private int requests;

    private BackendSession() {}

    public static boolean connect(UUID player) {
        return SESSIONS.putIfAbsent(player, new BackendSession()) == null;
    }

    public static boolean supports(UUID player) {
        return SESSIONS.containsKey(player);
    }

    /** Bound expensive requests, independently of packet size and shape limits. */
    public static boolean acceptRequest(UUID player, long tick) {
        BackendSession session = SESSIONS.get(player);
        if (session == null) return false;
        if (tick - session.windowStart >= 20) {
            session.windowStart = tick;
            session.requests = 0;
        }
        return ++session.requests <= 40;
    }

    public static void disconnect(UUID player) { SESSIONS.remove(player); }
    public static void clear() { SESSIONS.clear(); }
}
