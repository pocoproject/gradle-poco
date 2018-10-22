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

import java.util.concurrent.Callable

abstract class PropertySpec<T> extends ProviderSpec<T> {
    /**
     * Returns a property with _no_ value.
     */
    abstract PropertyInternal<T> property()

    abstract T someValue()

    abstract T someOtherValue()

    abstract Class<T> type()

    def "has no value by default"() {
        expect:
        def property = property()
        !property.present
        property.getOrNull() == null
        property.getOrElse(someValue()) == someValue()

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No value has been specified for this provider.'
    }

    def "cannot get value when it has none"() {
        given:
        def property = property()

        when:
        property.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == "No value has been specified for this provider."

        when:
        property.set(someValue())
        property.get()

        then:
        noExceptionThrown()
    }

    def "can set value"() {
        given:
        def property = property()
        property.set(someValue())

        expect:
        property.present
        property.get() == someValue()
        property.getOrNull() == someValue()
        property.getOrElse(someOtherValue()) == someValue()
        property.getOrElse(null) == someValue()
    }

    def "can set value using provider"() {
        def provider = Mock(ProviderInternal)

        given:
        provider.type >> type()

        def property = property()
        property.set(provider)

        when:
        def r = property.present

        then:
        r
        1 * provider.present >> true
        0 * _

        when:
        def r2 = property.get()

        then:
        r2 == someValue()
        1 * provider.get() >> someValue()
        0 * _

        when:
        def r3 = property.getOrNull()

        then:
        r3 == someOtherValue()
        1 * provider.getOrNull() >> someOtherValue()
        0 * _

        when:
        def r4 = property.getOrElse(someOtherValue())

        then:
        r4 == someValue()
        1 * provider.getOrNull() >> someValue()
        0 * _
    }

    def "tracks value of provider"() {
        def provider = Mock(ProviderInternal)

        given:
        provider.type >> type()
        provider.get() >>> [someValue(), someOtherValue(), someValue()]

        def property = property()
        property.set(provider)

        expect:
        property.get() == someValue()
        property.get() == someOtherValue()
        property.get() == someValue()
    }

    def "does not allow a null provider"() {
        given:
        def property = property()

        when:
        property.set((Provider) null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot set the value of a property using a null provider.'
    }

    def "can set untyped using null"() {
        given:
        def property = property()
        property.setFromAnyValue(null)

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(someOtherValue()) == someOtherValue()
    }

    def "can set untyped using value"() {
        given:
        def property = property()
        property.setFromAnyValue(someValue())

        expect:
        property.present
        property.get() == someValue()
        property.getOrNull() == someValue()
        property.getOrElse(someOtherValue()) == someValue()
        property.getOrElse(null) == someValue()
    }

    def "fails when untyped value is set using incompatible type"() {
        def property = property()

        when:
        property.setFromAnyValue(new Thing())

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot set the value of a property of type ${type().name} using an instance of type ${Thing.name}."
    }

    def "can set untyped using provider"() {
        def provider = Stub(ProviderInternal)

        given:
        provider.type >> type()
        provider.get() >> someValue()
        provider.present >> true

        def property = property()
        property.setFromAnyValue(provider)

        when:
        def r = property.present
        def r2 = property.get()

        then:
        r
        r2 == someValue()
    }

    def "can map value using a transformation"() {
        def transformer = Mock(Transformer)
        def property = property()

        when:
        def provider = property.map(transformer)

        then:
        0 * _

        when:
        property.set(someValue())
        def r1 = provider.get()

        then:
        r1 == someOtherValue()
        1 * transformer.transform(someValue()) >> someOtherValue()
        0 * _

        when:
        def r2 = provider.get()

        then:
        r2 == someValue()
        1 * transformer.transform(someValue()) >> someValue()
        0 * _
    }

    def "transformation is provided with the current value of the property each time the value is queried"() {
        def transformer = Mock(Transformer)
        def property = property()

        when:
        def provider = property.map(transformer)

        then:
        0 * _

        when:
        property.set(someValue())
        def r1 = provider.get()

        then:
        r1 == 123
        1 * transformer.transform(someValue()) >> 123
        0 * _

        when:
        property.set(someOtherValue())
        def r2 = provider.get()

        then:
        r2 == 456
        1 * transformer.transform(someOtherValue()) >> 456
        0 * _
    }

    def "can map value to some other type"() {
        def transformer = Mock(Transformer)
        def property = property()

        when:
        def provider = property.map(transformer)

        then:
        0 * _

        when:
        property.set(someValue())
        def r1 = provider.get()

        then:
        r1 == 12
        1 * transformer.transform(someValue()) >> 12
        0 * _

        when:
        def r2 = provider.get()

        then:
        r2 == 10
        1 * transformer.transform(someValue()) >> 10
        0 * _
    }

    def "mapped provider has no value and transformer is not invoked when property has no value"() {
        def transformer = Mock(Transformer)
        def property = property()

        when:
        def provider = property.map(transformer)

        then:
        !provider.present
        provider.getOrNull() == null
        provider.getOrElse(someOtherValue()) == someOtherValue()
        0 * _

        when:
        provider.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No value has been specified for this provider.'
    }

    def "can finalize value when no value defined"() {
        def property = property()

        when:
        property."$method"()

        then:
        !property.present
        property.getOrNull() == null

        where:
        method << ["finalizeValue", "finalizeValueOnReadAndWarnAboutChanges"]
    }

    def "can finalize value when value set"() {
        def property = property()

        when:
        property.set(someValue())
        property."$method"()

        then:
        property.present
        property.getOrNull() == someValue()

        where:
        method << ["finalizeValue", "finalizeValueOnReadAndWarnAboutChanges"]
    }

    def "replaces provider with fixed value when value finalized"() {
        def property = property()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.finalizeValue()

        then:
        1 * function.call() >> someValue()
        0 * _

        when:
        def present = property.present
        def result = property.getOrNull()

        then:
        present
        result == someValue()
        0 * _
    }

    def "replaces provider with fixed value when value finalized on next read"() {
        def property = property()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.finalizeValueOnReadAndWarnAboutChanges()

        then:
        0 * _

        when:
        def present = property.present
        def result = property.getOrNull()

        then:
        1 * function.call() >> someValue()
        0 * _

        and:
        present
        result == someValue()
    }

    def "replaces provider with fixed value when value finalized after finalize on next read"() {
        def property = property()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)
        property.finalizeValueOnReadAndWarnAboutChanges()

        when:
        property.finalizeValue()

        then:
        1 * function.call() >> someValue()
        0 * _

        when:
        def present = property.present
        def result = property.getOrNull()

        then:
        0 * _

        and:
        present
        result == someValue()
    }

