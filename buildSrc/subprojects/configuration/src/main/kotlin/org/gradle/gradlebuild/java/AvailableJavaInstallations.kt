package org.gradle.gradlebuild.java

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.internal.GradleInternal
import org.gradle.internal.jvm.JavaInfo
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmVersionDetector
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe
import org.gradle.jvm.toolchain.internal.LocalJavaInstallation
import org.slf4j.LoggerFactory
import java.io.File


class JavaInstallation(val current: Boolean, val jvm: JavaInfo, val javaVersion: JavaVersion, private val javaInstallationProbe: JavaInstallationProbe) {
    val javaHome = jvm.javaHome

    override fun toString(): String = "$vendorAndMajorVersion (${javaHome.absolutePath})"

    val toolsJar: File? by lazy { jvm.toolsJar }
    val vendorAndMajorVersion: String by lazy {
        ProbedLocalJavaInstallation(jvm.javaHome).apply {
            javaInstallationProbe.checkJdk(jvm.javaHome).configure(this)
        }.displayName
    }
}


private
class ProbedLocalJavaInstallation(private val javaHome: File) : LocalJavaInstallation {

    private
    lateinit var name: String
    private
    lateinit var javaVersion: JavaVersion
    private
    lateinit var displayName: String

    override fun getName() = name
    override fun getDisplayName() = displayName
    override fun setDisplayName(displayName: String) { this.displayName = displayName }
    override fun getJavaVersion() = javaVersion
    override fun setJavaVersion(javaVersion: JavaVersion) { this.javaVersion = javaVersion }
    override fun getJavaHome() = javaHome
    override fun setJavaHome(javaHome: File) { throw UnsupportedOperationException("JavaHome cannot be changed") }
}


private
const val java9HomePropertyName = "java9Home"


private
const val testJavaHomePropertyName = "testJavaHome"


private
const val oracleJdk9 = "Oracle JDK 9"


open class AvailableJavaInstallations(private val project: Project, private val javaInstallationProbe: JavaInstallationProbe, private val jvmVersionDetector: JvmVersionDetector) {
    private
    val logger = LoggerFactory.getLogger(AvailableJavaInstallations::class.java)

    val currentJavaInstallation: JavaInstallation
    val javaInstallationForTest: JavaInstallation
    val javaInstallationForCompilation: JavaInstallation

    init {
        currentJavaInstallation = JavaInstallation(true, Jvm.current(), JavaVersion.current(), javaInstallationProbe)
        javaInstallationForTest = determineJavaInstallation(testJavaHomePropertyName)
        javaInstallationForCompilation = determineJavaInstallationForCompilation()
    }

    private
    fun determineJavaInstallationForCompilation() = if (JavaVersion.current().isJava9Compatible) currentJavaInstallation else determineJavaInstallation(java9HomePropertyName)

    private
    fun determineJavaInstallation(propertyName: String): JavaInstallation {
        val resolvedJavaHome = resolveJavaHomePath(propertyName)
        when (resolvedJavaHome) {
            null -> return currentJavaInstallation
            else -> return detectJavaInstallation(resolvedJavaHome)
        }
    }

    fun validateForAllBuilds() {
        validate(validateBuildJdks())
    }

    fun validateForCompilation() {
        if (remoteBuildCacheEnabled()) {
            validate(validateForRemoteCache())
        }
        validate(validateCompilationJdks())
    }

    fun validateForProductionEnvironment() {
        validate(validateProductionJdks())
    }

    private
    fun remoteBuildCacheEnabled() = (project.gradle as GradleInternal).settings.buildCache.remote?.isEnabled == true

    private
    fun validateForRemoteCache(): Map<String, Boolean> =
        mapOf("Remote cache is enabled, which requires Oracle JDK 9 to perform this build. It's currently ${currentJavaInstallation.vendorAndMajorVersion} at ${currentJavaInstallation.javaHome}." to
            (currentJavaInstallation.vendorAndMajorVersion != oracleJdk9))

    private
    fun validate(errorMessages: Map<String, Boolean>) {
        val errors = errorMessages.filterValues { it }.keys
        if (errors.isNotEmpty()) {
            throw GradleException(formatValidationError("JDKs not configured correctly for the build.", errors))
        }
    }

    private
    fun validateCompilationJdks(): Map<String, Boolean> =
        mapOf(
            "Must use JDK 9+ to perform compilation in this build. It's currently ${javaInstallationForCompilation.vendorAndMajorVersion} at ${javaInstallationForCompilation.javaHome}. " +
                "You can either run the build on JDK 9+ or set a project, system property, or environment variable '$java9HomePropertyName' to a Java9-compatible JDK home path" to
                !javaInstallationForCompilation.javaVersion.isJava9Compatible
        )

    private
    fun validateBuildJdks(): Map<String, Boolean> =
        if (!JavaVersion.current().isJava8Compatible)
            mapOf("Must use JDK 8+ to perform this build. Is currently ${currentJavaInstallation.vendorAndMajorVersion} at ${currentJavaInstallation.javaHome}." to true)
        else emptyMap()

    private
    fun validateProductionJdks(): Map<String, Boolean> =
        mapOf(
            "Must use Oracle JDK 9 to perform this build. Is currently ${currentJavaInstallation.vendorAndMajorVersion} at ${currentJavaInstallation.javaHome}." to
                (currentJavaInstallation.vendorAndMajorVersion != oracleJdk9)
        )

    private
    fun formatValidationError(mainMessage: String, validationErrors: Collection<String>): String =
        (listOf(mainMessage, "Problems found:") +
            validationErrors.map {
                "    - $it"
            }).joinToString("\n")

    private
    fun detectJavaInstallation(javaHomePath: String) =
        Jvm.forHome(File(javaHomePath)).let {
            JavaInstallation(false, Jvm.forHome(File(javaHomePath)), jvmVersionDetector.getJavaVersion(it), javaInstallationProbe)
        }

    private
    fun resolveJavaHomePath(propertyName: String): String? = when {
        project.hasProperty(propertyName) -> project.property(propertyName) as String
        System.getProperty(propertyName) != null -> System.getProperty(propertyName)
        System.getenv(propertyName) != null -> System.getenv(propertyName)
        else -> null
    }
}
