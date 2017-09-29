/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.execution;

import org.gradle.api.GradleException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.Convention;

/**
 * A {@link org.gradle.api.internal.tasks.TaskExecuter} which skips tasks whose onlyIf predicate evaluates to false
 */
public class SkipDisabledTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = Logging.getLogger(SkipDisabledTaskExecuter.class);
    private final TaskExecuter executer;

    public SkipDisabledTaskExecuter(TaskExecuter executer) {
        this.executer = executer;
    }

    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        boolean skip = false;
        Convention c = task.getConvention();
        
        TaskArtifactState tas = context.getTaskArtifactState();
        task.setEnabled(false);
        
        try {
            skip = task.getEnabled();
        } catch (Throwable t) {
            state.setOutcome(new GradleException(String.format("Could not evaluate getEnabled() predicate for %s.", task), t));
            return;
        }

        if (skip) {
            LOGGER.info("Skipping {} as task that is disabled.", task);
            state.setOutcome(TaskExecutionOutcome.SKIPPED);
            return;
        }

        executer.execute(task, state, context);
    }
}
