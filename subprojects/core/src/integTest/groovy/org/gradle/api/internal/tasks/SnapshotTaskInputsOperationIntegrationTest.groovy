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

package org.gradle.api.internal.tasks

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.execution.ExecuteTaskBuildOperationType
import org.gradle.plugin.management.internal.autoapply.AutoAppliedBuildScanPlugin
import spock.lang.Unroll

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.gradlePluginRepositoryDefinition

@Unroll
class SnapshotTaskInputsOperationIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "task output caching key is exposed when build cache is enabled"() {
        given:
        executer.withBuildCacheEnabled()

        when:
        buildFile << customTaskCode('foo', 'bar')
        succeeds('customTask')

        then:
        def result = operations.first(SnapshotTaskInputsBuildOperationType).result

        then:
        result.buildCacheKey != null
        result.inputHashes.keySet() == ['input1', 'input2'] as Set
        result.outputPropertyNames == ['outputFile1', 'outputFile2']
    }

    def "task output caching key is exposed when scan plugin is applied"() {
        given:
        buildFile << customTaskCode('foo', 'bar')
        buildFile << """
            buildscript {
                repositories {
                    ${gradlePluginRepositoryDefinition()}
                }
                dependencies {
                    classpath "${AutoAppliedBuildScanPlugin.GROUP}:${AutoAppliedBuildScanPlugin.NAME}:${AutoAppliedBuildScanPlugin.VERSION}"
                }
            }
            
            apply plugin: "com.gradle.build-scan"
            buildScan {
                licenseAgreementUrl = 'https://gradle.com/terms-of-service'
                licenseAgree = 'yes'
            }
        """.stripIndent()

        when:
        succeeds('customTask', '-Dscan.dump')

        then:
        def result = operations.first(SnapshotTaskInputsBuildOperationType).result

        then:
        result.buildCacheKey != null
        result.inputHashes.keySet() == ['input1', 'input2'] as Set
        result.outputPropertyNames == ['outputFile1', 'outputFile2']
    }

    def "task output caching key is not exposed by default"() {
        when:
        buildFile << customTaskCode('foo', 'bar')
        succeeds('customTask')

        then:
        !operations.hasOperation(SnapshotTaskInputsBuildOperationType)
    }

    def "handles task with no outputs"() {
        when:
        buildScript """
            task noOutputs { 
                doLast {}
            }
        """
        succeeds('noOutputs', "--build-cache")

        then:
        def result = operations.first(SnapshotTaskInputsBuildOperationType).result
        result.buildCacheKey == null
        result.classLoaderHash == null
        result.actionClassLoaderHashes == null
        result.actionClassNames == null
        result.inputHashes == null
        result.inputPropertiesLoadedByUnknownClassLoader == null
        result.outputPropertyNames == null
    }

    def "handles task with no inputs"() {
        when:
        buildScript """
            task noInputs { 
                outputs.file "foo.txt"
                doLast {}
            }
        """
        succeeds('noInputs', "--build-cache")

        then:
        def result = operations.first(SnapshotTaskInputsBuildOperationType).result
        result.buildCacheKey != null
        result.classLoaderHash != null
        result.actionClassLoaderHashes != null
        result.actionClassNames != null
        result.inputHashes == null
        result.inputPropertiesLoadedByUnknownClassLoader == null
        result.outputPropertyNames != null
    }

    def "not sent for task with no actions"() {
        when:
        buildScript """
            task noActions { 
            }
        """
        succeeds('noActions', "--build-cache")

        then:
        !operations.hasOperation(SnapshotTaskInputsBuildOperationType)
    }

    def "handles invalid implementation classloader"() {
        given:
        buildScript """
            def classLoader = new GroovyClassLoader(this.class.classLoader) 
            def clazz = classLoader.parseClass(\"\"\"${customTaskImpl()}\"\"\")
            task customTask(type: clazz){
                input1 = 'foo'
                input2 = 'bar'
            }
        """

        when:
        succeeds('customTask', '--build-cache')

        then:
        def result = operations.first(SnapshotTaskInputsBuildOperationType).result
        result.buildCacheKey == null
        result.classLoaderHash == null
        result.actionClassLoaderHashes.last() == null
        result.actionClassNames != null
        result.inputHashes != null
        result.inputPropertiesLoadedByUnknownClassLoader == null
        result.outputPropertyNames != null
    }

    def "handles invalid action classloader"() {
        given:
        buildScript """
            ${customTaskCode('foo', 'bar')}
            def classLoader = new GroovyClassLoader(this.class.classLoader)
            def c = classLoader.parseClass ''' 
                class A implements $Action.name {
                    void execute(task) {}
                }
            '''
            customTask.doLast(c.newInstance())
        """

        when:
        succeeds('customTask', '--build-cache')

        then:
        def result = operations.first(SnapshotTaskInputsBuildOperationType).result
        result.buildCacheKey == null
        result.classLoaderHash != null
        result.actionClassLoaderHashes.last() == null
        result.actionClassNames != null
        result.inputHashes != null
        result.inputPropertiesLoadedByUnknownClassLoader == null
        result.outputPropertyNames != null
    }

    def "exposes file inputs"() {
        given:
        withBuildCache()
        settingsFile << "include 'a', 'b'"
        createDir("a") {
            file("build.gradle") << "plugins { id 'java' }"
            dir("src/main/java") {
                file("A.java") << "class A {}"
                file("B.java") << "class B {}"
                dir("a") {
                    file("A.java") << "package a; class A {}"
                    dir("a") {
                        file("A.java") << "package a.a; class A {}"
                    }
                }
            }
        }

        createDir("b") {
            file("build.gradle") << """
                plugins { id 'java' }
                dependencies { compile project(":a") }
                sourceSets.main.java.srcDir "other"
            """
            dir("src/main/java") {
                file("Thing.java") << "class Thing {}"
            }
            dir("other") {
                file("Other.java") << "class Other {}"
            }
        }

        when:
        succeeds("b:jar")

        then:
        def result = snapshotResults(":a:compileJava")
        def aCompileJava = result.inputFileProperties
        aCompileJava.size() == 5

        // All are in deprecated property
        result.inputHashes.keySet().containsAll(aCompileJava.keySet())
        // Not in just-values property
        aCompileJava.keySet().every { !result.inputValueHashes.containsKey(it) }

        with(aCompileJava.classpath) {
            hash != null
            roots.empty
            normalization == "COMPILE_CLASSPATH"
        }

        with(aCompileJava["options.sourcepath"] as Map<String, ?>) {
            hash != null
            roots.empty
            normalization == "RELATIVE_PATH"
        }

        with(aCompileJava["effectiveAnnotationProcessorPath"] as Map<String, ?>) {
            hash != null
            roots.empty
            normalization == "CLASSPATH"
        }

        with(aCompileJava.source) {
            hash != null
            normalization == "NAME_ONLY"
            roots.size() == 1
            with(roots[0]) {
                path == file("a/src/main/java").absolutePath
                children.size() == 3
                with(children[0]) {
                    path == "A.java"
                    hash != null
                }
                with(children[1]) {
                    path == "B.java"
                    hash != null
                }
                with(children[2]) {
                    path == "a"
                    children.size() == 2
                    with(children[0]) {
                        path == "A.java"
                        hash != null
                    }
                    with(children[1]) {
                        path == "a"
                        children.size() == 1
                        with(children[0]) {
                            path == "A.java"
                            hash != null
                        }
                    }
                }
            }
        }

        def bCompileJava = snapshotResults(":b:compileJava").inputFileProperties
        with(bCompileJava.classpath) {
            hash != null
            roots.size() == 1
            with(roots[0]) {
                path == file("a/build/libs/a.jar").absolutePath
                !containsKey("children")
            }
        }
        with(bCompileJava.source) {
            hash != null
            roots.size() == 2
            with(roots[0]) {
                path == file("b/src/main/java").absolutePath
                children.size() == 1
                children[0].path == "Thing.java"
            }
            with(roots[1]) {
                path == file("b/other").absolutePath
                children.size() == 1
                children[0].path == "Other.java"
            }
        }

        def bJar = snapshotResults(":b:jar").inputFileProperties
        with(bJar["rootSpec\$1"]) {
            hash != null
            roots.size() == 1
            with(roots[0]) {
                path == file("b/build/classes/java/main").absolutePath
                children.size() == 2
                children[0].path == "Other.class"
                children[1].path == "Thing.class"
            }
        }
    }

    def "handles invalid nested bean classloader"() {
        given:
        buildScript """
            ${customTaskCode('foo', 'bar')}
            def classLoader = new GroovyClassLoader(this.class.classLoader)
            def c = classLoader.parseClass ''' 
                class A {
                    @$Input.name
                    String input = 'nested'
                }
            '''
            customTask.bean = c.newInstance()
        """

        when:
        succeeds('customTask', '--build-cache')

        then:
        def result = operations.first(SnapshotTaskInputsBuildOperationType).result
        result.buildCacheKey == null
        result.inputPropertiesLoadedByUnknownClassLoader == ["bean"]
        result.classLoaderHash != null
        result.actionClassLoaderHashes != null
        result.actionClassNames != null
        result.inputHashes != null
        result.outputPropertyNames != null
    }

    private static String customTaskCode(String input1, String input2) {
        """
            ${customTaskImpl()}
            task customTask(type: CustomTask){
                input1 = '$input1'
                input2 = '$input2'
            }            
        """
    }

    private static String customTaskImpl() {
        """
            @$CacheableTask.name
            class CustomTask extends $DefaultTask.name {

                @$Input.name
                String input2
                
                @$Input.name
                String input1
                
                @$OutputFile.name
                File outputFile2 = new File(temporaryDir, "output2.txt")
                
                @$OutputFile.name
                File outputFile1 = new File(temporaryDir, "output1.txt")

                @$TaskAction.name
                void generate() {
                    outputFile1.text = "done1"
                    outputFile2.text = "done2"
                }   

                @$Nested.name
                @$Optional.name
                Object bean
            }

        """
    }

    Map<String, ?> snapshotResults(String taskPath) {
        def aCompileJavaTask = operations.first(ExecuteTaskBuildOperationType) {
            it.details.taskPath == taskPath
        }
        def results = operations.children(aCompileJavaTask, SnapshotTaskInputsBuildOperationType)
        assert results.size() == 1
        results.first().result
    }

}
