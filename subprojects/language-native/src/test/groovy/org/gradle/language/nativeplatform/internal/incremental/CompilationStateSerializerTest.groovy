/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.incremental

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.SerializerSpec

class CompilationStateSerializerTest extends SerializerSpec {
    private CompilationStateSerializer serializer = new CompilationStateSerializer()

    def "serializes empty state"() {
        def state = new CompilationState()

        expect:
        with (serialized(state)) {
            sourceInputs.empty
            fileStates.isEmpty()
        }
    }

    def "serializes state with source files"() {
        when:
        def fileEmpty = new File("empty")
        def fileStates = [:]
        fileStates.put(fileEmpty, compilationFileState(HashCode.fromInt(0x12345678), []))

        def fileTwo = new File("two")
        def stateTwo = compilationFileState(HashCode.fromInt(0x23456789), [new File("ONE"), new File("TWO")])
        fileStates.put(fileTwo, stateTwo)
        def state = compilationState(fileStates)

        then:
        def newState = serialized(state)
        newState.fileStates.size() == 2

        def emptyCompileState = newState.getState(fileEmpty)
        emptyCompileState.hash == HashCode.fromInt(0x12345678)
        emptyCompileState.resolvedIncludes.empty

        def otherCompileState = newState.getState(fileTwo)
        otherCompileState.hash == HashCode.fromInt(0x23456789)
        otherCompileState.resolvedIncludes == stateTwo.resolvedIncludes
    }

    def "serializes state with shared include files"() {
        when:
        def fileOne = new File("one")
        def fileStates = [:]
        def stateOne = compilationFileState(HashCode.fromInt(0x12345678), [new File("ONE"), new File("TWO")])
        fileStates.put(fileOne, stateOne)

        def fileTwo = new File("two")
        def stateTwo = compilationFileState(HashCode.fromInt(0x23456789), [new File("TWO"), new File("THREE")])
        fileStates.put(fileTwo, stateTwo)
        def state = compilationState(fileStates)

        then:
        def newState = serialized(state)
        newState.fileStates.size() == 2

        def emptyCompileState = newState.getState(fileOne)
        emptyCompileState.hash == HashCode.fromInt(0x12345678)
        emptyCompileState.resolvedIncludes == stateOne.resolvedIncludes

        def otherCompileState = newState.getState(fileTwo)
        otherCompileState.hash == HashCode.fromInt(0x23456789)
        otherCompileState.resolvedIncludes == stateTwo.resolvedIncludes
    }

    private SourceFileState compilationFileState(HashCode hash, Collection<File> resolvedIncludes) {
        return new SourceFileState(hash, ImmutableSet.copyOf(resolvedIncludes.collect { new IncludeFileState(HashCode.fromInt(123), it )}))
    }

    private CompilationState compilationState(Map<File, SourceFileState> states) {
        return new CompilationState(ImmutableMap.copyOf(states))
    }

    private CompilationState serialized(CompilationState state) {
        serialize(state, serializer) as CompilationState
    }
}
