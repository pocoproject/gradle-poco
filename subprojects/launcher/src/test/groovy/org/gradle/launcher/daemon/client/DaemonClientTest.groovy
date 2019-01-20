/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.client

import org.gradle.api.BuildCancelledException
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildRequestContext
import org.gradle.internal.id.UUIDGenerator
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.daemon.context.DaemonCompatibilitySpec
import org.gradle.launcher.daemon.context.DaemonConnectDetails
import org.gradle.launcher.daemon.protocol.Build
import org.gradle.launcher.daemon.protocol.BuildStarted
import org.gradle.launcher.daemon.protocol.Cancel
import org.gradle.launcher.daemon.protocol.CloseInput
import org.gradle.launcher.daemon.protocol.DaemonUnavailable
import org.gradle.launcher.daemon.protocol.Failure
import org.gradle.launcher.daemon.protocol.Finished
import org.gradle.launcher.daemon.protocol.Success
import org.gradle.launcher.daemon.server.api.DaemonStoppedException
import org.gradle.launcher.exec.BuildActionParameters
import org.gradle.launcher.exec.BuildActionResult
import org.gradle.util.ConcurrentSpecification

class DaemonClientTest extends ConcurrentSpecification {
    final DaemonConnector connector = Mock()
    final DaemonClientConnection connection = Mock()
    final OutputEventListener outputEventListener = Mock()
    final DaemonCompatibilitySpec compatibilitySpec = Mock()
    final def idGenerator = new UUIDGenerator()
    final ProcessEnvironment processEnvironment = Mock()
    final DaemonClient client = new DaemonClient(connector, outputEventListener, compatibilitySpec, new ByteArrayInputStream(new byte[0]), executorFactory, idGenerator, processEnvironment)

    def executesAction() {
        def resultMessage = Stub(BuildActionResult)

        when:
        def result = client.execute(Stub(BuildAction), Stub(BuildRequestContext), Stub(BuildActionParameters), Stub(ServiceRegistry))

        then:
        result == resultMessage
        1 * processEnvironment.maybeGetPid()
        1 * connector.connect(compatibilitySpec) >> connection
        _ * connection.daemon >> Stub(DaemonConnectDetails)
        1 * connection.dispatch({it instanceof Build})
        2 * connection.receive() >>> [Stub(BuildStarted), new Success(resultMessage)]
        1 * connection.dispatch({it instanceof CloseInput})
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()
        0 * _
    }

    def rethrowsFailureToExecuteAction() {
        RuntimeException failure = new RuntimeException()

        when:
        client.execute(Stub(BuildAction), Stub(BuildRequestContext), Stub(BuildActionParameters), Stub(ServiceRegistry))

        then:
        RuntimeException e = thrown()
        e == failure
        1 * processEnvironment.maybeGetPid()
        1 * connector.connect(compatibilitySpec) >> connection
        _ * connection.daemon >> Stub(DaemonConnectDetails)
        1 * connection.dispatch({it instanceof Build})
        2 * connection.receive() >>> [Stub(BuildStarted), new Failure(failure)]
        1 * connection.dispatch({it instanceof CloseInput})
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()
        0 * _
    }

    def "fails with an exception when build is cancelled and daemon is forcefully stopped"() {
        def cancellationToken = Mock(BuildCancellationToken)
        def buildRequestContext = Stub(BuildRequestContext) {
            getCancellationToken() >> cancellationToken
        }

        when:
        def result = client.execute(Stub(BuildAction), buildRequestContext, Stub(BuildActionParameters), Stub(ServiceRegistry))

        then:
        result.exception instanceof BuildCancelledException
        1 * processEnvironment.maybeGetPid()
        1 * connector.connect(compatibilitySpec) >> connection
        _ * connection.daemon >> Stub(DaemonConnectDetails)
        1 * cancellationToken.addCallback(_) >> { Runnable callback ->
            callback.run()
            return false
        }

        1 * connection.dispatch({it instanceof Build})
        2 * connection.receive() >>> [ Stub(BuildStarted), new Failure(new DaemonStoppedException())]
        1 * connection.dispatch({it instanceof Cancel})
        1 * connection.dispatch({it instanceof CloseInput})
        1 * connection.dispatch({it instanceof Finished})
        1 * cancellationToken.cancellationRequested >> true
        1 * cancellationToken.removeCallback(_)
        1 * connection.stop()
        0 * _
    }

    def "tries to find a different daemon if connected to a stale daemon address"() {
        def resultMessage = Stub(BuildActionResult)
        DaemonClientConnection connection2 = Mock()

        when:
        client.execute(Stub(BuildAction), Stub(BuildRequestContext), Stub(BuildActionParameters), Stub(ServiceRegistry))

        then:
        2 * connector.connect(compatibilitySpec) >>> [connection, connection2]
        _ * connection.daemon >> Stub(DaemonConnectDetails)
        1 * connection.dispatch({it instanceof Build}) >> { throw new StaleDaemonAddressException("broken", new RuntimeException())}
        1 * connection.stop()
        _ * connection2.daemon >> Stub(DaemonConnectDetails)
        2 * connection2.receive() >>> [Stub(BuildStarted), new Success(resultMessage)]
        0 * connection._
    }

    def "tries to find a different daemon if the daemon is busy"() {
        def resultMessage = Stub(BuildActionResult)
        DaemonClientConnection connection2 = Mock()

        when:
        client.execute(Stub(BuildAction), Stub(BuildRequestContext), Stub(BuildActionParameters), Stub(ServiceRegistry))

        then:
        2 * connector.connect(compatibilitySpec) >>> [connection, connection2]
        _ * connection.daemon >> Stub(DaemonConnectDetails)
        1 * connection.dispatch({it instanceof Build})
        1 * connection.receive() >> Stub(DaemonUnavailable)
        1 * connection.dispatch({it instanceof Finished})
        1 * connection.stop()
        _ * connection2.daemon >> Stub(DaemonConnectDetails)
        2 * connection2.receive() >>> [Stub(BuildStarted), new Success(resultMessage)]
        0 * connection._
    }

    def "tries to find a different daemon if the first result is null"() {
        def resultMessage = Stub(BuildActionResult)
        DaemonClientConnection connection2 = Mock()

        when:
        client.execute(Stub(BuildAction), Stub(BuildRequestContext), Stub(BuildActionParameters), Stub(ServiceRegistry))

        then:
        2 * connector.connect(compatibilitySpec) >>> [connection, connection2]
        _ * connection.daemon >> Stub(DaemonConnectDetails)
        1 * connection.dispatch({it instanceof Build})
        1 * connection.receive() >> null
        1 * connection.stop()
        _ * connection2.daemon >> Stub(DaemonConnectDetails)
        2 * connection2.receive() >>> [Stub(BuildStarted), new Success(resultMessage)]
        0 * connection._
    }

    def "does not loop forever finding usable daemons"() {
        given:
        connector.connect(compatibilitySpec) >> connection
        connection.daemon >> Stub(DaemonConnectDetails)
        connection.receive() >> Mock(DaemonUnavailable)

        when:
        client.execute(Stub(BuildAction), Stub(BuildRequestContext), Stub(BuildActionParameters), Stub(ServiceRegistry))

        then:
        thrown(NoUsableDaemonFoundException)
    }
}
