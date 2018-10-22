/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

/**
 * Tests for resolving dependency artifacts with substitution within a composite build.
 */
class CompositeBuildDependencyArtifactsIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB
    List arguments = []

    def setup() {
        new ResolveTestFixture(buildA.buildFile).prepare()

        buildA.buildFile << """
            task resolve(type: Copy) {
                from configurations.compile
                into 'libs'
            }
"""

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
"""
        }
        includedBuilds << buildB
    }

    def "builds single artifact for substituted dependency"() {
        given:
        dependency 'org.test:buildB:1.0'

        when:
        resolveArtifacts()

        then:
        executedInOrder ":buildB:jar", ":resolve"
        assertResolved buildB.file('build/libs/buildB-1.0.jar')
    }

    def "builds multiple artifacts for substituted dependency"() {
        given:
        dependency 'org.test:buildB:1.0'

        and:
        buildB.buildFile << """
            task myJar(type: Jar) {
                classifier 'my'
            }

            artifacts {
                compile myJar
            }
"""

        when:
        resolveArtifacts()

        then:
        executed ":buildB:compileJava", ":buildB:jar", ":buildB:myJar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildB.file('build/libs/buildB-1.0-my.jar')
    }

    def "builds artifacts for dependencies on multiple subprojects in the same build"() {
        given:
        dependency 'org.test:b1:1.0'
        dependency 'org.test:b2:1.0'

        when:
        resolveArtifacts()

        then:
        executed ":buildB:b1:compileJava", ":buildB:b1:jar", ":buildB:b2:compileJava", ":buildB:b2:jar"
        assertResolved buildB.file('b1/build/libs/b1-1.0.jar'), buildB.file('b2/build/libs/b2-1.0.jar')
    }

    def "builds substituted dependency with transitive external dependencies"() {
        given:
        dependency 'org.test:buildB:1.0'

        def moduleC = mavenRepo.module("org.test", "buildC", "1.0").publish()
        buildB.buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                compile 'org.test:buildC:1.0'
            }
"""

        when:
        resolveArtifacts()

        then:
        executed ":buildB:jar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), moduleC.artifactFile
    }

    def "builds substituted dependency with transitive external dependency that is substituted"() {
        given:
        dependency 'org.test:buildB:1.0'

        buildB.buildFile << """
            dependencies {
                compile 'org.test:buildC:1.0'
            }
"""
        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
"""
        }
        includedBuilds << buildC

        when:
        resolveArtifacts()

        then:
        executedInOrder ":buildC:jar", ":buildB:jar", ":resolve"
        executedInOrder ":buildC:compileJava", ":buildB:compileJava", ":resolve"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildC.file('build/libs/buildC-1.0.jar')
    }

    def "builds dependency once when included directly and as a transitive dependency"() {
        given:
        dependency 'org.test:buildB:1.0'
        dependency 'org.test:buildC:1.0'

        buildB.buildFile << """
            dependencies {
                compile 'org.test:buildC:1.0'
            }
"""
        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
"""
        }
        includedBuilds << buildC

        when:
        resolveArtifacts()

        then:
        executedInOrder ":buildC:jar", ":buildB:jar", ":resolve"
        executedInOrder ":buildC:compileJava", ":buildB:compileJava", ":resolve"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildC.file('build/libs/buildC-1.0.jar')
    }

    def "builds substituted dependency with transitive external dependency that is substituted in the same build"() {
        given:
        dependency 'org.test:buildB:1.0'

        buildB.buildFile << """
            dependencies {
                compile 'org.test:b1:1.0'
            }
"""

        when:
        resolveArtifacts()

        then:
        executedInOrder ":buildB:b1:compileJava", ":buildB:b1:jar", ":buildB:compileJava", ":buildB:jar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildB.file('b1/build/libs/b1-1.0.jar')
    }

    def "builds substituted dependency with transitive project dependencies"() {
        given:
        dependency "org.test:buildB:1.0"

        buildB.buildFile << """
            dependencies {
                compile project(':b1')
                compile project(path: ':b2', configuration: 'other')
            }

            project('b2') {
                configurations {
                    other
                }
                task myJar(type: Jar) {
                    classifier 'my'
                }
                artifacts {
                    other myJar
                }
            }
"""

        when:
        resolveArtifacts()

        then:
        executedInOrder ":buildB:b1:jar", ":buildB:jar"
        executedInOrder ":buildB:b2:myJar", ":buildB:jar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildB.file('b1/build/libs/b1-1.0.jar'), buildB.file('b2/build/libs/b2-1.0-my.jar')
    }

    def "builds substituted dependency with file dependency"() {
        given:
        dependency 'org.test:buildB:1.0'

        buildB.buildFile << """
            task jar1(type: Jar) {
                classifier '1'
            }
            dependencies {
                compile files(jar1.archivePath) { builtBy jar1 }
            }
"""

        when:
        resolveArtifacts()

        then:
        executed ":buildB:jar1", ":buildB:jar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildB.file("build/libs/buildB-1.0-1.jar")
    }

    def "builds substituted dependency with non-default configuration"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile group: 'org.test', name: 'buildB', version: '1.0', configuration: 'other'
            }
