package org.gradle.gradlebuild.java

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.jvm.inspection.JvmVersionDetector
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.support.serviceOf


// TODO We should not add this to the root project but a single instance to every subproject
open class AvailableJavaInstallationsPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val javaInstallationProbe = project.serviceOf<JavaInstallationProbe>()
        val jvmVersionDetector = project.serviceOf<JvmVersionDetector>()
        extensions.create<AvailableJavaInstallations>(
            "availableJavaInstallations",
            project,
            javaInstallationProbe,
            jvmVersionDetector
        )
    }
}
