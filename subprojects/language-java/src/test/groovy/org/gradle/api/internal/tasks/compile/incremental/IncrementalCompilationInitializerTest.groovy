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


package org.gradle.api.internal.tasks.compile.incremental

import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec
import org.gradle.api.tasks.util.PatternSet
import spock.lang.Specification
import spock.lang.Subject

class IncrementalCompilationInitializerTest extends Specification {

    def fileOperations = Mock(FileOperations)
    @Subject
        initializer = new IncrementalCompilationInitializer(fileOperations)

    def "prepares patterns"() {
        PatternSet filesToDelete = Mock(PatternSet)
        PatternSet sourceToCompile = Mock(PatternSet)

        when:
        initializer.preparePatterns(["com.Foo", "Bar"], filesToDelete, sourceToCompile)

        then:
        1 * filesToDelete.include('com/Foo.class')
        1 * filesToDelete.include('com/Foo.java')
        1 * filesToDelete.include('com/Foo$*.class')
        1 * filesToDelete.include('com/Foo$*.java')
        1 * filesToDelete.include('Bar.class')
        1 * filesToDelete.include('Bar.java')
        1 * filesToDelete.include('Bar$*.class')
        1 * filesToDelete.include('Bar$*.java')

        1 * sourceToCompile.include('Bar.java')
        1 * sourceToCompile.include('com/Foo.java')
        1 * sourceToCompile.include('Bar$*.java')
        1 * sourceToCompile.include('com/Foo$*.java')

        0 * _
    }

    def "configures empty source when stale classes empty"() {
        def compileSpec = Mock(JavaCompileSpec)

        when:
        initializer.initializeCompilation(compileSpec, new RecompilationSpec())

        then:
        1 * compileSpec.setSource { it.files.empty }
    }

    def "configures empty classes when aggregated types empty"() {
        def compileSpec = Mock(JavaCompileSpec)
        def spec = new RecompilationSpec()

        when:
        initializer.initializeCompilation(compileSpec, spec)

        then:
        1 * compileSpec.setClasses(ImmutableSet.of())
    }

    def "removes recompiled types from list of reprocessed types"() {
        def compileSpec = Mock(JavaCompileSpec)
        def spec = new RecompilationSpec()
        spec.getClassesToCompile().add('A')
        spec.getClassesToProcess().add('A')
        spec.getClassesToProcess().add('B')

        when:
        initializer.addClassesToProcess(compileSpec, spec)

        then:
        1 * compileSpec.setClasses(ImmutableSet.of("B"))
    }
}
