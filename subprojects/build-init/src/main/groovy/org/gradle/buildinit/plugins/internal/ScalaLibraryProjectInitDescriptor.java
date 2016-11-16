/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildinit.plugins.internal;

import org.gradle.api.internal.file.FileResolver;

import java.util.Collections;

public class ScalaLibraryProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor{

    public ScalaLibraryProjectInitDescriptor(TemplateOperationFactory templateOperationFactory, FileResolver fileResolver,
                                             TemplateLibraryVersionProvider libraryVersionProvider, ProjectInitDescriptor globalSettingsDescriptor) {
        super("scala", templateOperationFactory, fileResolver, libraryVersionProvider, globalSettingsDescriptor);
    }

    @Override
    public void generate(BuildInitTestFramework testFramework) {
        globalSettingsDescriptor.generate(testFramework);
        templateOperationFactory.newTemplateOperation()
            .withTemplate("scalalibrary/build.gradle.template")
            .withTarget("build.gradle")
            .withDocumentationBindings(Collections.singletonMap("ref_userguide_scala_plugin", "scala_plugin"))
            .withBindings(Collections.singletonMap("scalaVersion", libraryVersionProvider.getVersion("scala")))
            .withBindings(Collections.singletonMap("scalaLibraryVersion", libraryVersionProvider.getVersion("scala-library")))
            .withBindings(Collections.singletonMap("scalaTestModule", "scalatest_" + libraryVersionProvider.getVersion("scala")))
            .withBindings(Collections.singletonMap("scalaTestVersion", libraryVersionProvider.getVersion("scalatest")))
            .withBindings(Collections.singletonMap("scalaXmlModule", "scala-xml_" + libraryVersionProvider.getVersion("scala")))
            .withBindings(Collections.singletonMap("scalaXmlVersion", libraryVersionProvider.getVersion("scala-xml")))
            .withBindings(Collections.singletonMap("junitVersion", libraryVersionProvider.getVersion("junit")))
            .create().generate();
        TemplateOperation scalaLibTemplateOperation = fromClazzTemplate("scalalibrary/Library.scala.template", "main");
        TemplateOperation scalaTestTemplateOperation = fromClazzTemplate("scalalibrary/LibrarySuite.scala.template", "test");
        whenNoSourcesAvailable(scalaLibTemplateOperation, scalaTestTemplateOperation).generate();
    }

    @Override
    public boolean supports(BuildInitTestFramework testFramework) {
        return false;
    }
}
