/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks

import org.gradle.api.Transformer
import org.gradle.api.internal.BuildDefinition
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.StandAloneNestedBuild
import org.gradle.internal.invocation.BuildController
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class GradleBuildTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    def owner = Mock(BuildState)
    def buildStateRegistry = Mock(BuildStateRegistry)
    def build = Mock(StandAloneNestedBuild)
    GradleBuild task = TestUtil.create(temporaryFolder).task(GradleBuild, [currentBuild: owner, buildStateRegistry: buildStateRegistry])

    void usesCopyOfCurrentBuildsStartParams() {
        def expectedStartParameter = task.project.gradle.startParameter.newBuild()
        expectedStartParameter.currentDir = task.project.projectDir

        expect:
        task.startParameter == expectedStartParameter

        when:
        task.tasks = ['a', 'b']

        then:
        task.tasks == ['a', 'b']
        task.startParameter.taskNames == ['a', 'b']
    }

    void executesBuild() {
        def buildController = Mock(BuildController)

        when:
        task.build()

        then:
        1 * buildStateRegistry.addNestedBuildTree(_, owner) >> { BuildDefinition buildDefinition, BuildState b ->
            assert buildDefinition.startParameter == task.startParameter
            build
        }
        1 * build.run(_) >> { Transformer transformer -> transformer.transform(buildController) }
        1 * buildController.run()
    }
}
