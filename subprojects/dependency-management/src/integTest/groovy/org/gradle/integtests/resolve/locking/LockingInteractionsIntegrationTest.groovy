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

package org.gradle.integtests.resolve.locking

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import spock.lang.Unroll

class LockingInteractionsIntegrationTest extends AbstractDependencyResolutionTest {

    def lockfileFixture = new LockfileFixture(testDirectory: testDirectory)

    def setup() {
        settingsFile << "rootProject.name = 'locking-interactions'"
    }

    def 'locking constraints do not bring back excluded modules'() {
        def foo = mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').dependsOn(foo).publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf('org:bar:1.+') {
        exclude module: 'foo'
    }
}
"""

        lockfileFixture.createLockfile('lockedConf', ['org:bar:1.0'])

        when:
        succeeds 'dependencies'

        then:
        outputContains('org:bar:1.0')
        outputDoesNotContain('foo')

    }

    def 'does not lock dependencies missing a version'() {
        def flatRepo = testDirectory.file('repo')
        flatRepo.createFile('my-dep-1.0.jar')

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    flatDir {
        dirs 'repo'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf name: 'my-dep-1.0'
}
"""
        when:
        succeeds 'dependencies', '--write-locks'

        then:
        outputContains('my-dep-1.0')
        lockfileFixture.verifyLockfile('lockedConf', [])
    }

    @Unroll
    def "can lock when using latest.#level"() {
        mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0-SNAPSHOT').withNonUniqueSnapshots().publish()
        mavenRepo.module('org', 'bar', '1.1').publish()
        mavenRepo.module('org', 'bar', '1.1-SNAPSHOT').withNonUniqueSnapshots().publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:bar:latest.$level'
}
"""
        def version = '1.0'
        if (level == 'integration') {
            version += '-SNAPSHOT'
        }
        lockfileFixture.createLockfile('lockedConf', ["org:bar:$version".toString()])

        when:
        succeeds 'dependencies'

        then:
        outputContains("org:bar:$resolvedVersion")

        where:
        level         | resolvedVersion
        'release'     | '1.0'
        'integration' | '1.0-SNAPSHOT'
    }

    @Unroll
    def "can write lock when using latest.#level"() {
        mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0-SNAPSHOT').withNonUniqueSnapshots().publish()
        mavenRepo.module('org', 'bar', '1.1').publish()
        mavenRepo.module('org', 'bar', '1.1-SNAPSHOT').publish()
        mavenRepo.module('org', 'bar', '1.2-SNAPSHOT').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:bar:latest.$level'
}
"""

        when:
        succeeds 'dependencies', '--write-locks'

        then:
        outputContains("org:bar:latest.$level -> $resolvedVersion")

        and:
        lockfileFixture.verifyLockfile('lockedConf', ["org:bar:$resolvedVersion"])

        where:
        level         | resolvedVersion
        'release'     | '1.1'
        'integration' | '1.2-SNAPSHOT'

    }

    def 'kind of locks snapshots but warns about it'() {
        mavenRepo.module('org', 'bar', '1.0-SNAPSHOT').publish()
        mavenRepo.module('org', 'bar', '1.0-SNAPSHOT').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:bar:1.0-SNAPSHOT'
}
"""

        when:
        succeeds 'dependencies', '--write-locks'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:bar:1.0-SNAPSHOT'])
        outputContains('Dependency lock state for configuration \'lockedConf\' contains changing modules: [org:bar:1.0-SNAPSHOT]. This means that dependencies content may still change over time.')

        when:
        mavenRepo.module('org', 'bar', '1.0-SNAPSHOT').publish()

        then:
        succeeds 'dependencies'
    }

    @Unroll
    def "can update a single lock entry when using #version"() {
        mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.1').publish()
        mavenRepo.module('org', 'bar', '1.2-SNAPSHOT').withNonUniqueSnapshots().publish()
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()
        mavenRepo.module('org', 'foo', '1.2-SNAPSHOT').withNonUniqueSnapshots().publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}
repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}
dependencies {
    lockedConf 'org:bar:$version'
    lockedConf 'org:foo:$version'
}
"""
        lockfileFixture.createLockfile('lockedConf', ['org:bar:1.0', 'org:foo:1.0'])

        when:
        succeeds 'dependencies', '--update-locks', 'org:foo'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:bar:1.0', "org:foo:$expectedVersion"])

        where:
        version              | expectedVersion
        '[1.0, 2.0)'         | '1.2-SNAPSHOT'
        '1.+'                | '1.2-SNAPSHOT'
        '+'                  | '1.2-SNAPSHOT'
        'latest.release'     | '1.1'
        'latest.integration' | '1.2-SNAPSHOT'
    }

    def 'locking works with default dependency action'() {
        mavenRepo.module('org', 'foo', '1.0').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}

configurations {
    lockedConf {
        defaultDependencies { deps ->
            deps.add(project.dependencies.create('org:foo:1.0'))
        }
    }
}

task copyFiles(type: Copy) {
    from configurations.lockedConf
    into 'build/copied'
}
"""
        when:
        succeeds 'copyFiles', '--write-locks'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:foo:1.0'])

        and:
        succeeds 'copyFiles'

    }

}