"""

        buildB.buildFile << """
            configurations {
                other
            }
            dependencies {
                other 'org.test:buildC:1.0'
            }
            task myJar(type: Jar) {
                classifier 'my'
            }
            artifacts {
                other myJar
            }
"""
        def moduleC = mavenRepo.module("org.test", "buildC", "1.0").publish()

        when:
        resolveArtifacts()

        then:
        executed ":buildB:myJar"
        assertResolved buildB.file('build/libs/buildB-1.0-my.jar'), moduleC.artifactFile
    }

    def "builds substituted dependency with non-default artifactType"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile 'org.test:buildB:1.0@zip'
            }
"""

        buildB.buildFile << """
            task myZip(type: Zip) {
                extension 'zip'
                from 'src'
            }
            artifacts {
                compile myZip
            }
"""

        when:
        resolveArtifacts()

        then:
        executed ":buildB:myZip"
        assertResolved buildB.file('build/distributions/buildB-1.0.zip')
    }

    def "builds substituted dependency with defined artifacts"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile group: 'org.test', name: 'buildB', version: '1.0', classifier: 'my'
                compile(group: 'org.test', name: 'buildB', version: '1.0') {
                    artifact {
                        name = 'another'
                        type = 'jar'
                    }
                }
            }
"""

        buildB.buildFile << """
            task myJar(type: Jar) {
                classifier 'my'
            }
            task anotherJar(type: Jar) {
                baseName 'another'
            }
            artifacts {
                compile myJar
                compile anotherJar
            }
