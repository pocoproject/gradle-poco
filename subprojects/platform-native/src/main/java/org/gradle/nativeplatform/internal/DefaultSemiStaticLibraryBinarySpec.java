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

package org.gradle.nativeplatform.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.gradle.api.file.FileCollection;
import org.gradle.nativeplatform.SemiStaticLibraryBinary;
import org.gradle.nativeplatform.SemiStaticLibraryBinarySpec;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.tasks.CreateSemiStaticLibrary;
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.internal.BinaryBuildAbility;
import org.gradle.platform.base.internal.BinaryTasksCollectionWrapper;
import org.gradle.platform.base.internal.FixedBuildAbility;

public class DefaultSemiStaticLibraryBinarySpec extends AbstractNativeLibraryBinarySpec implements SemiStaticLibraryBinary, SemiStaticLibraryBinarySpecInternal {
    private final List<FileCollection> additionalLinkFiles = new ArrayList<FileCollection>();
    private final DefaultTasksCollection tasks = new DefaultTasksCollection(super.getTasks());
    private File staticLibraryFile;

    @Override
    protected BinaryBuildAbility getBinaryBuildAbility() {
        // Default behavior is to always be buildable.  Binary implementations should define what
        // criteria make them buildable or not.
    	NativePlatform nativePlatform = this.getTargetPlatform();
    	return new FixedBuildAbility(nativePlatform.getOperatingSystem().isWindows());
    }

    @Override
    public File getSemiStaticLibraryFile() {
        return staticLibraryFile;
    }

    @Override
    public void setSemiStaticLibraryFile(File staticLibraryFile) {
        this.staticLibraryFile = staticLibraryFile;
    }

    @Override
    public File getPrimaryOutput() {
        return getSemiStaticLibraryFile();
    }

    @Override
    public void additionalLinkFiles(FileCollection files) {
        this.additionalLinkFiles.add(files);
    }

    @Override
    public FileCollection getLinkFiles() {
        return getFileCollectionFactory().create(new StaticLibraryLinkOutputs());
    }

    @Override
    public FileCollection getRuntimeFiles() {
        return getFileCollectionFactory().empty("Runtime files for " + getDisplayName());
    }

    @Override
    protected ObjectFilesToBinary getCreateOrLink() {
        return tasks.getCreateSemiStaticLib();
    }

    @Override
    public SemiStaticLibraryBinarySpec.TasksCollection getTasks() {
        return tasks;
    }

    static class DefaultTasksCollection extends BinaryTasksCollectionWrapper implements SemiStaticLibraryBinarySpec.TasksCollection {
        public DefaultTasksCollection(BinaryTasksCollection delegate) {
            super(delegate);
        }

		@Override
		public CreateSemiStaticLibrary getCreateSemiStaticLib() {
          return findSingleTaskWithType(CreateSemiStaticLibrary.class);
		}

//        @Override
//        public CreateSemiStaticLibrary getCreateStaticLib() {
//            return findSingleTaskWithType(CreateSemiStaticLibrary.class);
//        }
    }

    private class StaticLibraryLinkOutputs extends LibraryOutputs {
        @Override
        public String getDisplayName() {
            return "Link files for " + DefaultSemiStaticLibraryBinarySpec.this.getDisplayName();
        }

        @Override
        protected boolean hasOutputs() {
            return hasSources() || !additionalLinkFiles.isEmpty();
        }

        @Override
        protected Set<File> getOutputs() {
            Set<File> allFiles = new LinkedHashSet<File>();
            if (hasSources()) {
                allFiles.add(getSemiStaticLibraryFile());
            }
            for (FileCollection resourceSet : additionalLinkFiles) {
                allFiles.addAll(resourceSet.getFiles());
            }
            return allFiles;
        }
    }
}
