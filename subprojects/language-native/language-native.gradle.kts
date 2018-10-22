import org.gradle.gradlebuild.unittestandcompile.ModuleType

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
plugins {
    id("gradlebuild.strict-compile")
    id("gradlebuild.classycle")
}

dependencies {
    compile(project(":core"))
    compile(project(":platformNative"))
    compile(project(":maven"))
    compile(project(":ivy"))
    compile(project(":toolingApi"))

    implementation(project(":versionControl"))
    implementation(library("commons_io"))

    integTestRuntimeOnly(project(":ideNative"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":versionControl")
    from(":platformNative")
    from(":platformBase")
    from(":messaging")
    from(":platformNative", "testFixtures")
    from(":snapshots")
}

classycle {
    excludePatterns.set(listOf("org/gradle/language/nativeplatform/internal/**"))
}
