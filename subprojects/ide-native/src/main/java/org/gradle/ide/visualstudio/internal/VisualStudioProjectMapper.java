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

package org.gradle.ide.visualstudio.internal;

import java.util.List;

import org.apache.commons.lang.StringUtils;
<<<<<<< HEAD
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeExecutableBinarySpec;
import org.gradle.nativeplatform.SemiStaticLibraryBinarySpec;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;

public class VisualStudioProjectMapper {

    public static String getProjectName(VisualStudioTargetBinary targetBinary) {
        return getProjectName(targetBinary.getProjectPath(), targetBinary.getComponentName(), targetBinary.getProjectType());
    }

    public static String getProjectName(String projectPath, String componentName, VisualStudioTargetBinary.ProjectType type) {
        return projectPrefix(projectPath) + componentName + type.getSuffix();
    }

    public static String getConfigurationName(List<String> variantDimensions) {
        return makeName(variantDimensions);
    }

    private static String projectPrefix(String projectPath) {
        if (":".equals(projectPath)) {
            return "";
        }
        return projectPath.substring(1).replace(":", "_") + "_";
    }

    private String componentName(NativeBinarySpec nativeBinary) {
        return nativeBinary.getComponent().getName();
    }

    private String projectSuffix(NativeBinarySpec nativeBinary) {
        return nativeBinary instanceof SharedLibraryBinarySpec ? "Dll"
                : nativeBinary instanceof StaticLibraryBinarySpec ? "Lib"
                : nativeBinary instanceof SemiStaticLibraryBinarySpec ? "SemiLib"
                : nativeBinary instanceof NativeExecutableBinarySpec ? "Exe"
                : nativeBinary instanceof NativeTestSuiteBinarySpec ? "Exe"
                : "";
    }

    private static String makeName(Iterable<String> components) {
        StringBuilder builder = new StringBuilder();
        for (String component : components) {
            if (component != null && component.length() > 0) {
                if (builder.length() == 0) {
                    builder.append(component);
                } else {
                    builder.append(StringUtils.capitalize(component));
                }
            }
        }
        return builder.toString();
    }
}
