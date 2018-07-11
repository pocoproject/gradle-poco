/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.executer.ArtifactBuilder
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.junit.Test

@SuppressWarnings("IntegrationTestFixtures")
class SettingsScriptExecutionIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void executesSettingsScriptWithCorrectEnvironment() {
        createExternalJar()
        createBuildSrc()
        def implClassName = 'com.google.common.collect.Multimap'

        testFile('settings.gradle') << """
buildscript {
    dependencies { classpath files('repo/test-1.3.jar') }
}
new org.gradle.test.BuildClass()
new BuildSrcClass();
println 'quiet message'
logging.captureStandardOutput(LogLevel.ERROR)
println 'error message'
assert settings != null
assert buildscript.classLoader == getClass().classLoader.parent
assert buildscript.classLoader == Thread.currentThread().contextClassLoader
Gradle.class.classLoader.loadClass('${implClassName}')
try {
    buildscript.classLoader.loadClass('${implClassName}')
    assert false: 'should fail'
} catch (ClassNotFoundException e) {
    // expected
} finally {
    if (buildscript.classLoader instanceof Closeable) {
        buildscript.classLoader.close()
    }
}
"""
        testFile('build.gradle') << 'task doStuff'

        ExecutionResult result = inTestDirectory().withTasks('doStuff').run()
        result.assertOutputContains('quiet message')
        result.assertHasErrorOutput('error message')
    }

    private TestFile createBuildSrc() {
        return testFile('buildSrc/src/main/java/BuildSrcClass.java') << '''
            public class BuildSrcClass { }
'''
    }

    private def createExternalJar() {
        ArtifactBuilder builder = artifactBuilder();
        builder.sourceFile('org/gradle/test/BuildClass.java') << '''
            package org.gradle.test;
            public class BuildClass { }
'''
        builder.buildJar(testFile("repo/test-1.3.jar"))
    }
}
