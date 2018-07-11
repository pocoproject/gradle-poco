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

package org.gradle.api.tasks.compile

import org.gradle.language.fixtures.NonIncrementalProcessorFixture

class UnknownIncrementalAnnotationProcessingIntegrationTest extends AbstractIncrementalAnnotationProcessingIntegrationTest {

    @Override
    def setup() {
        withProcessor(new NonIncrementalProcessorFixture())
    }

    def "all sources are recompiled when any class changes"() {
        def a = java "@Thing class A {}"
        java "class B {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.text = "@Thing class A { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledClasses("A", "AThing", "B")
    }

    def "the user is informed about non-incremental processors"() {
        def a = java "@Thing class A {}"

        when:
        run "compileJava"
        a.text = "@Thing class A { public void foo() {} }"
        run "compileJava", "--info"

        then:
        output.contains("Full recompilation is required because ThingProcessor is not incremental.")
    }

    def "compilation is incremental if the non-incremental processor is not used"() {
        def a = java "class A {}"
        java "class B {}"

        when:
        outputs.snapshot { run "compileJava" }
        a.text = "class A { public void foo() {} }"
        run "compileJava", "--info"

        then:
        outputs.recompiledClasses("A")
    }

    def "generated files and classes are deleted when processor is removed"() {
        given:
        def a = java "@Thing class A {}"

        when:
        outputs.snapshot { run "compileJava" }

        then:
        file("build/classes/java/main/AThing.java").exists()

        when:
        buildFile << "compileJava.options.annotationProcessorPath = files()"
        run "compileJava", "--info"

        then:
        !file("build/classes/java/main/AThing.java").exists()

        and:
        outputs.deletedClasses("AThing")

        and:
        output.contains("Annotation processor path changed")
    }

    def "generated files and classes are deleted when processor is removed"() {
        given:
        def a = java "@Thing class A {}"

        when:
        outputs.snapshot { run "compileJava" }

        then:
        file("build/classes/java/main/AThing.java").exists()

        when:
        buildFile << "compileJava.options.annotationProcessorPath = files()"
        run "compileJava", "--info"

        then:
        !file("build/classes/java/main/AThing.java").exists()

        and:
        outputs.deletedClasses("AThing")

        and:
        output.contains("Annotation processor path changed")
    }
}
