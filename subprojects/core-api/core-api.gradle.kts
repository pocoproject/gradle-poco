import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty

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
plugins {
    `java-library`
    // gradlebuild.`strict-compile`
}

dependencies {
    api(project(":baseServices"))
    api(project(":baseServicesGroovy"))
    api(project(":logging"))
    api(project(":persistentCache"))
    api(project(":processServices"))
    api(project(":resources"))

    implementation(library("ant"))
    implementation(library("commons_io"))
    implementation(library("commons_lang"))
    implementation(library("jcip"))

    testFixturesImplementation(project(":internalTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.ENTRY_POINT
}

testFixtures {
    from(":logging")
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