"""

        when:
        resolveArtifacts()

        then:
        executed ":buildB:myJar"
        assertResolved buildB.file('build/libs/buildB-1.0-my.jar'), buildB.file('build/libs/another-1.0.jar')
    }

    def "builds multiple configurations for the same substituted dependency"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile group: 'org.test', name: 'buildB', version: '1.0'
                compile group: 'org.test', name: 'buildB', version: '1.0', configuration: 'other'
            }
"""

        buildB.buildFile << """
            configurations {
                other
            }
            task myJar(type: Jar) {
                classifier 'my'
            }
            artifacts {
                other myJar
            }
"""

        when:
        resolveArtifacts()

        then:
        executed ":buildB:jar", ":buildB:myJar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildB.file('build/libs/buildB-1.0-my.jar')
    }

    def "does not attempt to build dependency artifacts more than once"() {
        given:
        dependency 'org.test:b1:1.0'
        dependency 'org.test:b2:1.0'

        buildB.buildFile << """
            project(':b1') {
                dependencies {
                    compile project(':b2')
                }
            }
"""
        when:
        resolveArtifacts()

        then:
        executedInOrder ":buildB:b2:compileJava", ":buildB:b2:jar", ":buildB:b1:compileJava", ":buildB:b1:jar"
        result.executedTasks.count {it == ":buildB:b2:jar"} == 1
        assertResolved buildB.file('b1/build/libs/b1-1.0.jar'), buildB.file('b2/build/libs/b2-1.0.jar')
    }

    def "build dependency artifacts only once when depended on by multiple subprojects"() {
        given:
        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
"""
        }

        buildB.buildFile << """
            project(':b1') {
                dependencies {
                    compile 'org.test:buildC:1.0'
                }
            }
            project(':b2') {
                dependencies {
                    compile 'org.test:buildC:1.0'
                }
            }
"""
        buildB.settingsFile << """
            includeBuild '${buildC.toURI()}'
"""

        when:
        execute(buildB, "jar")

        then:
        // Need to assert order separately to cater for parallel execution
        executedInOrder ":buildC:jar", ":b1:classes", ":b1:jar"
        executedInOrder ":buildC:jar", ":b2:classes", ":b2:jar"

        executedInOrder ":buildC:compileJava", ":buildC:jar", ":b1:compileJava", ":b1:jar"
        executedInOrder ":buildC:compileJava", ":buildC:jar", ":b2:compileJava", ":b2:jar"
    }

    def "builds multiple configurations for the same project via separate dependency paths"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile group: 'org.test', name: 'buildB', version: '1.0'
                compile group: 'org.test', name: 'buildC', version: '1.0'
            }
"""

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
                dependencies {
                    compile group: 'org.test', name: 'buildB', version: '1.0', configuration: 'other'
                }
"""
        }
        includedBuilds << buildC

        buildB.buildFile << """
            configurations {
                other
            }
            task myJar(type: Jar) {
                classifier 'my'
            }
            artifacts {
                other myJar
            }
"""

        when:
        resolveArtifacts()

        then:
        executed ":buildB:jar", ":buildB:myJar", ":buildC:jar"
        assertResolved buildB.file('build/libs/buildB-1.0.jar'), buildB.file('build/libs/buildB-1.0-my.jar'), buildC.file("build/libs/buildC-1.0.jar")
    }

    def "builds artifacts for different subprojects from the same build included via separate dependency paths"() {
        given:
        dependency 'org.test:b1:1.0'
        dependency 'org.test:buildC:1.0'

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
                dependencies {
                    compile 'org.test:b2:1.0'
                }
"""
        }
        includedBuilds << buildC


        when:
        resolveArtifacts()

        then:
        executed ":buildB:b1:jar", ":buildB:b2:jar", ":buildC:jar"
        executed ":buildB:b1:compileJava", ":buildB:b2:compileJava", ":buildC:compileJava"
    }

    def "substitutes and builds compileOnly dependency"() {
        given:
        buildA.buildFile << """
            dependencies {
                compileOnly 'org.test:buildB:1.0'
            }
"""
        when:
        execute(buildA, ":jar")

        then:
        executedInOrder ":buildB:compileJava", ":buildB:classes", ":compileJava", ":jar"
    }

    def "substitutes and builds transitive compileOnly dependency"() {
        given:
        dependency 'org.test:buildB:1.0'

        buildB.buildFile << """
            dependencies {
                compileOnly 'org.test:buildC:1.0'
            }
"""

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
"""
        }
        includedBuilds << buildC

        when:
        resolveArtifacts()

        then:
        executedInOrder ":buildC:jar", ":buildB:jar"
    }

    def "only builds dependency once when included as transitive compile and compileOnly dependency"() {
        given:
        dependency 'org.test:buildB:1.0'
        dependency 'org.test:buildC:1.0'

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
                dependencies {
                    compileOnly 'org.test:buildB:1.0'
                }
"""
        }
        includedBuilds << buildC

        when:
        resolveArtifacts()

        then:
        executedInOrder ":buildB:compileJava", ":buildB:jar", ":buildC:compileJava", ":buildC:jar"
    }

    def "handles compileOnly dependencies for different subprojects from the same build included via separate dependency paths"() {
        given:
        dependency 'org.test:b1:1.0'
        dependency 'org.test:buildC:1.0'

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
                dependencies {
                    compileOnly 'org.test:b2:1.0'
                }
"""
        }
        includedBuilds << buildC


        when:
        resolveArtifacts()

        then:
        executed ":buildB:b1:jar", ":buildB:b2:jar", ":buildC:jar"
        executed ":buildB:b1:compileJava", ":buildB:b2:compileJava", ":buildC:compileJava"
    }

    def "handles separate compile and compileOnly dependencies on different subprojects"() {
        given:
        dependency 'org.test:buildC:1.0'

        buildB.buildFile << """
            gradle.taskGraph.whenReady {
                println "Executing buildB: " + it.allTasks.collect { it.identityPath }
            }
"""

        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
                dependencies {
                    compile 'org.test:b1:1.0'
                    compileOnly 'org.test:b2:1.0'
                }
"""
        }
        includedBuilds << buildC

        when:
        resolveArtifacts()

        then:
        executedInOrder ":buildB:b1:jar", ":buildC:compileJava", ":buildC:jar"
        executedInOrder ":buildB:b2:jar", ":buildC:compileJava", ":buildC:jar"
    }

    def "reports failure to build dependent artifact"() {
        given:
        dependency "org.test:buildB:1.0"

        buildB.buildFile << """
            jar.doLast {
                throw new GradleException("jar task failed")
            }
"""
        when:
        resolveArtifactsFails()

        then:
        failure
            .assertHasDescription("Execution failed for task ':buildB:jar'")
            .assertHasCause("jar task failed")
    }

    def "reports failure to build transitive dependent artifact"() {
        given:
        dependency "org.test:buildB:1.0"

        buildB.buildFile << """
            dependencies {
                compile "org.test:buildC:1.0"
            }
"""
        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
                jar.doLast {
                    throw new GradleException("jar task failed")
                }
"""
        }
        includedBuilds << buildC

        when:
        resolveArtifactsFails()

        then:
        failure
            .assertHasDescription("Execution failed for task ':buildC:jar'")
            .assertHasCause("jar task failed")
    }

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def "builds artifacts and reports failures for dependency on multiple subprojects where one fails"() {
        given:
        server.start()
        dependency 'org.test:b1:1.0'
        buildA.buildFile << """
            dependencies {
                runtime 'org.test:b2:1.0'
            }
            resolve.doLast {
                ${server.callFromTaskAction("resolve")}
            }
            task resolveRuntime(type: Copy) {
                dependsOn "resolve"
                from configurations.runtime
                into 'libs-runtime'
            }
"""

        buildB.buildFile << """
            project(':b2') {
                jar.doLast {
                    ${server.callFromTaskAction("b2")}
                    throw new GradleException("jar task failed")
                }
            }
"""

        when:
        server.expectConcurrent("resolve", "b2")
        fails buildA, ":resolveRuntime"

        then:
        failure.assertHasFailures(1)
        failure.assertHasDescription("Execution failed for task ':buildB:b2:jar'.")
        executedTasks.containsAll(":buildB:b1:jar", ":resolve", ":buildB:b2:jar")
        !executedTasks.containsAll(":resolveRuntime")
    }

    def "new substitutions can be discovered while building the task graph for the first level included builds"() {
        given:
        def firstLevel = (1..2).collect{ "firstLevel$it" }

        buildA.buildFile << """
            dependencies {
                implementation ${firstLevel.collect { "'org.test:${it}:1.0'" }.join(", ") }
            }
        """

        firstLevel.each { buildName ->
            def build = singleProjectBuild(buildName) {
                buildFile << """
                    apply plugin: 'java'
                    dependencies {
                        //compileOnly ensures that this is not already found in the dependency graph of the root build
                        compileOnly 'org.test:secondLevel:1.0'
                    }
                """
            }
            includeBuild build
        }
        def secondLevel = singleProjectBuild("secondLevel") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        includeBuild secondLevel

        when:
        execute(buildA, "jar")

        then:
        executedTasks.contains(":secondLevel:jar")
    }

    private void resolveArtifacts() {
        execute(buildA, ":resolve", arguments)
    }

    private void resolveArtifactsFails() {
        fails(buildA, ":resolve", arguments)
    }

    private void assertResolved(TestFile... files) {
        String[] names = files.collect { it.name }
        buildA.file('libs').assertHasDescendants(names)
        files.each {
            buildA.file('libs/' + it.name).assertIsCopyOf(it)
        }
    }

    private void executedInOrder(String... tasks) {
        def executedTasks = result.executedTasks
        def beforeTask
        for (String task : tasks) {
            result.assertTaskExecuted(task)

            if (beforeTask != null) {
                assert executedTasks.indexOf(beforeTask) < executedTasks.indexOf(task) : "task ${beforeTask} must be executed before ${task}"
            }
            beforeTask = task
        }
    }
}
