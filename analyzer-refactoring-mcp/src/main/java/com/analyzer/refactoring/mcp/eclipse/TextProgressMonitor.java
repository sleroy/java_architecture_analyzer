package com.analyzer.refactoring.mcp.eclipse;

import org.eclipse.core.runtime.IProgressMonitor;

/**
 * A simple text-based Eclipse progress monitor for CLI environments.
 * Prints task progress and completion info to standard output.
 */
public class TextProgressMonitor implements IProgressMonitor {

    private boolean canceled = false;
    private String taskName = "";
    private int totalWork = 0;
    private int worked = 0;

    @Override
    public void beginTask(String name, int totalWork) {
        this.taskName = name;
        this.totalWork = totalWork > 0 ? totalWork : IProgressMonitor.UNKNOWN;
        this.worked = 0;
        System.out.println("[START] " + taskName + 
            (totalWork == UNKNOWN ? " (unknown total work)" : " (total: " + totalWork + ")"));
    }

    @Override
    public void done() {
        System.out.println("[DONE] " + taskName);
    }

    @Override
    public void internalWorked(double work) {
        // Optional finer-grained progress tracking
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public void setCanceled(boolean value) {
        this.canceled = value;
        if (value) {
            System.out.println("[CANCELED] " + taskName);
        }
    }

    @Override
    public void setTaskName(String name) {
        this.taskName = name;
        System.out.println("[TASK] " + name);
    }

    @Override
    public void subTask(String name) {
        System.out.println("   [SUBTASK] " + name);
    }

    @Override
    public void worked(int work) {
        this.worked += work;
        if (totalWork == UNKNOWN) {
            System.out.println("   Progress: +" + work);
        } else {
            int percent = (int) ((worked / (double) totalWork) * 100);
            System.out.println("   Progress: " + worked + "/" + totalWork + " (" + percent + "%)");
        }
    }
}
