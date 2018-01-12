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

package org.gradle.internal.operations.notify

import groovy.json.JsonOutput
import org.gradle.api.internal.plugins.ApplyPluginBuildOperationType
import org.gradle.configuration.ApplyScriptPluginBuildOperationType
import org.gradle.configuration.project.ConfigureProjectBuildOperationType
import org.gradle.initialization.EvaluateSettingsBuildOperationType
import org.gradle.initialization.LoadProjectsBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.execution.ExecuteTaskBuildOperationType
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType

class BuildOperationNotificationIntegrationTest extends AbstractIntegrationSpec {

    String registerListener() {
        listenerClass() + """
        registrar.registerBuildScopeListener(listener)
        """
    }

    String registerListenerWithDrainRecordings() {
        listenerClass() + """
        registrar.registerBuildScopeListenerAndReceiveStoredOperations(listener)
        """
    }

    String listenerClass(){
        """
            def listener = new $BuildOperationNotificationListener.name() {
                void started($BuildOperationStartedNotification.name notification) {
                    def details = notification.notificationOperationDetails
                    if (details instanceof $ExecuteTaskBuildOperationType.Details.name) {
                        details = [taskPath: details.taskPath, buildPath: details.buildPath, taskClass: details.taskClass.name]
                    } else  if (details instanceof $ApplyPluginBuildOperationType.Details.name) {
                        details = [pluginId: details.pluginId, pluginClass: details.pluginClass.name, targetType: details.targetType, targetPath: details.targetPath, buildPath: details.buildPath]
                    }
                    println "STARTED: \${notification.details.class.interfaces.first().name} - \${${JsonOutput.name}.toJson(details)} - \$notification.notificationOperationId - \$notification.notificationOperationParentId"   
                }

                void finished($BuildOperationFinishedNotification.name notification) {
                    println "FINISHED: \${notification.result?.class?.interfaces?.first()?.name} - \${${JsonOutput.name}.toJson(notification.notificationOperationResult)} - \$notification.notificationOperationId - \$notification.notificationOperationParentId"
                }
            }
            def registrar = services.get($BuildOperationNotificationListenerRegistrar.name)            
        """
    }

    def "obtains notifications about init scripts"() {
        when:
        executer.requireOwnGradleUserHomeDir()
        def init = executer.gradleUserHomeDir.file("init.d/init.gradle") << """
            println "init script"
        """
        buildScript """
           ${registerListenerWithDrainRecordings()}
            task t
        """

        file("buildSrc/build.gradle") << ""

        succeeds "t"

        then:
        started(ApplyScriptPluginBuildOperationType.Details, [targetType: "gradle", targetPath: null, file: init.absolutePath, buildPath: ":", uri: null])
        started(ApplyScriptPluginBuildOperationType.Details, [targetType: "gradle", targetPath: null, file: init.absolutePath, buildPath: ":buildSrc", uri: null])
    }

    def "can emit notifications from start of build"() {
        when:
        buildScript """
           ${registerListenerWithDrainRecordings()}
            task t
        """

        succeeds "t", "-S"

        then:
        started(EvaluateSettingsBuildOperationType.Details, [settingsDir: testDirectory.absolutePath, settingsFile: settingsFile.absolutePath])
        finished(EvaluateSettingsBuildOperationType.Result, [:])
        started(LoadProjectsBuildOperationType.Details, [:])
        finished(LoadProjectsBuildOperationType.Result)
        started(ConfigureProjectBuildOperationType.Details, [buildPath: ':', projectPath: ':'])
        started(ApplyPluginBuildOperationType.Details, [pluginId: "org.gradle.help-tasks", pluginClass: "org.gradle.api.plugins.HelpTasksPlugin", targetType: "project", targetPath: ":", buildPath: ":"])
        finished(ApplyPluginBuildOperationType.Result, [:])
        started(ApplyScriptPluginBuildOperationType.Details, [targetType: "project", targetPath: ":", file: buildFile.absolutePath, buildPath: ":", uri:null])
        finished(ApplyScriptPluginBuildOperationType.Result, [:])
        finished(ConfigureProjectBuildOperationType.Result, [:])

        started(CalculateTaskGraphBuildOperationType.Details, [buildPath: ':'])
        finished(CalculateTaskGraphBuildOperationType.Result, [excludedTaskPaths: [], requestedTaskPaths: [":t"]])
        started(ExecuteTaskBuildOperationType.Details, [taskPath: ":t", buildPath: ":", taskClass: "org.gradle.api.DefaultTask"])
        finished(ExecuteTaskBuildOperationType.Result, [actionable: false, originExecutionTime: null, cachingDisabledReasonMessage: "Cacheability was not determined", upToDateMessages: null, cachingDisabledReasonCategory: "UNKNOWN", skipMessage: "UP-TO-DATE", originBuildInvocationId: null])
    }

