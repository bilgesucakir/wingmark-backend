package com.wingmark.backend.service;

/**
 * Runs work after the request has been answered. Used so the response time of an endpoint does not depend on
 * whether an account exists (login protection): the lookup is the same either way and the rest happens here.
 */
public interface BackgroundTasks {

    /** Runs the task soon on another thread; a failure is logged and never reaches the caller. */
    void run(Runnable task);
}
