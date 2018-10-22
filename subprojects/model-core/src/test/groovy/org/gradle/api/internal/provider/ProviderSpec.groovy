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

package org.gradle.api.internal.provider

import org.gradle.api.Transformer
import org.gradle.api.provider.Provider
import spock.lang.Specification

abstract class ProviderSpec<T> extends Specification {
    abstract Provider<T> providerWithValue(T value)

    abstract Provider<T> providerWithNoValue()

    abstract T someValue()

    abstract T someOtherValue()

    def "can query value when it has as value"() {
        given:
        def provider = providerWithValue(someValue())

        expect:
        provider.present
        provider.get() == someValue()
        provider.getOrNull() == someValue()
        provider.getOrElse(someOtherValue()) == someValue()
    }

    def "mapped provider returns result of transformer"() {
        def transform = Mock(Transformer)

        given:
        def provider = providerWithValue(someValue())
        def mapped = provider.map(transform)

        when:
        mapped.present

        then:
        0 * transform._

        when:
        mapped.get() == someOtherValue()
        mapped.getOrNull() == someOtherValue()
        mapped.getOrElse(someValue()) == someOtherValue()

        then:
        _ * transform.transform(someValue()) >> someOtherValue()
    }

    def "mapped provider fails when transformer returns null"() {
        given:
        def transform = Mock(Transformer)
        def provider = providerWithValue(someValue())

        when:
        def mapped = provider.map(transform)

        then:
        mapped.present
        0 * transform._

        when:
        mapped.get()

        then:
        1 * transform.transform(someValue()) >> null
        0 * transform._

        and:
        def e = thrown(IllegalStateException)
        e.message == 'Transformer for this provider returned a null value.'

        when:
        mapped.get()

        then:
        1 * transform.transform(someValue()) >> someOtherValue()
        0 * transform._
    }

    def "flat mapped provider returns result of transformer"() {
        def transformer = Stub(Transformer)
        transformer.transform(someValue()) >> providerWithValue(someOtherValue())

        given:
        def provider = providerWithValue(someValue())
        def mapped = provider.flatMap(transformer)

        expect:
        mapped.present
        mapped.get() == someOtherValue()
        mapped.getOrNull() == someOtherValue()
        mapped.getOrElse(someValue()) == someOtherValue()
    }

    def "flat mapped provider returns result of transformer when the result has no value"() {
        def transformer = Stub(Transformer)
        transformer.transform(someValue()) >> providerWithNoValue()

        given:
        def provider = providerWithValue(someValue())
        def mapped = provider.flatMap(transformer)

        expect:
        !mapped.present
        mapped.getOrNull() == null
        mapped.getOrElse(someOtherValue()) == someOtherValue()
    }

    def "flat mapped provider fails when transformer returns null"() {
        given:
        def transform = Mock(Transformer)
        def provider = providerWithValue(someValue())
        def mapped = provider.flatMap(transform)

        when:
        mapped.get()

        then:
        1 * transform.transform(someValue()) >> null
        0 * transform._

        and:
        def e = thrown(IllegalStateException)
        e.message == 'Transformer for this provider returned a null value.'

        when:
        mapped.get()

        then:
        1 * transform.transform(someValue()) >> providerWithValue(someOtherValue())
        0 * transform._
    }

    def "cannot query value when it has none"() {
        given:
        def provider = providerWithNoValue()

        expect:
        !provider.present
        provider.getOrNull() == null
        provider.getOrElse(someValue()) == someValue()

        when:
        provider.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == "No value has been specified for this provider."
    }

    def "mapped provider with no value does not use transformer"() {
        def transformer = { throw new RuntimeException() } as Transformer

        given:
        def provider = providerWithNoValue()
        def mapped = provider.map(transformer)

        expect:
        !mapped.present
        mapped.getOrNull() == null
        mapped.getOrElse(someValue()) == someValue()

        when:
        mapped.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == "No value has been specified for this provider."
    }

    def "flat mapped provider with no value does not use transformer"() {
        def transformer = { throw new RuntimeException() } as Transformer

        given:
        def provider = providerWithNoValue()
        def mapped = provider.flatMap(transformer)

        expect:
        !mapped.present
        mapped.getOrNull() == null
        mapped.getOrElse(someValue()) == someValue()

        when:
        mapped.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == "No value has been specified for this provider."
    }

}
