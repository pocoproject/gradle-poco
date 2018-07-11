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

package org.gradle.integtests.samples.dependencymanagement

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.junit.Rule

class SamplesTroubleshootingDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {

    private static final String COPY_LIBS_TASK_NAME = 'copyLibs'

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    def setup() {
        executer.usingInitScript(RepoScriptBlockUtil.createMirrorInitScript())
    }

    @UsesSample("userguide/dependencyManagement/troubleshooting/cache/changing")
    def "can declare custom TTL for dependency with dynamic version"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        sample.dir.file('build/libs/spring-web-5.0.3.BUILD-SNAPSHOT.jar').isFile()
    }

    @UsesSample("userguide/dependencyManagement/troubleshooting/cache/changing")
    def "can declare custom TTL for dependency with changing version"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        sample.dir.file('build/libs').listFiles().any { it.name.startsWith('spring-web-5.') }
    }
}
