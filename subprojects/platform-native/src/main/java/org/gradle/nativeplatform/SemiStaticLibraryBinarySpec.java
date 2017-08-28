/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativeplatform;

import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.platform.base.BinaryTasksCollection;

import java.io.File;

/**
 * A static library binary built by Gradle for a native library.
 */
@Incubating
public interface SemiStaticLibraryBinarySpec extends NativeLibraryBinarySpec {
    /**
     * Provides access to key tasks used for building the binary.
     */
    interface TasksCollection extends BinaryTasksCollection {
        /**
         * The create static library task.
         */
        Task getCreateSemiStaticLib();
    }

    /**
     * The static library file.
     */
    File getSemiStaticLibraryFile();

    /**
     * The static library binary file.
     */
    void setSemiStaticLibraryFile(File staticLibraryFile);

    /**
     * Add some additional files required by consumers of this library at link time.
     */
    void additionalLinkFiles(FileCollection files);

    /**
     * {@inheritDoc}
     */
    @Override
    TasksCollection getTasks();
}
