/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import spock.lang.Issue

class ConfigurationDefaultsIntegrationTest extends AbstractDependencyResolutionTest {

    def setup() {
        mavenRepo.module("org", "default-dependency").publish()
        mavenRepo.module("org", "explicit-dependency").publish()

        buildFile << """
configurations {
    conf
    child.extendsFrom conf
}
repositories {
    maven { url '${mavenRepo.uri}' }
}

if (project.hasProperty('explicitDeps')) {
    dependencies {
        conf "org:explicit-dependency:1.0"
    }
}
task checkDefault {
    doLast {
        if (project.hasProperty('resolveChild')) {
            configurations.child.resolve()
        }

        def deps = configurations.conf.incoming.resolutionResult.allDependencies
        assert deps*.selected.id.displayName == ['org:default-dependency:1.0']

        def files = configurations.conf.files
        assert files*.name == ["default-dependency-1.0.jar"]
    }
}
task checkExplicit {
    doLast {
        def deps = configurations.conf.incoming.resolutionResult.allDependencies
        assert deps*.selected.id.displayName == ['org:explicit-dependency:1.0']

        def files = configurations.conf.files
        assert files*.name == ["explicit-dependency-1.0.jar"]
    }
}
"""
    }

    def "can use defaultDependencies to specify default dependencies"() {
        buildFile << """
configurations.conf.defaultDependencies { deps ->
    deps.add project.dependencies.create("org:default-dependency:1.0")
}
"""

        expect:
        succeeds "checkDefault"

        when:
        executer.withArgument("-PresolveChild")

        then:
        succeeds "checkDefault"

        when:
        executer.withArgument("-PexplicitDeps")

        then:
        succeeds "checkExplicit"
    }

    @Issue("gradle/gradle#3908")
    def "defaultDependencies action is executed only when configuration participates in resolution"() {
        buildFile << """
configurations {
    other
    conf {
        defaultDependencies { deps ->
            println 'project.status == ' + project.status
            assert project.status == 'foo'
            deps.add project.dependencies.create("org:default-dependency:1.0")
        }
    }
}
dependencies {
    other "org:explicit-dependency:1.0"
}
// Resolve unrelated configuration should not realize defaultDependencies
println configurations.other.files

project.status = 'foo'
"""

        expect:
        succeeds "checkDefault"
    }

    @Issue("gradle/gradle#812")
    def "can use defaultDependencies in a multi-project build"() {
        buildFile.text = """
subprojects {
    apply plugin: 'java'

    repositories {
        maven { url '${mavenRepo.uri}' }
    }
}

project(":producer") {
    configurations {
        compile {
            defaultDependencies {
                add(project.dependencies.create("org:default-dependency:1.0"))
            }
        }
    }
    dependencies {
        if (project.hasProperty('explicitDeps')) {
            compile "org:explicit-dependency:1.0"
        }
    }
}

project(":consumer") {
    dependencies {
        compile project(":producer")
    }
}

subprojects {
    task resolve {
        dependsOn configurations.compile

        doLast {
            def resolvedJars = configurations.compile.files.collect { it.name }
            if (project.hasProperty('explicitDeps')) {
                assert "explicit-dependency-1.0.jar" in resolvedJars
            } else {
                assert "default-dependency-1.0.jar" in resolvedJars
            }
        }
    }
}
"""
        settingsFile << """
include 'consumer', 'producer'
"""
        expect:
        // relying on explicit dependency
        succeeds("resolve", "-PexplicitDeps")

        // relying on default dependency
        succeeds("resolve")

    }
    def "can use defaultDependencies in a composite build"() {
        buildTestFixture.withBuildInSubDir()

        def producer = singleProjectBuild("producer") {
            buildFile << """
    apply plugin: 'java'

    repositories {
        maven { url '${mavenRepo.uri}' }
    }
    configurations {
        compile {
            defaultDependencies {
                add(project.dependencies.create("org:default-dependency:1.0"))
            }
        }
    }
"""
        }

        def consumer = singleProjectBuild("consumer") {
            settingsFile << """
    includeBuild '${producer.toURI()}'
"""
            buildFile << """
    apply plugin: 'java'
    repositories {
        maven { url '${mavenRepo.uri}' }
    }

    repositories {
        maven { url '${mavenRepo.uri}' }
    }
    dependencies {
        compile 'org.test:producer:1.0'
    }
    task resolve {
        dependsOn configurations.compile

        doLast {
            def resolvedJars = configurations.compile.files.collect { it.name }
            assert "default-dependency-1.0.jar" in resolvedJars
        }
    }
"""
        }

        expect:
        executer.inDirectory(consumer)
        succeeds ":resolve"
    }

    def "can use beforeResolve to specify default dependencies"() {
        buildFile << """
configurations.conf.incoming.beforeResolve {
    if (configurations.conf.dependencies.empty) {
        configurations.conf.dependencies.add project.dependencies.create("org:default-dependency:1.0")
    }
}
"""

        expect:
        succeeds "checkDefault"

        when:
        executer.withArgument("-PexplicitDeps")

        then:
        succeeds "checkExplicit"
    }

    def "fails if beforeResolve used to add dependencies to observed configuration"() {
        buildFile << """
configurations.conf.incoming.beforeResolve {
    if (configurations.conf.dependencies.empty) {
        configurations.conf.dependencies.add project.dependencies.create("org:default-dependency:1.0")
    }
}
"""


        when:
        executer.withArgument("-PresolveChild")

        then:
        fails "checkDefault"

        and:
        failure.assertHasCause "Cannot change dependencies of configuration ':conf' after it has been included in dependency resolution."
    }
}
