/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.operations.notify;

import org.gradle.BuildAdapter;
import org.gradle.BuildListener;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BuildOperationNotificationBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildOperationNotificationBridge.class);

    private final BuildOperationListenerManager buildOperationListenerManager;
    private final ListenerManager listenerManager;

    private class State {
        private ReplayAndAttachListener replayAndAttachListener = new ReplayAndAttachListener();
        private BuildOperationListener buildOperationListener = new Adapter(replayAndAttachListener);
        private BuildOperationNotificationListener2 notificationListener;

        private void assignSingleListener(BuildOperationNotificationListener2 notificationListener) {
            if (this.notificationListener != null) {
                throw new IllegalStateException("listener is already registered (implementation class " + this.notificationListener.getClass().getName() + ")");
            }
            this.notificationListener = notificationListener;
        }

        private void stop() {
            buildOperationListenerManager.removeListener(state.buildOperationListener);
            listenerManager.removeListener(buildListener);
        }
    }

    private State state;

    private final BuildOperationNotificationValve valve = new BuildOperationNotificationValve() {
        @Override
        public void start() {
            if (state != null) {
                throw new IllegalStateException("build operation notification valve already started");
            }

            state = new State();
            buildOperationListenerManager.addListener(state.buildOperationListener);
        }


        @Override
        public void stop() {
            if (state != null) {
                state.stop();
                state = null;
            }
        }
    };

    // Listen for the end of configuration of the root project of the root build,
    // and discard buffered notifications if no listeners have yet appeared.
    // This avoids buffering until the end of the build when no listener comes.
    private final BuildListener buildListener = new BuildAdapter() {
        public void buildStarted(@SuppressWarnings("NullableProblems") Gradle gradle) {
            if (gradle.getParent() == null) {
                gradle.rootProject(new Action<Project>() {
                    @Override
                    public void execute(@SuppressWarnings("NullableProblems") Project project) {
                        project.afterEvaluate(new Action<Project>() {
                            @Override
                            public void execute(@SuppressWarnings("NullableProblems") Project project) {
                                State s = state;
                                if (s != null && s.notificationListener == null) {
                                    valve.stop();
                                }
                            }
                        });
                    }
                });
            }
        }
    };

    private final BuildOperationNotificationListenerRegistrar registrar = new BuildOperationNotificationListenerRegistrar() {
        @Override
        public void registerBuildScopeListener(BuildOperationNotificationListener notificationListener) {
            State state = requireState();
            BuildOperationNotificationListener2 adapted = adapt(notificationListener);
            state.assignSingleListener(adapted);

            // Remove the old adapter and start again.
            // We explicitly do not want to receive finish notifications
            // for any operations currently in flight,
            // and we want to throw away the recorded notifications.
            buildOperationListenerManager.removeListener(state.buildOperationListener);
            state.buildOperationListener = new Adapter(adapted);
            buildOperationListenerManager.addListener(state.buildOperationListener);
            state.replayAndAttachListener = null;
        }

        @Override
        public void registerBuildScopeListenerAndReceiveStoredOperations(BuildOperationNotificationListener notificationListener) {
            register(adapt(notificationListener));
        }

        @Override
        public void register(BuildOperationNotificationListener2 listener) {
            State state = requireState();
            state.assignSingleListener(listener);
            state.replayAndAttachListener.attach(listener);
        }

        private State requireState() {
            State s = state;
            if (s == null) {
                throw new IllegalStateException("state is null");
            }

            return s;
        }
    };

    public BuildOperationNotificationBridge(BuildOperationListenerManager buildOperationListenerManager, ListenerManager listenerManager) {
        this.buildOperationListenerManager = buildOperationListenerManager;
        this.listenerManager = listenerManager;
        listenerManager.addListener(buildListener);
    }

    public BuildOperationNotificationListenerRegistrar getRegistrar() {
        return registrar;
    }

    public BuildOperationNotificationValve getValve() {
        return valve;
    }

    /*
        Note: the intention here is to work towards not having to create new objects
        to meet the notification object interfaces.
        Instead, the base types like BuildOperationDescriptor should implement them natively.
        However, this will require restructuring this type and associated things such as
        OperationStartEvent. This will happen later.
     */
    private static class Adapter implements BuildOperationListener {

        private final BuildOperationNotificationListener2 notificationListener;

        private final Map<Object, Object> parents = new ConcurrentHashMap<Object, Object>();
        private final Map<Object, Object> active = new ConcurrentHashMap<Object, Object>();

        private Adapter(BuildOperationNotificationListener2 notificationListener) {
            this.notificationListener = notificationListener;
        }

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            Object id = buildOperation.getId();
            Object parentId = buildOperation.getParentId();

            if (parentId != null) {
                if (active.containsKey(parentId)) {
                    parents.put(id, parentId);
                } else {
                    parentId = parents.get(parentId);
                    if (parentId != null) {
                        parents.put(id, parentId);
                    }
                }
            }

            if (buildOperation.getDetails() == null) {
                return;
            }

            active.put(id, "");

            Started notification = new Started(startEvent.getStartTime(), id, parentId, buildOperation.getDetails());

            try {
                notificationListener.started(notification);
            } catch (Throwable e) {
                LOGGER.debug("Build operation notification listener threw an error on " + notification, e);
                maybeThrow(e);
            }
        }

        private void maybeThrow(Throwable e) {
            if (e instanceof Error && !(e instanceof LinkageError)) {
                throw (Error) e;
            }
        }

        @Override
        public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
            Object details = progressEvent.getDetails();
            if (details == null) {
                return;
            }

            // Find the nearest parent up that we care about and use that as the parent.
            Object owner = findOwner(buildOperationId);
            if (owner == null) {
                return;
            }

            notificationListener.progress(new Progress(owner, progressEvent.getTime(), details));
        }

        private Object findOwner(Object id) {
            if (active.containsKey(id)) {
                return id;
            } else {
                return parents.get(id);
            }
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            Object id = buildOperation.getId();
            Object parentId = parents.remove(id);
            if (active.remove(id) == null) {
                return;
            }

            Finished notification = new Finished(finishEvent.getEndTime(), id, parentId, buildOperation.getDetails(), finishEvent.getResult(), finishEvent.getFailure());
            try {
                notificationListener.finished(notification);
            } catch (Throwable e) {
                LOGGER.debug("Build operation notification listener threw an error on " + notification, e);
                maybeThrow(e);
            }
        }

    }

    private static class RecordingListener implements BuildOperationNotificationListener2 {

        private final Queue<Object> storedEvents = new ConcurrentLinkedQueue<Object>();

        @Override
        public void started(BuildOperationStartedNotification notification) {
            storedEvents.add(notification);
        }

        @Override
        public void progress(BuildOperationProgressNotification notification) {
            storedEvents.add(notification);
        }

        @Override
        public void finished(BuildOperationFinishedNotification notification) {
            storedEvents.add(notification);
        }

    }

    private static class ReplayAndAttachListener implements BuildOperationNotificationListener2 {

        private RecordingListener recordingListener = new RecordingListener();

        private volatile BuildOperationNotificationListener2 listener = recordingListener;

        private final AtomicBoolean needLock = new AtomicBoolean(true);
        private final Lock lock = new ReentrantLock();

        private synchronized void attach(BuildOperationNotificationListener2 realListener) {
            lock.lock();
            try {
                for (Object storedEvent : recordingListener.storedEvents) {
                    if (storedEvent instanceof BuildOperationStartedNotification) {
                        realListener.started((BuildOperationStartedNotification) storedEvent);
                    } else if (storedEvent instanceof BuildOperationProgressNotification) {
                        realListener.progress((BuildOperationProgressNotification) storedEvent);
                    } else if (storedEvent instanceof BuildOperationFinishedNotification) {
                        realListener.finished((BuildOperationFinishedNotification) storedEvent);
                    }
                }

                this.listener = realListener;
                this.recordingListener = null; // release
            } finally {
                lock.unlock();
                needLock.set(false);
            }
        }

        @Override
        public synchronized void started(BuildOperationStartedNotification notification) {
            if (needLock.get()) {
                lock.lock();
                try {
                    listener.started(notification);
                } finally {
                    lock.unlock();
                }
            } else {
                listener.started(notification);
            }
        }

        @Override
        public synchronized void progress(BuildOperationProgressNotification notification) {
            if (needLock.get()) {
                lock.lock();
                try {
                    listener.progress(notification);
                } finally {
                    lock.unlock();
                }
            } else {
                listener.progress(notification);
            }
        }

        @Override
        public synchronized void finished(BuildOperationFinishedNotification notification) {
            if (needLock.get()) {
                lock.lock();
                try {
                    listener.finished(notification);
                } finally {
                    lock.unlock();
                }
            } else {
                listener.finished(notification);
            }
        }

        public void reset() {

        }
    }

    private static class Started implements BuildOperationStartedNotification {

        private final long timestamp;

        private final Object id;
        private final Object parentId;
        private final Object details;

        private Started(long timestamp, Object id, Object parentId, Object details) {
            this.timestamp = timestamp;
            this.id = id;
            this.parentId = parentId;
            this.details = details;
        }

        @Override
        public long getNotificationOperationStartedTimestamp() {
            return timestamp;
        }

        @Override
        public Object getNotificationOperationId() {
            return id;
        }

        @Override
        public Object getNotificationOperationParentId() {
            return parentId;
        }

        @Override
        public Object getNotificationOperationDetails() {
            return details;
        }

        @Override
        public String toString() {
            return "BuildOperationStartedNotification{"
                + "id=" + id
                + ", parentId=" + parentId
                + ", timestamp=" + timestamp
                + ", details=" + details
                + '}';
        }


    }

    private static class Progress implements BuildOperationProgressNotification {

        private final Object id;

        private final long timestamp;
        private final Object details;

        public Progress(Object id, long timestamp, Object details) {
            this.id = id;
            this.timestamp = timestamp;
            this.details = details;
        }

        @Override
        public Object getNotificationOperationId() {
            return id;
        }

        @Override
        public long getNotificationOperationProgressTimestamp() {
            return timestamp;
        }

        @Override
        public Object getNotificationOperationProgressDetails() {
            return details;
        }

    }

    private static class Finished implements BuildOperationFinishedNotification {

        private final long timestamp;

        private final Object id;
        private final Object parentId;
        private final Object details;
        private final Object result;
        private final Throwable failure;

        private Finished(long timestamp, Object id, Object parentId, Object details, Object result, Throwable failure) {
            this.timestamp = timestamp;
            this.id = id;
            this.parentId = parentId;
            this.details = details;
            this.result = result;
            this.failure = failure;
        }

        @Override
        public long getNotificationOperationFinishedTimestamp() {
            return timestamp;
        }

        @Override
        public Object getNotificationOperationId() {
            return id;
        }

        @Nullable
        @Override
        public Object getNotificationOperationParentId() {
            return parentId;
        }

        @Override
        public Object getNotificationOperationDetails() {
            return details;
        }

        @Override
        public Object getNotificationOperationResult() {
            return result;
        }

        @Override
        public Throwable getNotificationOperationFailure() {
            return failure;
        }

        @Override
        public String toString() {
            return "BuildOperationFinishedNotification{"
                + "id=" + id
                + ", parentId=" + parentId
                + ", timestamp=" + timestamp
                + ", details=" + details
                + ", result=" + result
                + ", failure=" + failure
                + '}';
        }
    }

    private static BuildOperationNotificationListener2 adapt(final BuildOperationNotificationListener listener) {
        return new BuildOperationNotificationListener2() {
            @Override
            public void started(BuildOperationStartedNotification notification) {
                listener.started(notification);
            }

            @Override
            public void progress(BuildOperationProgressNotification notification) {

            }

            @Override
            public void finished(BuildOperationFinishedNotification notification) {
                listener.finished(notification);
            }
        };
    }
}
