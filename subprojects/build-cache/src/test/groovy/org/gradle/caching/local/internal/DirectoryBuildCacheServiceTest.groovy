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

package org.gradle.caching.local.internal

import org.gradle.cache.PersistentCache
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheKey
import org.gradle.internal.resource.local.PathKeyFileStore
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
@CleanupTestDirectory
class DirectoryBuildCacheServiceTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    def cacheDir = temporaryFolder.createDir("cache")
    def fileStore = Mock(PathKeyFileStore)
    def persistentCache = Mock(PersistentCache) {
        getBaseDir() >> cacheDir
    }
    def tempFileStore = new DefaultBuildCacheTempFileStore(cacheDir)
    def service = new DirectoryBuildCacheService(fileStore, persistentCache, tempFileStore, ".failed")
    def key = Mock(BuildCacheKey)

    def "does not store partial result"() {
        def hashCode = "1234abcd"
        when:
        service.store(key, new BuildCacheEntryWriter() {
            @Override
            void writeTo(OutputStream output) throws IOException {
                // Check that partial result file is created inside the cache directory
                def cacheDirFiles = cacheDir.listFiles()
                assert cacheDirFiles.length == 1

                def partialCacheFile = cacheDirFiles[0]
                assert partialCacheFile.name.startsWith(hashCode)
                assert partialCacheFile.name.endsWith(BuildCacheTempFileStore.PARTIAL_FILE_SUFFIX)

                output << "abcd"
                throw new RuntimeException("Simulated write error")
            }

            @Override
            long getSize() {
                return 100
            }
        })
        then:
        def ex = thrown RuntimeException
        ex.message == "Simulated write error"
        cacheDir.listFiles() as List == []
        1 * key.getHashCode() >> hashCode
    }
}
