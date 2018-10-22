/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.performance.regression.inception

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.categories.PerformanceExperiment
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.junit.experimental.categories.Category
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL
import static org.gradle.performance.generator.JavaTestProject.MEDIUM_MONOLITHIC_JAVA_PROJECT

/**
 * Test Gradle performance against it's own build.
 *
 * Reasons for re-baselining:
 * - accept a regression in Gradle itself
 * - accept a regression in the Gradle build
 * - improvements to Gradle or its build!
 *
 * Reasons for breaking:
 *   - e.g. change in Gradle that breaks the Gradle build
 */
@Issue('https://github.com/gradle/gradle-private/issues/1313')
class GradleInceptionPerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll
    def "#tasks on the gradle build comparing gradle"() {
        given:
        runner.testProject = "gradleBuildCurrent"
        runner.tasksToRun = tasks.split(' ')
        runner.targetVersions = ["5.0-20181016235834+0000"]
        runner.args = ["-Djava9Home=${System.getProperty('java9Home')}"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        tasks  | _
        'help' | _
    }

    @Category(PerformanceExperiment)
    @Unroll
    def "buildSrc api change in #testProject comparing gradle"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ['help']
        runner.targetVersions = ["5.0-20181016235834+0000"]
        runner.runs = runs
        runner.args = [
            "-Djava9Home=${System.getProperty('java9Home')}",
            "-Pgradlebuild.skipBuildSrcChecks=true"
        ]

        and:
        def changingClassFilePath = "buildSrc/${buildSrcProjectDir}src/main/groovy/ChangingClass.groovy"
        runner.addBuildExperimentListener(new BuildExperimentListenerAdapter() {
            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                new File(invocationInfo.projectDir, changingClassFilePath).tap {
                    parentFile.mkdirs()
                    text = """
                        class ChangingClass {
                            void changingMethod${invocationInfo.phase}${invocationInfo.iterationNumber}() {}
                        }
                    """.stripIndent()
                }
            }
        })

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                         | buildSrcProjectDir   | runs
        MEDIUM_MONOLITHIC_JAVA_PROJECT      | ""                   | 40
        LARGE_JAVA_MULTI_PROJECT            | ""                   | 20
        LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL | ""                   | 10
        'gradleBuildCurrent'                | "subprojects/build/" | 10
    }
}
