package dev.palazon.lvhyard.dashboard.services;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import dev.palazon.lvhyard.dashboard.docker.Docker;
import dev.palazon.lvhyard.dashboard.docker.Docker.ContainerEvents;

/**
 * Keeps the Bound Instances in step with Docker while the dashboard runs. It follows Docker's container events, and
 * brings every Container Binding in step on startup, so containers that started or stopped while the dashboard was
 * not running are caught up with. A Docker that does not answer, or stops answering, is tried again every few seconds,
 * and caught up with the same way once it answers.
 */
@Component
class ContainerWatcher implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(ContainerWatcher.class);

    /** How long to wait before trying a Docker that does not answer again. */
    private static final Duration RETRY = Duration.ofSeconds(5);

    /**
     * How long before a sync its events are asked from. Docker counts in whole seconds, and its clock may be a little
     * behind; an event seen twice only syncs again.
     */
    private static final Duration CLOCK_MARGIN = Duration.ofSeconds(2);

    private final Docker docker;

    private final ContainerBindings bindings;

    private volatile Thread watcher;

    /** The events being followed now, closed to stop the wait for the next one. */
    private volatile ContainerEvents events;

    ContainerWatcher(Docker docker, ContainerBindings bindings) {
        this.docker = docker;
        this.bindings = bindings;
    }

    @Override
    public void start() {
        // Started only once it is the watcher, which it checks to know it may go on.
        Thread starting = Thread.ofVirtual().name("container-watcher").unstarted(this::watch);
        watcher = starting;
        starting.start();
    }

    @Override
    public void stop() {
        Thread stopping = watcher;
        watcher = null;
        ContainerEvents following = events;
        if (following != null) {
            following.close();
        }
        if (stopping != null) {
            stopping.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return watcher != null;
    }

    /**
     * Follows Docker until stopped. The events are asked for from just before each sync, so a container that changes
     * during the sync is not missed: Docker sends its event once asked.
     */
    private void watch() {
        boolean failing = false;
        while (watcher == Thread.currentThread()) {
            Instant synced = Instant.now().minus(CLOCK_MARGIN);
            try {
                bindings.sync();
            } catch (RuntimeException e) {
                failing = failed(failing, e);
                if (!pause()) {
                    return;
                }
                continue;
            }
            if (failing) {
                LOG.info("Docker answers again: the Bound Instances are in step with its containers");
                failing = false;
            }
            try (ContainerEvents following = docker.containerEvents(synced)) {
                events = following;
                // Asking waits for an event, so the watcher may have been stopped meanwhile, with nothing to close.
                while (watcher == Thread.currentThread() && following.hasNext()) {
                    following.next();
                    if (watcher == Thread.currentThread()) {
                        bindings.sync();
                    }
                }
            } catch (RuntimeException e) {
                if (watcher != Thread.currentThread()) {
                    return;
                }
                failing = failed(failing, e);
            } finally {
                events = null;
            }
            if (!pause()) {
                return;
            }
        }
    }

    /** Logs the first of a run of failures to follow Docker, and answers that it is failing. */
    private static boolean failed(boolean failing, RuntimeException e) {
        if (!failing) {
            LOG.warn("Docker cannot be followed, so the Bound Instances may be out of step until it can; trying again"
                    + " every {} s: {}", RETRY.toSeconds(), e.getMessage());
        }
        return true;
    }

    /** Waits before Docker is tried again; {@code false} when the watcher is stopped meanwhile. */
    private boolean pause() {
        try {
            Thread.sleep(RETRY);
            return watcher == Thread.currentThread();
        } catch (InterruptedException e) {
            return false;
        }
    }
}
