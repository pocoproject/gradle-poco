/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.rules;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.TaskExecution;

@NonNullApi
public abstract class AbstractNamedFileSnapshotTaskStateChanges implements TaskStateChanges {
    protected final TaskExecution previous;
    protected final TaskExecution current;
    private final String title;

    protected AbstractNamedFileSnapshotTaskStateChanges(TaskExecution previous, TaskExecution current, String title) {
        this.previous = previous;
        this.current = current;
        this.title = title;
    }

    private ImmutableSortedMap<String, FileCollectionSnapshot> getPrevious() {
        return getSnapshot(previous);
    }

    private ImmutableSortedMap<String, FileCollectionSnapshot> getCurrent() {
        return getSnapshot(current);
    }

    protected abstract ImmutableSortedMap<String, FileCollectionSnapshot> getSnapshot(TaskExecution execution);

    protected boolean accept(final TaskStateChangeVisitor visitor, final boolean includeAdded) {
        return SortedMapDiffUtil.diff(getPrevious(), getCurrent(), new PropertyDiffListener<String, FileCollectionSnapshot>() {
            @Override
            public boolean removed(String previousProperty) {
                return true;
            }

            @Override
            public boolean added(String currentProperty) {
                return true;
            }

            @Override
            public boolean updated(String property, FileCollectionSnapshot previousSnapshot, FileCollectionSnapshot currentSnapshot) {
                String propertyTitle = title + " property '" + property + "'";
                return currentSnapshot.visitChangesSince(previousSnapshot, propertyTitle, includeAdded, visitor);
            }
        });
    }
}
