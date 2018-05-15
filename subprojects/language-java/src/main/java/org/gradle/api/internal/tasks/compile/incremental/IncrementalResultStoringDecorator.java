/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarClasspathSnapshotWriter;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorPathStore;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;

/**
 * Stores the incremental class dependency analysis after compilation has finished.
 */
class IncrementalResultStoringDecorator implements Compiler<JavaCompileSpec> {

    private final Compiler<JavaCompileSpec> delegate;
    private final JarClasspathSnapshotWriter writer;
    private final ClassSetAnalysisUpdater updater;
    private final AnnotationProcessorPathStore annotationProcessorPathStore;

    public IncrementalResultStoringDecorator(Compiler<JavaCompileSpec> delegate, JarClasspathSnapshotWriter writer, ClassSetAnalysisUpdater updater, AnnotationProcessorPathStore annotationProcessorPathStore) {
        this.delegate = delegate;
        this.writer = writer;
        this.updater = updater;
        this.annotationProcessorPathStore = annotationProcessorPathStore;
    }

    @Override
    public WorkResult execute(JavaCompileSpec spec) {
        WorkResult out = delegate.execute(spec);
        updater.updateAnalysis(spec, out);
        writer.storeJarSnapshots(spec.getCompileClasspath());
        annotationProcessorPathStore.put(spec.getAnnotationProcessorPath());
        return out;
    }
}
