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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import spock.lang.Unroll

class LatestModuleConflictResolverTest extends AbstractConflictResolverTest {

    def setup() {
        resolver = new LatestModuleConflictResolver(new DefaultVersionComparator())
    }

    @Unroll
    def "chooses latest module version #version for candidates #candidates"() {
        given:
        candidateVersions candidates

        when:
        resolveConflicts()

        then:
        selected version

        where:
        candidates                   | version
        ['1.0', '1.1']               | '1.1'
        ['1.1', '1.0']               | '1.1'
        ['1.1', '1.2', '1.0']        | '1.2'
        ['1.0', '1.0-beta-1']        | '1.0'
        ['1.0-beta-1', '1.0-beta-2'] | '1.0-beta-2'
    }

    def "rejections can fail conflict resolution"() {
        given:
        prefer('1.2')
        strictly('1.1')

        when:
        resolveConflicts()

        then:
        resolutionFailedWith """Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':root:' --> 'org:foo' prefers '1.2'
   Dependency path ':root:' --> 'org:foo' prefers '1.1', rejects ']1.1,)'
"""
    }

    def "reasonable error message when path to dependency isn't simple"() {
        given:
        prefer('1.2', module('org', 'bar', '1.0', module('org', 'baz', '1.0')))
        strictly('1.1', module('com', 'other', '15'))

        when:
        resolveConflicts()

        then:
        resolutionFailedWith """Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':root:' --> 'org:baz:1.0' --> 'org:bar:1.0' --> 'org:foo' prefers '1.2'
   Dependency path ':root:' --> 'com:other:15' --> 'org:foo' prefers '1.1', rejects ']1.1,)'
"""
    }

    def "recognizes a rejectAll clause"() {
        given:
        prefer('1.2', module('org', 'bar', '1.0', module('org', 'baz', '1.0')))
        participants << module('org', 'foo', '', module('com', 'other', '15')).rejectAll()

        when:
        resolveConflicts()

        then:
        resolutionFailedWith """Module 'org:foo' has been rejected:
   Dependency path ':root:' --> 'org:baz:1.0' --> 'org:bar:1.0' --> 'org:foo' prefers '1.2'
   Dependency path ':root:' --> 'com:other:15' --> 'org:foo' rejects all versions
"""
    }

    def "can upgrade non strict version"() {
        given:
        prefer('1.0')
        strictly('1.1')

        when:
        resolveConflicts()

        then:
        selected '1.1'
    }

    // This documents the existing behavior, not necessarily what we want to do
    def "can select a release version over unqualified"() {
        given:
        prefer('1.0-beta-1').release()
        prefer('1.0-beta-2')

        when:
        resolveConflicts()

        then:
        selected '1.0-beta-1'
    }

}
