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
import io.cucumber.plugin.event.TestStep;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Optional.ofNullable;

/**
 * @author Vadzim Hushchanskou
 */
public class Utils {
	private static final String EMPTY = "";

	private static final String DEFINITION_MATCH_FIELD_NAME = "definitionMatch";
	private static final String STEP_DEFINITION_FIELD_NAME = "stepDefinition";
	private static final String METHOD_FIELD_NAME = "method";
	public static final String ONE_SPACE = "&nbsp;";
	public static final String NEW_LINE = "\r\n";
	public static final String TABLE_INDENT = "&nbsp;&nbsp;&nbsp;&nbsp;";
	public static final String TABLE_SEPARATOR = "|";
	public static final String TAG_KEY = "@";

	private Utils() {
		throw new AssertionError("No instances should exist for the class!");
	}

	//@formatter:off
	public static final Map<Status, ItemStatus> STATUS_MAPPING = ImmutableMap.<Status, ItemStatus>builder()
            .put(Status.PASSED, ItemStatus.PASSED)
            .put(Status.FAILED, ItemStatus.FAILED)
            .put(Status.SKIPPED, ItemStatus.SKIPPED)
            .put(Status.PENDING, ItemStatus.SKIPPED)
            .put(Status.AMBIGUOUS, ItemStatus.SKIPPED)
            .put(Status.UNDEFINED, ItemStatus.SKIPPED)
            .put(Status.UNUSED, ItemStatus.SKIPPED).build();

	public static final Map<Status, String> LOG_LEVEL_MAPPING = ImmutableMap.<Status, String>builder()
			.put(Status.PASSED, "INFO")
			.put(Status.FAILED, "ERROR")
			.put(Status.SKIPPED, "WARN")
			.put(Status.PENDING, "WARN")
			.put(Status.AMBIGUOUS, "WARN")
			.put(Status.UNDEFINED, "WARN")
			.put(Status.UNUSED, "WARN").build();
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

	@Deprecated
	public static Method retrieveMethod(Field definitionMatchField, TestStep testStep) throws IllegalAccessException, NoSuchFieldException {
		Object stepDefinitionMatch = definitionMatchField.get(testStep);
		Field stepDefinitionField = stepDefinitionMatch.getClass().getDeclaredField(STEP_DEFINITION_FIELD_NAME);
		stepDefinitionField.setAccessible(true);
		Object javaStepDefinition = stepDefinitionField.get(stepDefinitionMatch);
		Field methodField = javaStepDefinition.getClass().getSuperclass().getDeclaredField(METHOD_FIELD_NAME);
		methodField.setAccessible(true);
		return (Method) methodField.get(javaStepDefinition);
	}

	public static final java.util.function.Function<List<Argument>, List<?>> ARGUMENTS_TRANSFORM = arguments -> ofNullable(arguments).map(
			args -> args.stream().map(Argument::getValue).collect(Collectors.toList())).orElse(null);

	public static Field getDefinitionMatchField(TestStep testStep) {
		Class<?> clazz = testStep.getClass();
		try {
			return clazz.getField(DEFINITION_MATCH_FIELD_NAME);
		} catch (NoSuchFieldException e) {
			do {
				try {
					Field definitionMatchField = clazz.getDeclaredField(DEFINITION_MATCH_FIELD_NAME);
					definitionMatchField.setAccessible(true);
					return definitionMatchField;
				} catch (NoSuchFieldException ignore) {
				}

				clazz = clazz.getSuperclass();
			} while (clazz != null);

			return null;
		}
	}

	/**
	 * Converts a table represented as List of Lists to a formatted table string
	 *
	 * @param table a table object
	 * @return string representation of the table
	 */
	@Nonnull
	public static String formatDataTable(@Nonnull final List<List<String>> table) {
		StringBuilder result = new StringBuilder();
		int tableLength = table.stream().mapToInt(List::size).max().orElse(-1);
		List<Iterator<String>> iterList = table.stream().map(List::iterator).collect(Collectors.toList());
		List<Integer> colSizes = IntStream.range(0, tableLength)
				.mapToObj(n -> iterList.stream().filter(Iterator::hasNext).map(Iterator::next).collect(Collectors.toList()))
				.map(col -> col.stream().mapToInt(String::length).max().orElse(0))
				.collect(Collectors.toList());

		for (List<String> row : table) {
			result.append(TABLE_INDENT).append(TABLE_SEPARATOR);
			for (int i = 0; i < row.size(); i++) {
				String cell = row.get(i);
				int maxSize = colSizes.get(i) - cell.length() + 2;
				int lSpace = maxSize / 2;
				int rSpace = maxSize - lSpace;
				IntStream.range(0, lSpace).forEach(j -> result.append(ONE_SPACE));
				result.append(cell);
				IntStream.range(0, rSpace).forEach(j -> result.append(ONE_SPACE));
				result.append(TABLE_SEPARATOR);
			}
			result.append(NEW_LINE);
		}
		return result.toString().trim();
	}

	/**
	 * Parses a feature source and return all declared tags before the feature.
	 *
	 * @param feature Cucumber's Feature object
	 * @return tags set
	 */
	@Nonnull
	public static Set<String> getTags(@Nonnull Feature feature) {
		return feature.getKeyword().map(k->{
			Set<String> tags = new HashSet<>();
			for(String line : feature.getSource().split("\\r?\\n")) {
				String bareLine = line.trim();
				if(bareLine.startsWith(k)) {
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
