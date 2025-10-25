package com.example.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A mutable service class with non-final instance fields.
 * This should be detected by MutableServiceInspector as having thread-safety
 * issues.
 */
public class UserService {

    // Non-final instance fields that make this service mutable and potentially not
    // thread-safe
    private Map<String, User> userCache;
    private List<String> recentUserQueries;
    private User lastAccessedUser;
    private int accessCount;

    // Configuration that might be changed at runtime
    private int maxCacheSize = 1000;
    private boolean loggingEnabled = true;

    public UserService() {
        userCache = new HashMap<>();
        recentUserQueries = new ArrayList<>();
    }

    public User findUserById(String id) {
        // Save the query in history
        recentUserQueries.add(id);

        // Update access counter
        accessCount++;

        // Check if user is in cache
        User user = userCache.get(id);

        if (user != null) {
            // Save as last accessed
            lastAccessedUser = user;
            return user;
        }

        // Simulate fetching from database
        user = fetchUserFromDatabase(id);

        // Update cache
        userCache.put(id, user);
        lastAccessedUser = user;

        // Cache maintenance
        if (userCache.size() > maxCacheSize) {
            pruneCache();
        }

        return user;
    }

    public List<User> getRecentUsers() {
        List<User> result = new ArrayList<>();
        for (String id : recentUserQueries) {
            User user = userCache.get(id);
            if (user != null) {
                result.add(user);
            }
        }
        return result;
    }

    public void updateUser(User user) {
        // Update in "database"
        updateUserInDatabase(user);

        // Update in cache
        userCache.put(user.getId(), user);

        // If this was the last accessed user, update that reference too
        if (lastAccessedUser != null && lastAccessedUser.getId().equals(user.getId())) {
            lastAccessedUser = user;
        }
    }

    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
        if (userCache.size() > maxCacheSize) {
            pruneCache();
        }
    }

    public void enableLogging(boolean enabled) {
        this.loggingEnabled = enabled;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void resetAccessCount() {
        accessCount = 0;
    }

    // Cross-method field modification - the recentUserQueries field is modified
    // here and in findUserById
    public void clearHistory() {
        recentUserQueries.clear();
    }

    // Synchronized method - trying to handle thread-safety but only partially
    synchronized void pruneCache() {
        // Only keep the most recently used entries up to maxCacheSize
        if (recentUserQueries.size() <= maxCacheSize) {
            return;
        }

        // Create a new cache with only the recent entries
        Map<String, User> newCache = new HashMap<>();
        int keepCount = Math.min(maxCacheSize, recentUserQueries.size());

        for (int i = recentUserQueries.size() - 1; i >= recentUserQueries.size() - keepCount; i--) {
            String id = recentUserQueries.get(i);
            if (userCache.containsKey(id)) {
                newCache.put(id, userCache.get(id));
            }
        }

        userCache = newCache;
    }

    // Simulate database operations
    private User fetchUserFromDatabase(String id) {
        return new User(id, "User " + id, "user" + id + "@example.com");
    }

    private void updateUserInDatabase(User user) {
        // Simulation only
    }

    // User class
    public static class User {
        private final String id;
        private String name;
        private String email;

        public User(String id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}
