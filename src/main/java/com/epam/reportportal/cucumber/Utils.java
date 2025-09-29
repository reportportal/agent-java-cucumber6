/*
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.cucumber;

import com.epam.reportportal.listeners.ItemStatus;
import io.cucumber.core.gherkin.Feature;
import io.cucumber.plugin.event.Argument;
import io.cucumber.plugin.event.Status;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

/**
 * Utility class for static methods
 *
 * @author Vadzim Hushchanskou
 */
public class Utils {
	private static final String EMPTY = "";
	public static final String TAG_KEY = "@";

	private Utils() {
		throw new AssertionError("No instances should exist for the class!");
	}

	//@formatter:off
	public static final Map<Status, ItemStatus> STATUS_MAPPING = Map.of(
			Status.PASSED, ItemStatus.PASSED,
			Status.FAILED, ItemStatus.FAILED,
			Status.SKIPPED, ItemStatus.SKIPPED,
			Status.PENDING, ItemStatus.SKIPPED,
			Status.AMBIGUOUS, ItemStatus.SKIPPED,
			Status.UNDEFINED, ItemStatus.SKIPPED,
			Status.UNUSED, ItemStatus.SKIPPED
	);
	//@formatter:on

	/**
	 * Generate name representation
	 *
	 * @param prefix   - substring to be prepended at the beginning (optional)
	 * @param infix    - substring to be inserted between keyword and name
	 * @param argument - main text to process
	 * @return transformed string
	 */
	public static String buildName(@Nullable String prefix, @Nullable String infix, @Nullable String argument) {
		return (prefix == null ? EMPTY : prefix) + infix + argument;
	}

	public static final java.util.function.Function<List<Argument>, List<?>> ARGUMENTS_TRANSFORM = arguments -> ofNullable(arguments).map(
			args -> args.stream().map(Argument::getValue).collect(Collectors.toList())).orElse(null);

	/**
	 * Parses a feature source and return all declared tags before the feature.
	 *
	 * @param feature Cucumber's Feature object
	 * @return tags set
	 */
	@Nonnull
	public static Set<String> getTags(@Nonnull Feature feature) {
		return feature.getKeyword().map(k -> {
			Set<String> tags = new HashSet<>();
			for (String line : feature.getSource().split("\\r?\\n")) {
				String bareLine = line.trim();
				if (bareLine.startsWith(k)) {
					return tags;
				}
				if (!line.startsWith(TAG_KEY)) {
					continue;
				}
				tags.addAll(Arrays.asList(line.split("\\s+")));
			}
			return tags;
		}).orElse(Collections.emptySet());
	}
}
