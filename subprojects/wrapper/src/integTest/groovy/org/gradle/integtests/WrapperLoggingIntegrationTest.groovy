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

package org.gradle.integtests

import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class WrapperLoggingIntegrationTest extends AbstractWrapperIntegrationSpec {

    def setup() {
        executer.withWelcomeMessageEnabled()
        file("build.gradle") << "task emptyTask"
    }

    def "wrapper only renders welcome message when executed in quiet mode"() {
        given:
        prepareWrapper()

        when:
        args '-q'
        result = wrapperExecuter.withTasks("emptyTask").run()

        then:
        outputContains("Welcome to Gradle $wrapperExecuter.distribution.version.version!")

        when:
        args '-q'
        result = wrapperExecuter.withTasks("emptyTask").run()

        then:
        result.output.contains("Welcome to Gradle $wrapperExecuter.distribution.version.version!")

        when:
        args '-q'
        result = wrapperExecuter.withTasks("emptyTask").run()

        then:
        result.output.empty
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "wrapper logs and continues when there is a problem setting permissions"() {
        given: "malformed distribution"
        // Repackage distribution with bin/gradle removed so permissions cannot be set
        TestFile tempUnzipDir = temporaryFolder.createDir("temp-unzip")
        distribution.binDistribution.unzipTo(tempUnzipDir)
        assert tempUnzipDir.file("gradle-${distribution.version.version}", "bin", "gradle").delete()
        TestFile tempZipDir = temporaryFolder.createDir("temp-zip-foo")
        TestFile malformedDistZip = new TestFile(tempZipDir, "gradle-${distribution.version.version}-bin.zip")
        tempUnzipDir.zipTo(malformedDistZip)
        prepareWrapper(malformedDistZip.toURI())

        when:
        result = wrapperExecuter
            .withTasks("emptyTask")
            .run()

        then:
        outputContains("Could not set executable permissions")
        outputContains("Please do this manually if you want to use the Gradle UI.")
    }

    def "wrapper prints error and fails build if downloaded zip is empty"() {
        given: "empty distribution"
        TestFile tempUnzipDir = temporaryFolder.createDir("empty-distribution")
        TestFile malformedDistZip = new TestFile(tempUnzipDir, "gradle-${distribution.version.version}-bin.zip") << ""
        prepareWrapper(malformedDistZip.toURI())

        when:
        failure = wrapperExecuter
            .withTasks("emptyTask")
            .withStackTraceChecksDisabled()
            .runWithFailure()

        then:
        failure.assertOutputContains("Could not unzip")
        failure.assertNotOutput("Could not set executable permissions")
    }
}
