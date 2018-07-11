/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.composite.internal

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.internal.artifacts.ForeignBuildIdentifier
import org.gradle.internal.build.BuildState
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.util.Path
import spock.lang.Specification

class DefaultIncludedBuildTest extends Specification {
    def "creates a foreign id for projects"() {
        def build = new DefaultIncludedBuild(Stub(BuildIdentifier), Path.path(":a:b:c"), Stub(BuildDefinition), false, Stub(BuildState), Stub(WorkerLeaseRegistry.WorkerLease))
        def projectId = new DefaultProjectComponentIdentifier(Stub(BuildIdentifier), Path.path("id"), Path.path("project"), "name")

        expect:
        def id = build.idToReferenceProjectFromAnotherBuild(projectId)
        id.build instanceof ForeignBuildIdentifier
        id.identityPath == projectId.identityPath
        id.projectPath == projectId.projectPath
        id.projectName == projectId.projectName
    }
}
