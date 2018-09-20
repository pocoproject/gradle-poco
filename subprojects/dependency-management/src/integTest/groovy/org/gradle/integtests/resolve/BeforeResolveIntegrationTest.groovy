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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

class BeforeResolveIntegrationTest extends AbstractDependencyResolutionTest {

    @Issue("gradle/gradle#2480")
    def "can use beforeResolve hook to modify dependency excludes"() {
        def module1 = mavenRepo.module('org.test', 'excluded-dep', '1.0').publish()
        mavenRepo.module('org.test', 'direct-dep', '1.0').dependsOn(module1).publish()

        buildFile << """
repositories {
    maven { url '${mavenRepo.uri}' }
}
configurations {
    conf
}
dependencies {
    conf 'org.test:direct-dep:1.0'
}

configurations.conf.incoming.beforeResolve { resolvableDependencies ->
    resolvableDependencies.dependencies.each { dependency ->
        dependency.exclude module: 'excluded-dep'
    }
}

task printFiles {
    doLast {
        def files = configurations.conf.collect { it.name }
        println files
        assert files == ['direct-dep-1.0.jar']
    }
}

task printFilesWithConfigurationInput {
    dependsOn configurations.conf
    doLast {
        def files = configurations.conf.collect { it.name }
        println files
        assert files == ['direct-dep-1.0.jar']
    }
}

task copyFiles(type:Copy) {
    from configurations.conf
    into 'libs'
}
"""

        when:
        succeeds 'printFiles'

        then: // Succeeds: configuration is not 'resolved' as part of the task inputs
        outputContains('[direct-dep-1.0.jar]')

        when:
        succeeds 'printFilesWithConfigurationInput'

        and:
        succeeds 'copyFiles'

        then: // Currently fails: excluded dependency is copied as part of configuration
        file('libs').assertHasDescendants('direct-dep-1.0.jar')
    }

    // This emulates the behaviour of the Spring Dependency Management plugin when applying dependency excludes from a BOM
    def "can use beforeResolve hook to modify dependency excludes for configuration hierarchy"() {
        mavenRepo.module('org.test', 'module1', '1.0').publish()
        mavenRepo.module('org.test', 'module2', '1.0').publish()

        given:
        buildFile << """            
            plugins { 
                id 'java'
            }
            
            repositories {
                maven { url '${mavenRepo.uri}' }
            }
            
            dependencies {
                compile('org.test:module1:1.0')
                testCompile('org.test:module2:1.0')
            }
            
            configurations.all { configuration ->
                configuration.incoming.beforeResolve { resolvableDependencies ->
                    resolvableDependencies.dependencies.each { dependency ->
                        dependency.exclude module: 'excluded-dep'
                    }
                }
            }
            
            task resolveDependencies {
                doLast {
                    configurations.compile.files
                    configurations.testCompile.files
                }
            }
"""

        expect:
        succeeds 'resolveDependencies'
    }

    @Requires(TestPrecondition.ONLINE)
    // This emulates the behaviour of the Spring Dependency Management plugin when applying dependency excludes from a BOM
    def "can use beforeResolve hook to modify excludes for a dependency shared with an already-resolved configuration"() {
        mavenRepo.module('org.test', 'module1', '1.0').publish()
        mavenRepo.module('org.test', 'module2', '1.0')
            .dependsOn('org.test', 'module1', '1.0')
            .publish()
        mavenRepo.module('org.test', 'module3', '1.0')
            .dependsOn('org.test', 'module2', '1.0')
            .publish()

        given:
        buildFile << """
plugins {
    id 'io.spring.dependency-management' version '1.0.3.RELEASE'
}

configurations {
   aOnly
   shared
   a.extendsFrom shared
   b.extendsFrom shared
}

repositories {
    maven { url '${mavenRepo.uri}' }
}

configurations.b.incoming.beforeResolve { resolvableDependencies ->
    resolvableDependencies.dependencies.each { dependency ->
        if (dependency.name == 'module3') {
            dependency.exclude module: 'module1'
        }
    }
}

dependencies {
    shared 'org.test:module3:1.0'
}

task resolveDependencies {
    doLast {
        configurations.a.incoming.resolutionResult
        configurations.b.incoming.resolutionResult
        configurations.a.incoming.resolutionResult
    }
}
"""

        expect:
        succeeds 'resolveDependencies'
    }
}
