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

class AbstractProviderTest extends ProviderSpec<String> {
    TestProvider provider = new TestProvider()

    @Override
    TestProvider providerWithNoValue() {
        return new TestProvider()
    }

    @Override
    TestProvider providerWithValue(String value) {
        def p = new TestProvider()
        p.value = value
        return p
    }

    @Override
    String someOtherValue() {
        "s1"
    }

    @Override
    String someValue() {
        "s2"
    }

    def "is present when value is not null"() {
        expect:
        !provider.present
        provider.value("s1")
        provider.present
    }

    def "can query with default when value is null"() {
        expect:
        provider.getOrNull() == null
        provider.getOrElse("s2") == "s2"
    }

    def "mapped provider is live"() {
        def transformer = Stub(Transformer)
        transformer.transform(_) >> {String s -> "[$s]" }

        expect:
        def mapped = provider.map(transformer)
        !mapped.present
        mapped.getOrNull() == null
        mapped.getOrElse("s2") == "s2"

        provider.value("abc")
        mapped.present
        mapped.get() == "[abc]"

        provider.value(null)
        !mapped.present

        provider.value("123")
        mapped.present
        mapped.get() == "[123]"
    }

    def "can chain mapped providers"() {
        def transformer1 = Stub(Transformer)
        transformer1.transform(_) >> {String s -> "[$s]" as String }
        def transformer2 = Stub(Transformer)
        transformer2.transform(_) >> {String s -> "-$s-" as String }

        expect:
        def mapped = provider.map(transformer1).map(transformer2)
        !mapped.present
        mapped.getOrNull() == null
        mapped.getOrElse("s2") == "s2"

        provider.value("abc")
        mapped.present
        mapped.get() == "-[abc]-"
    }

    def "cannot query mapped value when value is null"() {
        def transformer = Stub(Transformer)
        def provider = provider.map(transformer)

        when:
        provider.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No value has been specified for this provider.'
    }

    static class TestProvider extends AbstractReadOnlyProvider {
        String value

        void value(String s) {
            this.value = s
        }

        @Override
        Class getType() {
            return String
        }

        @Override
        Object getOrNull() {
            return value
        }
    }
}