    def "can emit notifications from point of registration"() {
        when:
        buildScript """
           ${registerListener()}
            task t
        """

        succeeds "t", "-S"

        then:
        // Operations that started before the listener registration are not included (even if they finish _after_ listener registration)
        notIncluded(EvaluateSettingsBuildOperationType)
        notIncluded(LoadProjectsBuildOperationType)
        notIncluded(ApplyPluginBuildOperationType)
        notIncluded(ConfigureProjectBuildOperationType)

        started(CalculateTaskGraphBuildOperationType.Details, [buildPath:':'])
        finished(CalculateTaskGraphBuildOperationType.Result, [excludedTaskPaths: [], requestedTaskPaths: [":t"]])
        started(ExecuteTaskBuildOperationType.Details, [taskPath: ":t", buildPath: ":", taskClass: "org.gradle.api.DefaultTask"])
        finished(ExecuteTaskBuildOperationType.Result, [actionable: false, originExecutionTime: null, cachingDisabledReasonMessage: "Cacheability was not determined", upToDateMessages: null, cachingDisabledReasonCategory: "UNKNOWN", skipMessage: "UP-TO-DATE", originBuildInvocationId: null])
    }

    def "can emit notifications for nested builds"() {
        when:
        file("buildSrc/build.gradle") << ""
        file("a/buildSrc/build.gradle") << ""
        file("a/build.gradle") << "task t"
        file("a/settings.gradle") << ""
        file("settings.gradle") << "includeBuild 'a'"
        buildScript """
           ${registerListenerWithDrainRecordings()}
            task t {
                dependsOn gradle.includedBuild("a").task(":t")
            }
        """

        succeeds "t"

        then:
        started(EvaluateSettingsBuildOperationType.Details, [settingsDir: file('buildSrc').absolutePath, settingsFile: file('buildSrc/settings.gradle').absolutePath])
        started(EvaluateSettingsBuildOperationType.Details, [settingsDir: file('a').absolutePath, settingsFile: file('a/settings.gradle').absolutePath])
        started(EvaluateSettingsBuildOperationType.Details, [settingsDir: file('a/buildSrc').absolutePath, settingsFile: file('a/buildSrc/settings.gradle').absolutePath])
        started(EvaluateSettingsBuildOperationType.Details, [settingsDir: file('.').absolutePath, settingsFile: file('settings.gradle').absolutePath])

        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":buildSrc", projectPath: ":"])
        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":a:buildSrc", projectPath: ":"])
        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":a", projectPath: ":"])
        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":", projectPath: ":"])
    }

    def "does not emit for GradleBuild tasks"() {
        when:
        def initScript = file("init.gradle") << """
            if (parent == null) {
                ${registerListener()}
            }
        """

        buildScript """
            task t(type: GradleBuild) {
                tasks = ["o"]
                startParameter.searchUpwards = false
            }
            task o
        """

        succeeds "t", "-I", initScript.absolutePath

        then:
        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":", projectPath: ":"])

        // Rough test for not getting notifications for the nested build
        executedTasks.find { it.endsWith(":o") }
        output.count(ConfigureProjectBuildOperationType.Details.name) == 1
    }

    def "listeners are deregistered after build"() {
        when:
        executer.requireDaemon().requireIsolatedDaemons()
        buildFile << registerListener() << "task t"
        succeeds("t")

        then:
        finished(CalculateTaskGraphBuildOperationType.Result, [excludedTaskPaths: [], requestedTaskPaths: [":t"]])

        when:
        // remove listener
        buildFile.text = "task x"
        succeeds("x")

        then:
        output.count(CalculateTaskGraphBuildOperationType.Result.name) == 0
    }

    // This test simulates what the build scan plugin does.
    def "drains notifications for buildSrc build"() {
        given:
        file("buildSrc/build.gradle") << ""
        file("build.gradle") << """
            ${registerListenerWithDrainRecordings()}
            task t
        """

        when:
        succeeds "t"

        then:
        output.contains(":buildSrc:compileJava") // executedTasks check fails with in process executer
        output.count(ConfigureProjectBuildOperationType.Details.name) == 2
        output.count(ExecuteTaskBuildOperationType.Details.name) == 14 // including all buildSrc task execution events
    }

    void started(Class<?> type, Map<String, ?> payload = null) {
        has(true, type, payload)
    }

    void finished(Class<?> type, Map<String, ?> payload = null) {
        has(false, type, payload)
    }

    void has(boolean started, Class<?> type, Map<String, ?> payload) {
        def string = notificationLogLine(started, type, payload)
        assert output.contains(string) : "did not emit event string: $string"
    }

    void notIncluded(Class<?> type) {
        assert !output.contains(type.name)
    }

    String notificationLogLine(boolean started, Class<?> type, Map<String, ?> payload) {
        def line = "${started ? "STARTED" : "FINISHED"}: $type.name"
        if (payload != null) {
            line += " - " + JsonOutput.toJson(payload)
        }
        return line
    }
}
