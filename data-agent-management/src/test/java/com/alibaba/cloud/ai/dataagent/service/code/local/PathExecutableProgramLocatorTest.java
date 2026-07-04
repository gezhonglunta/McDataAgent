/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.code.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathExecutableProgramLocatorTest {

	@TempDir
	private Path tempDirectory;

	@Test
	void findFirst_secondCandidateExists_returnsSecondCandidate() throws IOException {
		Path executable = Files.createFile(tempDirectory.resolve("python3"));
		assertTrue(executable.toFile().setExecutable(true));
		PathExecutableProgramLocator locator = new PathExecutableProgramLocator(tempDirectory.toString(), false);

		assertEquals(Optional.of("python3"), locator.findFirst(List.of("missing", "python3")));
	}

	@Test
	void findFirst_windowsExecutableSuffixExists_returnsProgramName() throws IOException {
		Path executable = Files.createFile(tempDirectory.resolve("python.exe"));
		assertTrue(executable.toFile().setExecutable(true));
		PathExecutableProgramLocator locator = new PathExecutableProgramLocator(tempDirectory.toString(), true);

		assertEquals(Optional.of("python"), locator.findFirst(List.of("python")));
	}

	@Test
	void findFirst_missingPathOrCandidates_returnsEmpty() {
		assertFalse(new PathExecutableProgramLocator(null, false).findFirst(List.of("python")).isPresent());
		assertFalse(new PathExecutableProgramLocator(tempDirectory.toString(), false).findFirst(List.of()).isPresent());
	}

}
