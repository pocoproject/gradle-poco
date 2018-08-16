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
package org.gradle.gradlebuild.testing.integrationtests.cleanup

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.kotlin.dsl.*


class CleanupPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        tasks.register("cleanUpCaches", CleanUpCaches::class.java) {
            dependsOn(":createBuildReceipt")
        }
        tasks.register("cleanUpDaemons", CleanUpDaemons::class.java)

        val killExistingProcessesStartedByGradle = tasks.register("killExistingProcessesStartedByGradle", KillLeakingJavaProcesses::class.java)

        if (BuildEnvironment.isCiServer) {
            tasks {
                getByName("clean") { // TODO: See https://github.com/gradle/gradle-native/issues/718
                    dependsOn(killExistingProcessesStartedByGradle)
                }
                subprojects {
                    this.tasks.configureEach {
                        mustRunAfter(killExistingProcessesStartedByGradle)
                    }
                }
            }
        }
    }
}
