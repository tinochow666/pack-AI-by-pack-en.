package com.phiigrame.services;

/**
 * Thin facade over {@link UserDatabase} for account operations.
 *
 * The actual data lives in the SQLite database - this class only exists
 * to keep the call sites in the UI layer stable and to provide a tiny
 * {@link User} DTO that mimics the historical shape (so {@code
 * authService.getCurrentUser().username} still works everywhere).
 */
public class AuthService {

    private final UserDatabase db;

    public AuthService() {
        this.db = new UserDatabase();
    }

    public boolean register(String username, String email, String password) {
        return db.register(username, email, password);
    }

    public boolean login(String username, String password) {
        return db.login(username, password);
    }

    public void logout() {
        db.logout();
    }

    public boolean isLoggedIn() {
        return db.isLoggedIn();
    }

    /**
     * Returns the currently signed-in user, or {@code null} if no one is
     * signed in. The DTO only carries the username because that is the
     * only field the UI currently needs.
     */
    public User getCurrentUser() {
        if (!isLoggedIn()) return null;
        User u = new User();
        u.username = db.currentUsername();
        return u;
    }

    /** Lightweight DTO for the current user, mirroring the legacy API. */
    public static class User {
        public String username;
    }
}
