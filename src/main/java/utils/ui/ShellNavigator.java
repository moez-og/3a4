package utils.ui;

import java.util.Deque;

/**
 * ShellNavigator — interface for route-based navigation in the front shell.
 * Also exposes the navigation history stack for gesture-back support.
 */
public interface ShellNavigator {
    void navigate(String route);

    /**
     * Returns the navigation history stack (most recent route on top).
     * Implementors should maintain this stack automatically in navigate().
     * May return null if history is not supported.
     */
    default Deque<String> getNavigationHistory() { return null; }
}
