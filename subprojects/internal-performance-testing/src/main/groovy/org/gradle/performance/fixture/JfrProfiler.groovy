/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance.fixture

import com.github.chrishantha.jfr.flamegraph.output.Application
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.google.common.io.Resources
import groovy.transform.CompileStatic
import groovy.util.logging.Log
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.measure.MeasuredOperation

/**
 * Profiles performance test scenarios using the Java Flight Recorder.
 *
 * TODO create memory, IO, locking flame graphs
 * TODO generate icicle graphs
 * TODO create flame graph diffs
 * TODO support pause/resume so we can exclude clean tasks from measurement
 * TODO refactor out helpers (pid instrumentation, flame graphs, jcmd, profile directory structure)
 * TODO simplify flame graphs more, e.g. collapse task executor chain, build operation handling etc
 * TODO maybe create "raw" flame graphs too, for cases when above mentioned things actually regress
 * TODO remove setters for useDaemon/versionUnderTest/scenarioUnderTest, this should all be available from BuildExperimentInvocationInfo
 * TODO move flamegraph generation to buildSrc and offer it as a task so it can be used when people send us .jfr files
 */
@CompileStatic
@Log
class JfrProfiler implements Profiler {
    private static final String TARGET_DIR_KEY = "org.gradle.performance.flameGraphTargetDir"

    final boolean enabled

    private final File logDirectory
    private final File jcmdExecutable
    private final File pidFile
    private final File pidFileInitScript
    private final File flamegraphScript

    boolean useDaemon
    String versionUnderTest
    String scenarioUnderTest

    JfrProfiler() {
        enabled = System.getProperty(TARGET_DIR_KEY) != null && !Jvm.current().ibmJvm
        if (enabled) {
            logDirectory = new File(System.getProperty(TARGET_DIR_KEY))
            jcmdExecutable = findJcmd()
            pidFile = createPidFile()
            pidFileInitScript = createPidFileInitScript(pidFile)
            flamegraphScript = createFlamegraphScript()
        } else {
            logDirectory = null
            jcmdExecutable = null
            pidFile = null
            pidFileInitScript = null
            flamegraphScript = null
        }
    }

    private static File findJcmd() {
        File javaHome = new File(System.getProperty("java.home"));
        def jcmdPath = "bin/" + OperatingSystem.current().getExecutableName("jcmd")
        def jcmd = new File(javaHome, jcmdPath);
        if (!jcmd.isFile() && javaHome.getName().equals("jre")) {
            jcmd = new File(javaHome.getParentFile(), jcmdPath);
        }
        if (!jcmd.isFile()) {
            throw new RuntimeException("Could not find 'jcmd' executable for Java home directory " + javaHome);
        }
        jcmd
    }

    private static File createFlamegraphScript() {
        URL flamegraphResource = JfrProfiler.classLoader.getResource("org/gradle/reporting/flamegraph.pl")
        def flamegraphScript = File.createTempFile("flamegraph", ".pl")
        flamegraphScript.deleteOnExit()
        Resources.asCharSource(flamegraphResource, Charsets.UTF_8).copyTo(Files.asCharSink(flamegraphScript, Charsets.UTF_8))
        flamegraphScript.setExecutable(true)
        flamegraphScript
    }

    private static File createPidFile() {
        def pidFile = File.createTempFile("build-under-test", ".pid")
        pidFile.deleteOnExit()
        pidFile
    }

    private static File createPidFileInitScript(File pidFile) {
        def pidFileInitScript = File.createTempFile("pid-instrumentation", ".gradle")
        pidFileInitScript.deleteOnExit()
        pidFileInitScript.text = """
            def e
            if (gradleVersion == '2.0') {
              e = services.get(org.gradle.internal.nativeplatform.ProcessEnvironment)
            } else {
              e = services.get(org.gradle.internal.nativeintegration.ProcessEnvironment)
            }
            new File(new URI('${pidFile.toURI()}')).text = e.pid
        """
        pidFileInitScript
    }

    @Override
    List<String> getAdditionalJvmOpts(File workingDir) {
        if (!enabled) {
            return Collections.emptyList()
        }
        String flightRecordOptions = "stackdepth=1024"
        if (!useDaemon) {
            flightRecordOptions += ",defaultrecording=true,dumponexit=true,dumponexitpath=$jfrFile,settings=profile"
        }
        ["-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder", "-XX:FlightRecorderOptions=$flightRecordOptions", "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"] as List<String>
    }


    @Override
    List<String> getAdditionalArgs(File workingDir) {
        if (!enabled) {
            return Collections.emptyList()
        }
        return ["--init-script", pidFileInitScript.absolutePath] as List<String>
    }

    @Override
    void collect(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation) {
        if (!enabled) {
            return
        }
        if (invocationInfo.iterationNumber == invocationInfo.iterationMax && invocationInfo.phase == BuildExperimentRunner.Phase.WARMUP && useDaemon) {
            start()
        }
        if (invocationInfo.iterationNumber == invocationInfo.iterationMax && invocationInfo.phase == BuildExperimentRunner.Phase.MEASUREMENT) {
            baseDir.mkdirs()
            if (useDaemon) {
                stop()
            }
            collapseStacks()
            sanitizeStacks()
            generateFlameGraph()
        }
    }

    private void collapseStacks() {
        Application.main("-f", jfrFile.absolutePath, "-o", stacksFile.absolutePath, "-ha", "-i")
    }

    private void sanitizeStacks() {
        FlameGraphSanitizer flameGraphSanitizer = new FlameGraphSanitizer(new FlameGraphSanitizer.RegexBasedSanitizerFunction(
            (~'build_([a-z0-9]+)'): 'build script',
            (~'settings_([a-z0-9]+)'): 'settings script',
            (~'org[.]gradle[.]'): '',
            (~'sun[.]reflect[.]GeneratedMethodAccessor[0-9]+'): 'GeneratedMethodAccessor',
            (~'com[.]sun[.]proxy[.][$]Proxy[0-9]+'): 'Proxy'
        ))
        flameGraphSanitizer.sanitize(stacksFile, sanitizedStacksFile)
    }

    private void generateFlameGraph() {
        invokeFlamegraphScript(sanitizedStacksFile, flamesFile, "--minwidth", "1")
    }

    private void invokeFlamegraphScript(File input, File output, String... args) {
        def process = ([flamegraphScript.absolutePath, input.absolutePath] + args.toList()).execute()
        def fos = output.newOutputStream()
        process.waitForProcessOutput(fos, System.err)
        fos.close()
    }

    private File file(String name) {
        new File(baseDir, name)
    }

    private File getBaseDir() {
        def fileSafeScenarioName = scenarioUnderTest.replaceAll('[^a-zA-Z0-9.-]', '-').replaceAll('-+', '-')
        new File(logDirectory, fileSafeScenarioName + "/" + versionUnderTest)
    }

    private File getJfrFile() {
        file("profile.jfr")
    }

    private File getStacksFile() {
        file("stacks.txt")
    }

    private File getSanitizedStacksFile() {
        file("sanitized-stacks.txt")
    }

    private File getFlamesFile() {
        file("flames.svg")
    }

    private void start() {
        jcmd(pid, "JFR.start", "name=profile", "settings=profile")
    }

    private void stop() {
        jcmd(pid, "JFR.stop", "name=profile", "filename=${jfrFile}")
    }

    private void jcmd(String... args) {
        def processArguments = [jcmdExecutable.absolutePath] + args.toList()
        def process = processArguments.execute()
        process.waitForProcessOutput(System.out as Appendable, System.err as Appendable)
    }

    private String getPid() {
        pidFile.text
    }
}
