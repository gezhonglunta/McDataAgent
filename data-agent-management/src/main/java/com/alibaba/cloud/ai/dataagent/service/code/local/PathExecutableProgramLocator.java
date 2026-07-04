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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Locates executables from the process {@code PATH}.
 */
public final class PathExecutableProgramLocator implements ExecutableProgramLocator {

	private final String pathEnvironment;

	private final boolean windows;

	public PathExecutableProgramLocator() {
		this(System.getenv("PATH"), System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"));
	}

	PathExecutableProgramLocator(String pathEnvironment, boolean windows) {
		this.pathEnvironment = pathEnvironment;
		this.windows = windows;
	}

	@Override
	public Optional<String> findFirst(List<String> programNames) {
		if (pathEnvironment == null || pathEnvironment.isBlank() || programNames == null || programNames.isEmpty()) {
			return Optional.empty();
		}

		for (String programName : programNames) {
			if (programName == null || programName.isBlank()) {
				continue;
			}
			for (String directory : pathEnvironment.split(Pattern.quote(File.pathSeparator))) {
				if (directory.isBlank()) {
					continue;
				}
				if (isExecutable(Path.of(directory, programName))
						|| windows && isExecutable(Path.of(directory, programName + ".exe"))) {
					return Optional.of(programName);
				}
			}
		}
		return Optional.empty();
	}

	private boolean isExecutable(Path path) {
		return Files.isRegularFile(path) && Files.isExecutable(path);
	}

}