    def "replaces provider with fixed missing value when value finalized"() {
        def property = property()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.finalizeValue()

        then:
        1 * function.call() >> null
        0 * _

        when:
        def present = property.present
        def result = property.getOrNull()

        then:
        !present
        result == null
        0 * _
    }

    def "replaces provider with fixed missing value when value finalized on next read"() {
        def property = property()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.finalizeValueOnReadAndWarnAboutChanges()

        then:
        0 * _

        when:
        def present = property.present
        def result = property.getOrNull()

        then:
        1 * function.call() >> null
        0 * _

        and:
        !present
        result == null
    }

    def "can finalize value when already finalized"() {
        def property = property()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        when:
        property.set(provider)
        property.finalizeValue()

        then:
        1 * function.call() >> someValue()
        0 * _

        when:
        property.finalizeValue()
        property.finalizeValueOnReadAndWarnAboutChanges()
        property.finalizeValueOnReadAndWarnAboutChanges()

        then:
        0 * _
    }

    def "cannot set value after value finalized"() {
        given:
        def property = property()
        property.set(someValue())
        property.finalizeValue()

        when:
        property.set(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "ignores set value after value finalized leniently"() {
        given:
        def property = property()
        property.set(someValue())
        property.finalizeValueOnReadAndWarnAboutChanges()
        property.get()

        when:
        property.set(someOtherValue())

        then:
        property.get() == someValue()
    }

    def "cannot set value after value finalized after value finalized leniently"() {
        given:
        def property = property()
        property.set(someValue())
        property.finalizeValueOnReadAndWarnAboutChanges()
        property.set(someOtherValue())
        property.finalizeValue()

        when:
        property.set(someOtherValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "cannot set value using provider after value finalized"() {
        given:
        def property = property()
        property.set(someValue())
        property.finalizeValue()

        when:
        property.set(Mock(ProviderInternal))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "ignores set value using provider after value finalized leniently"() {
        given:
        def property = property()
        property.set(someValue())
        property.finalizeValueOnReadAndWarnAboutChanges()
        property.get()

        when:
        property.set(Mock(ProviderInternal))

        then:
        property.get() == someValue()
    }

    def "cannot set value using any type after value finalized"() {
        given:
        def property = property()
        property.set(someValue())
        property.finalizeValue()

        when:
        property.setFromAnyValue(someOtherValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.setFromAnyValue(Stub(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "ignores set value using any type after value finalized leniently"() {
        given:
        def property = property()
        property.set(someValue())
        property.finalizeValueOnReadAndWarnAboutChanges()
        property.get()

        when:
        property.setFromAnyValue(someOtherValue())

        then:
        property.get() == someValue()

        when:
        property.setFromAnyValue(Stub(ProviderInternal))

        then:
        property.get() == someValue()
    }

    static class Thing { }
}
