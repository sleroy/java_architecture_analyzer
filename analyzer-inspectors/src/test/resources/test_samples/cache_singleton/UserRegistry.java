package com.example.singleton;

import java.util.HashMap;
import java.util.Map;

/**
 * A singleton registry that uses double-checked locking pattern.
 * This should be detected by CacheSingletonInspector.
 */
public class UserRegistry {

    // Singleton instance with volatile to ensure visibility
    private static volatile UserRegistry instance;

    // Cache of users
    private final Map<String, User> userCache;

    // Private constructor to prevent instantiation
    private UserRegistry() {
        userCache = new HashMap<>();
        // Pre-populate with some data
        userCache.put("admin", new User("admin", "Administrator", "admin@example.com"));
    }

    // Classic double-checked locking pattern for thread-safe singleton
    public static UserRegistry getInstance() {
        if (instance == null) {
            synchronized (UserRegistry.class) {
                if (instance == null) {
                    instance = new UserRegistry();
                }
            }
        }
        return instance;
    }

    // Cache operations
    public User getUser(String username) {
        return userCache.get(username);
    }

    public void addUser(User user) {
        userCache.put(user.username, user);
    }

    public void removeUser(String username) {
        userCache.remove(username);
    }

    // User class
    public static class User {
        private final String username;
        private final String displayName;
        private final String email;

        public User(String username, String displayName, String email) {
            this.username = username;
            this.displayName = displayName;
            this.email = email;
        }

        public String getUsername() {
            return username;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getEmail() {
            return email;
        }
    }
}
