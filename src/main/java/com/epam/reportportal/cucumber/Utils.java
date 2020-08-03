/*
 * Copyright 2018 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.cucumber;

import com.epam.reportportal.annotations.TestCaseId;
import com.epam.reportportal.annotations.attribute.Attributes;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.utils.AttributeParser;
import com.epam.reportportal.utils.TestCaseIdUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.ParameterResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import io.cucumber.messages.Messages;
import io.cucumber.plugin.event.*;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.collect.ImmutableMap;
import rp.com.google.common.collect.Lists;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Optional.ofNullable;

public class Utils {
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);
	private static final String TABLE_INDENT = "          ";
	private static final String TABLE_SEPARATOR = "|";
	private static final String DOCSTRING_DECORATOR = "\n\"\"\"\n";
	private static final String PASSED = "passed";
	private static final String SKIPPED = "skipped";
	private static final String INFO = "INFO";
	private static final String WARN = "WARN";
	private static final String ERROR = "ERROR";
	private static final String EMPTY = "";
	private static final String ONE_SPACE = " ";
	private static final String HOOK_ = "Hook: ";
	private static final String NEW_LINE = "\r\n";

	private static final String DEFINITION_MATCH_FIELD_NAME = "definitionMatch";
	private static final String STEP_DEFINITION_FIELD_NAME = "stepDefinition";
	private static final String GET_LOCATION_METHOD_NAME = "getLocation";
	private static final String METHOD_OPENING_BRACKET = "(";
	private static final String METHOD_FIELD_NAME = "method";
	private static final String PARAMETER_REGEX = "<[^<>]+>";

	private Utils() {
		throw new AssertionError("No instances should exist for the class!");
	}

	//@formatter:off
    private static final Map<Status, ItemStatus> STATUS_MAPPING = ImmutableMap.<Status, ItemStatus>builder()
            .put(Status.PASSED, ItemStatus.PASSED)
            .put(Status.FAILED, ItemStatus.FAILED)
            .put(Status.SKIPPED, ItemStatus.SKIPPED)
            .put(Status.PENDING, ItemStatus.SKIPPED)
            .put(Status.AMBIGUOUS, ItemStatus.SKIPPED)
            .put(Status.UNDEFINED, ItemStatus.SKIPPED)
            .put(Status.UNUSED, ItemStatus.SKIPPED).build();
    //@formatter:on

	static void finishFeature(Launch rp, Maybe<String> itemId, Date dateTime) {
		if (itemId == null) {
			LOGGER.error("BUG: Trying to finish unspecified test item.");
			return;
		}
		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setEndTime(dateTime);
		rp.finishTestItem(itemId, rq);
	}

	static void finishTestItem(Launch rp, Maybe<String> itemId) {
		finishTestItem(rp, itemId, null);
	}

	static Date finishTestItem(Launch rp, Maybe<String> itemId, Status status) {
		if (itemId == null) {
			LOGGER.error("BUG: Trying to finish unspecified test item.");
			return null;
		}
		FinishTestItemRQ rq = new FinishTestItemRQ();
		Date endTime = Calendar.getInstance().getTime();
		rq.setEndTime(endTime);
		rq.setStatus(mapItemStatus(status));
		rp.finishTestItem(itemId, rq);
		return endTime;
	}

	static Maybe<String> startNonLeafNode(Launch rp, Maybe<String> rootItemId, String name, String description,
			Set<ItemAttributesRQ> attributes, String type) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setDescription(description);
		rq.setName(name);
		rq.setAttributes(attributes);
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(type);
		return rp.startTestItem(rootItemId, rq);
	}

	static void sendLog(final String message, final String level) {
		ReportPortal.emitLog(message, level, Calendar.getInstance().getTime());
	}

	/**
	 * Transform tags from Cucumber to RP format
	 *
	 * @param tags - Cucumber tags
	 * @return set of tags
	 */
	public static Set<ItemAttributesRQ> extractAttributes(List<?> tags) {
		return tags.stream().map(s -> {
			String tagValue;
			if (s instanceof Messages.GherkinDocument.Feature.Tag) {
				tagValue = ((Messages.GherkinDocument.Feature.Tag) s).getName();
			} else {
				tagValue = s.toString();
			}
			return new ItemAttributesRQ(null, tagValue);
		}).collect(Collectors.toSet());
	}

	/**
	 * Map Cucumber statuses to RP log levels
	 *
	 * @param cukesStatus - Cucumber status
	 * @return regular log level
	 */
	static String mapLevel(String cukesStatus) {
		String mapped;
		if (cukesStatus.equalsIgnoreCase(PASSED)) {
			mapped = INFO;
		} else if (cukesStatus.equalsIgnoreCase(SKIPPED)) {
			mapped = WARN;
		} else {
			mapped = ERROR;
		}
		return mapped;
	}

	/**
	 * Map Cucumber statuses to RP item statuses
	 *
	 * @param status - Cucumber status
	 * @return RP test item status and null if status is null
	 */
	static String mapItemStatus(Status status) {
		if (status == null) {
			return null;
		} else {
			if (STATUS_MAPPING.get(status) == null) {
				LOGGER.error(String.format("Unable to find direct mapping between Cucumber and ReportPortal for TestItem with status: '%s'.",
						status
				));
				return ItemStatus.SKIPPED.name();
			}
			return STATUS_MAPPING.get(status).name();
		}
	}

	/**
	 * Generate name representation
	 *
	 * @param prefix   - substring to be prepended at the beginning (optional)
	 * @param infix    - substring to be inserted between keyword and name
	 * @param argument - main text to process
	 * @param suffix   - substring to be appended at the end (optional)
	 * @return transformed string
	 */
	//TODO: pass Node as argument, not test event
	static String buildNodeName(String prefix, String infix, String argument, String suffix) {
		return buildName(prefix, infix, argument, suffix);
	}

	private static String buildName(String prefix, String infix, String argument, String suffix) {
		return (prefix == null ? EMPTY : prefix) + infix + argument + (suffix == null ? EMPTY : suffix);
	}

	/**
	 * Generate multiline argument (DataTable or DocString) representation
	 *
	 * @param step - Cucumber step object
	 * @return - transformed multiline argument (or empty string if there is
	 * none)
	 */
	static String buildMultilineArgument(TestStep step) {
		List<List<String>> table = null;
		String dockString = EMPTY;
		PickleStepTestStep pickleStep = (PickleStepTestStep) step;
		if (pickleStep.getStep().getArgument() != null) {
			StepArgument argument = pickleStep.getStep().getArgument();
			if (argument instanceof DocStringArgument) {
				dockString = ((DocStringArgument) argument).getContent();
			} else if (argument instanceof DataTableArgument) {
				table = ((DataTableArgument) argument).cells();
			}
		}

		StringBuilder marg = new StringBuilder();
		if (table != null) {
			marg.append(NEW_LINE);
			for (List<String> row : table) {
				marg.append(TABLE_INDENT).append(TABLE_SEPARATOR);
				for (String cell : row) {
					marg.append(ONE_SPACE).append(cell).append(ONE_SPACE).append(TABLE_SEPARATOR);
				}
				marg.append(NEW_LINE);
			}
		}

		if (!dockString.isEmpty()) {
			marg.append(DOCSTRING_DECORATOR).append(dockString).append(DOCSTRING_DECORATOR);
		}
		return marg.toString();
	}

	static String getStepName(TestStep step) {
		return step instanceof HookTestStep ?
				HOOK_ + ((HookTestStep) step).getHookType().toString() :
				((PickleStepTestStep) step).getStep().getText();
	}

	@Nullable
	public static Set<ItemAttributesRQ> getAttributes(TestStep testStep) {
		Field definitionMatchField = getDefinitionMatchField(testStep);
		if (definitionMatchField != null) {
			try {
				Method method = retrieveMethod(definitionMatchField, testStep);
				Attributes attributesAnnotation = method.getAnnotation(Attributes.class);
				if (attributesAnnotation != null) {
					return AttributeParser.retrieveAttributes(attributesAnnotation);
				}
			} catch (NoSuchFieldException | IllegalAccessException e) {
				return null;
			}
		}
		return null;
	}

	@Nullable
	public static String getCodeRef(TestStep testStep) {
		Field definitionMatchField = getDefinitionMatchField(testStep);

		if (definitionMatchField != null) {
			try {
				Object stepDefinitionMatch = definitionMatchField.get(testStep);
				Field stepDefinitionField = stepDefinitionMatch.getClass().getDeclaredField(STEP_DEFINITION_FIELD_NAME);
				stepDefinitionField.setAccessible(true);
				Object javaStepDefinition = stepDefinitionField.get(stepDefinitionMatch);
				Method getLocationMethod = javaStepDefinition.getClass().getDeclaredMethod(GET_LOCATION_METHOD_NAME, boolean.class);
				getLocationMethod.setAccessible(true);
				String fullCodeRef = String.valueOf(getLocationMethod.invoke(javaStepDefinition, true));
				return fullCodeRef != null ? fullCodeRef.substring(0, fullCodeRef.indexOf(METHOD_OPENING_BRACKET)) : null;
			} catch (NoSuchFieldException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
				return null;
			}

		} else {
			return null;
		}
	}

	@Nullable
	public static TestCaseIdEntry getTestCaseId(TestStep testStep, String codeRef) {
		Field definitionMatchField = getDefinitionMatchField(testStep);
		if (definitionMatchField != null) {
			try {
				Method method = retrieveMethod(definitionMatchField, testStep);
				TestCaseId testCaseIdAnnotation = method.getAnnotation(TestCaseId.class);
				return ofNullable(testCaseIdAnnotation).flatMap(annotation -> ofNullable(getTestCaseId(testCaseIdAnnotation,
						method,
						((PickleStepTestStep) testStep).getDefinitionArgument()
				)))
						.orElseGet(() -> getTestCaseId(codeRef, ((PickleStepTestStep) testStep).getDefinitionArgument()));
			} catch (NoSuchFieldException | IllegalAccessException e) {
				return getTestCaseId(codeRef, ((PickleStepTestStep) testStep).getDefinitionArgument());
			}
		} else {
			return getTestCaseId(codeRef, ((PickleStepTestStep) testStep).getDefinitionArgument());
		}
	}

	static List<ParameterResource> getParameters(List<Argument> arguments, String text) {
		List<ParameterResource> parameters = Lists.newArrayList();
		ArrayList<String> parameterNames = Lists.newArrayList();
		Matcher matcher = Pattern.compile(PARAMETER_REGEX).matcher(text);
		while (matcher.find()) {
			parameterNames.add(text.substring(matcher.start() + 1, matcher.end() - 1));
		}
		IntStream.range(0, parameterNames.size()).forEach(index -> {
			String parameterName = parameterNames.get(index);
			if (index < arguments.size()) {
				Argument argument = arguments.get(index);
				String parameterValue = argument.getValue();
				ParameterResource parameterResource = new ParameterResource();
				parameterResource.setKey(parameterName);
				if ("string".equals(argument.getParameterTypeName())) {
					parameterResource.setValue(parameterValue.trim()
							.substring(1, parameterValue.length() - 1)); // strip mandatory string quotes
				} else {
					parameterResource.setValue(parameterValue);
				}
				parameters.add(parameterResource);
			}
		});
		return parameters;
	}

	private static Method retrieveMethod(Field definitionMatchField, TestStep testStep)
			throws IllegalAccessException, NoSuchFieldException {
		Object stepDefinitionMatch = definitionMatchField.get(testStep);
		Field stepDefinitionField = stepDefinitionMatch.getClass().getDeclaredField(STEP_DEFINITION_FIELD_NAME);
		stepDefinitionField.setAccessible(true);
		Object javaStepDefinition = stepDefinitionField.get(stepDefinitionMatch);
		Field methodField = javaStepDefinition.getClass().getDeclaredField(METHOD_FIELD_NAME);
		methodField.setAccessible(true);
		return (Method) methodField.get(javaStepDefinition);
	}

	@Nullable
	private static TestCaseIdEntry getTestCaseId(TestCaseId testCaseId, Method method, List<Argument> arguments) {
		if (testCaseId.parametrized()) {
			return TestCaseIdUtils.getParameterizedTestCaseId(method, arguments.stream().map(Argument::getValue).toArray());
		} else {
			return new TestCaseIdEntry(testCaseId.value());
		}
	}

	private static TestCaseIdEntry getTestCaseId(String codeRef, List<Argument> arguments) {
		return ofNullable(arguments).filter(args -> !args.isEmpty())
				.map(args -> new TestCaseIdEntry(codeRef + TRANSFORM_PARAMETERS.apply(args)))
				.orElseGet(() -> new TestCaseIdEntry(codeRef));
	}

	private static final Function<List<Argument>, String> TRANSFORM_PARAMETERS = it -> it.stream()
			.map(Argument::getValue)
			.collect(Collectors.joining(",", "[", "]"));

	private static Field getDefinitionMatchField(TestStep testStep) {
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
}