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

import com.epam.reportportal.annotations.TestCaseId;
import com.epam.reportportal.annotations.attribute.Attributes;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.message.ReportPortalMessage;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.service.tree.TestItemTree;
import com.epam.reportportal.utils.*;
import com.epam.reportportal.utils.files.ByteSource;
import com.epam.reportportal.utils.formatting.MarkdownUtils;
import com.epam.reportportal.utils.http.ContentType;
import com.epam.reportportal.utils.properties.SystemAttributesExtractor;
import com.epam.reportportal.utils.reflect.Accessible;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.ParameterResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import io.cucumber.core.gherkin.Feature;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.*;
import io.reactivex.Maybe;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.epam.reportportal.cucumber.Utils.*;
import static com.epam.reportportal.cucumber.util.ItemTreeUtils.createKey;
import static com.epam.reportportal.cucumber.util.ItemTreeUtils.retrieveLeaf;
import static com.epam.reportportal.utils.formatting.ExceptionUtils.getStackTrace;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Abstract Cucumber 5.x formatter for Report Portal
 *
 * @author Vadzim Hushchanskou
 */
public abstract class AbstractReporter implements ConcurrentEventListener {
	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractReporter.class);
	private static final ThreadLocal<AbstractReporter> INSTANCES = new InheritableThreadLocal<>();

	private static final String NO_NAME = "No name";
	private static final String AGENT_PROPERTIES_FILE = "agent.properties";
	private static final String DEFINITION_MATCH_FIELD_NAME = "definitionMatch";
	private static final String STEP_DEFINITION_FIELD_NAME = "stepDefinition";
	private static final String GET_LOCATION_METHOD_NAME = "getLocation";
	private static final String COLON_INFIX = ": ";
	private static final String SKIPPED_ISSUE_KEY = "skippedIssue";
	public static final String BACKGROUND_PREFIX = "BACKGROUND: ";

	protected static final URI WORKING_DIRECTORY = new File(System.getProperty("user.dir")).toURI();
	protected static final String METHOD_OPENING_BRACKET = "(";
	protected static final String DOCSTRING_DECORATOR = "\n\"\"\"\n";
	private static final String ERROR_FORMAT = "Error:\n%s";

	private final Map<URI, FeatureContext> featureContextMap = new ConcurrentHashMap<>();
	private final TestItemTree itemTree = new TestItemTree();
	private final ReportPortal rp = buildReportPortal();

	// There is no event for recognizing end of feature in Cucumber.
	// This map is used to record the last scenario time and its feature uri.
	// End of feature occurs once launch is finished.
	private final Map<URI, Instant> featureEndTime = new ConcurrentHashMap<>();

	/**
	 * This map uses to record the description of the scenario and the step to append the error to the description.
	 */
	private final Map<Maybe<String>, String> descriptionsMap = new ConcurrentHashMap<>();
	/**
	 * This map uses to record errors to append to the description.
	 */
	private final Map<Maybe<String>, Throwable> errorMap = new ConcurrentHashMap<>();

	/**
	 * A method for creation a Start Launch request which will be sent to Report Portal. You can customize it by overriding the method.
	 *
	 * @param startTime  launch start time, which will be set into the result request
	 * @param parameters Report Portal client parameters
	 * @return a Start Launch request instance
	 */
	protected StartLaunchRQ buildStartLaunchRq(Instant startTime, ListenerParameters parameters) {
		StartLaunchRQ rq = new StartLaunchRQ();
		rq.setName(parameters.getLaunchName());
		rq.setStartTime(startTime);
		rq.setMode(parameters.getLaunchRunningMode());
		Set<ItemAttributesRQ> attributes = new HashSet<>(parameters.getAttributes());
		rq.setAttributes(attributes);
		attributes.addAll(SystemAttributesExtractor.extract(AGENT_PROPERTIES_FILE, AbstractReporter.class.getClassLoader()));
		rq.setDescription(parameters.getDescription());
		rq.setRerun(parameters.isRerun());
		if (isNotBlank(parameters.getRerunOf())) {
			rq.setRerunOf(parameters.getRerunOf());
		}

		if (null != parameters.getSkippedAnIssue()) {
			ItemAttributesRQ skippedIssueAttribute = new ItemAttributesRQ();
			skippedIssueAttribute.setKey(SKIPPED_ISSUE_KEY);
			skippedIssueAttribute.setValue(parameters.getSkippedAnIssue().toString());
			skippedIssueAttribute.setSystem(true);
			attributes.add(skippedIssueAttribute);
		}
		return rq;
	}

	private final Supplier<Launch> launch = new MemoizingSupplier<>(new Supplier<>() {

		/* should not be lazy */
		private final Instant startTime = Instant.now();

		@Override
		public Launch get() {
			StartLaunchRQ rq = buildStartLaunchRq(startTime, getReportPortal().getParameters());
			Launch myLaunch = getReportPortal().newLaunch(rq);
			itemTree.setLaunchId(myLaunch.start());
			return myLaunch;
		}
	});

	public AbstractReporter() {
		INSTANCES.set(this);
	}

	/**
	 * @return a full Test Item Tree with attributes
	 */
	@Nonnull
	public TestItemTree getItemTree() {
		return itemTree;
	}

	/**
	 * Returns a reporter instance for the current thread.
	 *
	 * @return reporter instance for the current thread
	 */
	@Nonnull
	public static AbstractReporter getCurrent() {
		return INSTANCES.get();
	}

	/**
	 * @return a {@link ReportPortal} class instance which is used to communicate with the portal
	 */
	@Nonnull
	public ReportPortal getReportPortal() {
		return rp;
	}

	/**
	 * @return a Report Portal {@link Launch} class instance which is used in test item reporting
	 */
	@Nonnull
	public Launch getLaunch() {
		return launch.get();
	}

	/**
	 * Manipulations before the launch starts
	 */
	protected void beforeLaunch() {
		getLaunch();
	}

	/**
	 * Extension point to customize ReportPortal instance
	 *
	 * @return ReportPortal
	 */
	protected ReportPortal buildReportPortal() {
		return ReportPortal.builder().build();
	}

	/**
	 * Finish RP launch
	 */
	protected void afterLaunch() {
		FinishExecutionRQ finishLaunchRq = new FinishExecutionRQ();
		finishLaunchRq.setEndTime(Instant.now());
		getLaunch().finish(finishLaunchRq);
	}

	private void addToTree(Feature feature, TestCase testCase, Maybe<String> scenarioId) {
		retrieveLeaf(feature.getUri(), itemTree).ifPresent(suiteLeaf -> suiteLeaf.getChildItems()
				.put(createKey(testCase.getLocation().getLine()), TestItemTree.createTestItemLeaf(scenarioId)));
	}

	/**
	 * Transform tags from Cucumber to RP format
	 *
	 * @param tags - Cucumber tags
	 * @return set of tags
	 */
	@Nonnull
	protected Set<ItemAttributesRQ> extractAttributes(@Nonnull Collection<?> tags) {
		return tags.stream().map(Object::toString).map(tagValue -> new ItemAttributesRQ(null, tagValue)).collect(Collectors.toSet());
	}

	@FunctionalInterface
	private interface FeatureContextAware {
		void executeWithContext(@Nonnull FeatureContext featureContext);
	}

	private void execute(@Nonnull URI uri, @Nonnull FeatureContextAware context) {
		Optional<FeatureContext> feature = ofNullable(featureContextMap.get(uri));
		if (feature.isPresent()) {
			context.executeWithContext(feature.get());
		} else {
			LOGGER.warn("Unable to locate corresponding Feature for URI: {}", uri);
		}
	}

	/**
	 * Return a Test Case ID for a feature file
	 *
	 * @param codeRef   a code reference
	 * @param arguments a scenario arguments
	 * @return Test Case ID entity or null if it's not possible to calculate
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	protected TestCaseIdEntry getTestCaseId(@Nullable String codeRef, @Nullable List<Argument> arguments) {
		return TestCaseIdUtils.getTestCaseId(codeRef, (List<Object>) ARGUMENTS_TRANSFORM.apply(arguments));
	}

	/**
	 * Extension point to customize scenario creation event/request
	 *
	 * @param testCase Cucumber's TestCase object
	 * @param name     the scenario name
	 * @param uri      the scenario feature file relative path
	 * @param line     the scenario text line number
	 * @return start test item request ready to send on RP
	 */
	@Nonnull
	protected StartTestItemRQ buildStartScenarioRequest(@Nonnull TestCase testCase, @Nonnull String name, @Nonnull URI uri, int line) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(name);
		rq.setDescription(getDescription(testCase, uri));
		String codeRef = getCodeRef(uri, line);
		rq.setCodeRef(codeRef);
		Set<String> tags = new HashSet<>(testCase.getTags());
		execute(uri, f -> tags.removeAll(f.getTags()));
		rq.setAttributes(extractAttributes(tags));
		rq.setStartTime(Instant.now());
		String type = getScenarioTestItemType();
		rq.setType(type);
		if ("STEP".equals(type)) {
			rq.setTestCaseId(ofNullable(getTestCaseId(codeRef, null)).map(TestCaseIdEntry::getId).orElse(null));
		}
		return rq;
	}

	/**
	 * Start Cucumber Scenario
	 *
	 * @param featureId       parent feature item id
	 * @param startScenarioRq scenario start request
	 * @return scenario item id
	 */
	@Nonnull
	protected Maybe<String> startScenario(@Nonnull Maybe<String> featureId, @Nonnull StartTestItemRQ startScenarioRq) {
		return getLaunch().startTestItem(featureId, startScenarioRq);
	}

	@FunctionalInterface
	private interface ScenarioContextAware {

		void executeWithContext(@Nonnull FeatureContext featureContext, @Nonnull ScenarioContext scenarioContext);
	}

	private void execute(@Nonnull TestCase testCase, @Nonnull ScenarioContextAware context) {
		URI uri = testCase.getUri();
		int line = testCase.getLocation().getLine();
		execute(
				uri, f -> {
					Optional<ScenarioContext> scenario = f.getScenario(line);
					if (scenario.isPresent()) {
						context.executeWithContext(f, scenario.get());
					} else {
						LOGGER.warn("Unable to locate corresponding Feature or Scenario context for URI: {}; line: {}", uri, line);
					}
				}
		);
	}

	private void removeFromTree(Feature featureContext, TestCase scenarioContext) {
		retrieveLeaf(featureContext.getUri(), itemTree).ifPresent(suiteLeaf -> suiteLeaf.getChildItems()
				.remove(createKey(scenarioContext.getLocation().getLine())));
	}

	/**
	 * Finish Cucumber scenario
	 * Put scenario end time in a map to check last scenario end time per feature
	 *
	 * @param event Cucumber's TestCaseFinished object
	 */
	protected void afterScenario(TestCaseFinished event) {
		TestCase testCase = event.getTestCase();
		execute(
				testCase, (f, s) -> {
					URI featureUri = f.getUri();
					if (mapItemStatus(event.getResult().getStatus()) == ItemStatus.FAILED) {
						Optional.ofNullable(event.getResult().getError()).ifPresent(error -> errorMap.put(s.getId(), error));
					}
					Instant endTime = finishTestItem(s.getId(), mapItemStatus(event.getResult().getStatus()), null);
					featureEndTime.put(featureUri, endTime);
					removeFromTree(f.getFeature(), testCase);
				}
		);
	}

	/**
	 * Generate a step name.
	 *
	 * @param testStep Cucumber's TestStep object
	 * @return a step name
	 */
	@Nullable
	protected String getStepName(@Nonnull PickleStepTestStep testStep) {
		return testStep.getStep().getText();
	}

	/**
	 * Return a Test Case ID for mapped code
	 *
	 * @param testStep Cucumber's TestStep object
	 * @param codeRef  a code reference
	 * @return Test Case ID entity or null if it's not possible to calculate
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	protected TestCaseIdEntry getTestCaseId(@Nonnull TestStep testStep, @Nullable String codeRef) {
		List<Argument> arguments = ((PickleStepTestStep) testStep).getDefinitionArgument();

		return ofNullable(codeRef).flatMap(r -> {
			Pair<String, String> splitCodeRef = parseJavaCodeRef(codeRef);
			Optional<Class<?>> testStepClass = getStepClass(splitCodeRef.getKey(), codeRef);
			return testStepClass.flatMap(c -> getStepMethod(c, splitCodeRef.getValue()))
					.map(m -> TestCaseIdUtils.getTestCaseId(
							m.getAnnotation(TestCaseId.class),
							m,
							codeRef,
							(List<Object>) ARGUMENTS_TRANSFORM.apply(arguments)
					));
		}).orElseGet(() -> getTestCaseId(codeRef, arguments));
	}

	/**
	 * Extension point to customize test creation event/request
	 *
	 * @param testStep   a cucumber step object
	 * @param stepPrefix a prefix of the step (e.g. 'Background')
	 * @param keyword    a step keyword (e.g. 'Given')
	 * @return a Request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartStepRequest(@Nonnull PickleStepTestStep testStep, @Nullable String stepPrefix,
			@Nullable String keyword) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(Utils.buildName(stepPrefix, keyword, getStepName(testStep)));
		rq.setDescription(buildMultilineArgument(testStep));
		rq.setStartTime(Instant.now());
		rq.setType("STEP");
		String codeRef = getCodeRef(testStep);
		rq.setParameters(getParameters(codeRef, testStep));
		rq.setCodeRef(codeRef);
		rq.setTestCaseId(ofNullable(getTestCaseId(testStep, codeRef)).map(TestCaseIdEntry::getId).orElse(null));
		ofNullable(codeRef).ifPresent(c -> rq.setAttributes(getAttributes(c)));
		return rq;
	}

	/**
	 * Start Step item on Report Portal
	 *
	 * @param scenarioId  parent scenario item id
	 * @param startStepRq step start request
	 * @return step item id
	 */
	@Nonnull
	protected Maybe<String> startStep(@Nonnull Maybe<String> scenarioId, @Nonnull StartTestItemRQ startStepRq) {
		return getLaunch().startTestItem(scenarioId, startStepRq);
	}

	private void addToTree(@Nonnull TestCase scenario, @Nullable String text, @Nullable Maybe<String> stepId) {
		retrieveLeaf(scenario.getUri(), scenario.getLocation().getLine(), itemTree).ifPresent(scenarioLeaf -> scenarioLeaf.getChildItems()
				.put(createKey(text), TestItemTree.createTestItemLeaf(stepId)));
	}

	/**
	 * Start Cucumber step
	 *
	 * @param testCase Cucumber's TestCase object
	 * @param step     a cucumber step object
	 */
	protected void beforeStep(@Nonnull TestCase testCase, @Nonnull PickleStepTestStep step) {
		execute(
				testCase, (f, s) -> {
					String stepPrefix = step.getStep().getLocation().getLine() < s.getLine() ? BACKGROUND_PREFIX : null;
					StartTestItemRQ rq = buildStartStepRequest(step, stepPrefix, step.getStep().getKeyword());
					Maybe<String> stepId = startStep(s.getId(), rq);
					if (rq.isHasStats()) {
						descriptionsMap.put(stepId, ofNullable(rq.getDescription()).orElse(StringUtils.EMPTY));
					}
					s.setStepId(stepId);
					String stepText = step.getStep().getText();
					if (getLaunch().getParameters().isCallbackReportingEnabled()) {
						addToTree(testCase, stepText, stepId);
					}
				}
		);
	}

	/**
	 * Finish Cucumber step
	 *
	 * @param testCase Cucumber's TestCase object
	 * @param testStep a cucumber step object
	 * @param result   Step result
	 */
	@SuppressWarnings("unused")
	protected void afterStep(@Nonnull TestCase testCase, @Nonnull PickleStepTestStep testStep, @Nonnull Result result) {
		execute(
				testCase, (f, s) -> {
					reportResult(result);
					if (mapItemStatus(result.getStatus()) == ItemStatus.FAILED) {
						Optional.ofNullable(result.getError()).ifPresent(error -> errorMap.put(s.getStepId(), error));
					}
					finishTestItem(s.getStepId(), mapItemStatus(result.getStatus()), null);
					s.setStepId(Maybe.empty());
				}
		);
	}

	/**
	 * Returns hook type and name as a <code>Pair</code>
	 *
	 * @param hookType Cucumber's hoo type
	 * @return a pair of type and name
	 */
	@Nonnull
	protected Pair<String, String> getHookTypeAndName(@Nonnull HookType hookType) {
		switch (hookType) {
			case BEFORE:
				return Pair.of(ItemType.BEFORE_TEST.name(), "Before hook");
			case BEFORE_STEP:
				return Pair.of(ItemType.BEFORE_METHOD.name(), "Before step");
			case AFTER:
				return Pair.of(ItemType.AFTER_TEST.name(), "After hook");
			case AFTER_STEP:
				return Pair.of(ItemType.AFTER_METHOD.name(), "After step");
			default:
				return Pair.of(ItemType.TEST.name(), "Hook");
		}
	}

	/**
	 * Extension point to customize test creation event/request
	 *
	 * @param testCase Cucumber's TestCase object
	 * @param testStep a cucumber step object
	 * @return Request to ReportPortal
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected StartTestItemRQ buildStartHookRequest(@Nonnull TestCase testCase, @Nonnull HookTestStep testStep) {
		StartTestItemRQ rq = new StartTestItemRQ();
		Pair<String, String> typeName = getHookTypeAndName(testStep.getHookType());
		rq.setType(typeName.getKey());
		rq.setName(String.format("%s: %s", typeName.getValue(), testStep.getCodeLocation()));
		rq.setStartTime(Instant.now());
		return rq;
	}

	/**
	 * Start before/after-hook item on Report Portal
	 *
	 * @param parentId parent item id
	 * @param rq       hook start request
	 * @return hook item id
	 */
	@Nonnull
	protected Maybe<String> startHook(@Nonnull Maybe<String> parentId, @Nonnull StartTestItemRQ rq) {
		return getLaunch().startTestItem(parentId, rq);
	}

	/**
	 * Called when before/after-hooks are started
	 *
	 * @param testCase a Cucumber's TestCase object
	 * @param testStep Cucumber's TestStep object
	 */
	protected void beforeHooks(@Nonnull TestCase testCase, @Nonnull HookTestStep testStep) {
		execute(
				testCase, (f, s) -> {
					StartTestItemRQ rq = buildStartHookRequest(testCase, testStep);
					s.setHookId(startHook(s.getId(), rq));
				}
		);
	}

	/**
	 * Called when before/after-hooks are finished
	 *
	 * @param testCase a Cucumber's TestCase object
	 * @param step     a cucumber step object
	 * @param result   a cucumber result object
	 */
	@SuppressWarnings("unused")
	protected void afterHooks(@Nonnull TestCase testCase, @Nonnull HookTestStep step, Result result) {
		execute(
				testCase, (f, s) -> {
					reportResult(result);
					finishTestItem(s.getHookId(), mapItemStatus(result.getStatus()));
					s.setHookId(Maybe.empty());
				}
		);
	}

	/**
	 * Return RP launch test item name mapped to Cucumber feature
	 *
	 * @return test item name
	 */
	@Nonnull
	protected abstract String getFeatureTestItemType();

	/**
	 * Return RP launch test item name mapped to Cucumber scenario
	 *
	 * @return test item name
	 */
	@Nonnull
	protected abstract String getScenarioTestItemType();

	/**
	 * Report test item result and error (if present)
	 *
	 * @param result - Cucumber result object
	 */
	protected void reportResult(@Nonnull Result result) {
		ofNullable(result.getError()).ifPresent(ReportPortal::sendStackTraceToRP);
	}

	@Nullable
	private static String getDataType(@Nonnull byte[] data, @Nullable String name) {
		try {
			return MimeTypeDetector.detect(ByteSource.wrap(data), name);
		} catch (IOException e) {
			LOGGER.warn("Unable to detect MIME type", e);
		}
		return null;
	}

	/**
	 * Send a log with data attached.
	 *
	 * @param name     attachment name
	 * @param mimeType attachment type
	 * @param data     data to attach
	 */
	protected void embedding(@Nullable String name, @Nullable String mimeType, @Nonnull byte[] data) {
		String type = ofNullable(mimeType).filter(ContentType::isValidType).orElseGet(() -> getDataType(data, name));
		String attachmentName = ofNullable(name).filter(m -> !m.isEmpty())
				.orElseGet(() -> ofNullable(type).map(t -> t.substring(0, t.indexOf("/"))).orElse(""));
		ReportPortal.emitLog(new ReportPortalMessage(ByteSource.wrap(data), type, attachmentName), LogLevel.INFO.name(), Instant.now());
	}

	/**
	 * Send a text log entry to Report Portal with 'INFO' level, using current datetime as timestamp
	 *
	 * @param message a text message
	 */
	protected void sendLog(@Nullable String message) {
		ReportPortal.emitLog(message, LogLevel.INFO.name(), Instant.now());
	}

	@Nonnull
	protected abstract Optional<Maybe<String>> getRootItemId();

	/**
	 * Extension point to customize scenario creation event/request
	 *
	 * @param rule    the rule node
	 * @param codeRef the rule code reference
	 * @return start test item request ready to send on RP
	 */
	@Nonnull
	protected StartTestItemRQ buildStartRuleRequest(@Nonnull Node.Rule rule, @Nullable String codeRef) {
		String ruleKeyword = rule.getKeyword().orElse("");
		String ruleName = rule.getName().orElse(NO_NAME);
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(buildName(ruleKeyword, AbstractReporter.COLON_INFIX, ruleName));
		rq.setCodeRef(codeRef);
		rq.setStartTime(Instant.now());
		rq.setType("SUITE");
		return rq;
	}

	/**
	 * Start Rule item on Report Portal
	 *
	 * @param featureId parent item id
	 * @param ruleRq    Rule start request
	 * @return hook item id
	 */
	@Nonnull
	protected Maybe<String> startRule(@Nonnull Maybe<String> featureId, @Nonnull StartTestItemRQ ruleRq) {
		return getLaunch().startTestItem(featureId, ruleRq);
	}

	/**
	 * Start Cucumber scenario
	 *
	 * @param feature  current feature object
	 * @param scenario current scenario object
	 */
	protected void beforeScenario(@Nonnull Feature feature, @Nonnull TestCase scenario) {
		String scenarioName = Utils.buildName(scenario.getKeyword(), AbstractReporter.COLON_INFIX, scenario.getName());
		execute(
				scenario, (f, s) -> {
					Optional<RuleContext> rule = s.getRule();
					Optional<RuleContext> currentRule = f.getCurrentRule();
					if (!currentRule.equals(rule)) {
						if (currentRule.isEmpty()) {
							rule.ifPresent(r -> {
								r.setId(startRule(
										f.getId(),
										buildStartRuleRequest(r.getRule(), getCodeRef(feature.getUri(), r.getLine()))
								));
								f.setCurrentRule(r);
							});
						} else {
							finishTestItem(currentRule.get().getId());
							rule.ifPresent(r -> {
								r.setId(startRule(
										f.getId(),
										buildStartRuleRequest(r.getRule(), getCodeRef(feature.getUri(), r.getLine()))
								));
								f.setCurrentRule(r);
							});
						}
					}
					Maybe<String> rootId = rule.map(RuleContext::getId).orElseGet(f::getId);

					// If it's a ScenarioOutline use Example's line number as code reference to detach one Test Item from another
					int codeLine = s.getExample().map(e -> e.getLocation().getLine()).orElse(s.getLine());
					StartTestItemRQ startTestItemRQ = buildStartScenarioRequest(scenario, scenarioName, s.getUri(), codeLine);
					s.setId(startScenario(rootId, startTestItemRQ));
					descriptionsMap.put(s.getId(), ofNullable(startTestItemRQ.getDescription()).orElse(StringUtils.EMPTY));
					if (getLaunch().getParameters().isCallbackReportingEnabled()) {
						addToTree(feature, scenario, s.getId());
					}
				}
		);
	}

	/**
	 * Extension point to customize feature creation event/request
	 *
	 * @param feature a Cucumber's Feature object
	 * @param uri     a path to the feature
	 * @return Request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartFeatureRequest(@Nonnull Feature feature, @Nonnull URI uri) {
		String featureKeyword = feature.getKeyword().orElse("");
		String featureName = feature.getName().orElse(NO_NAME);
		StartTestItemRQ startFeatureRq = new StartTestItemRQ();
		startFeatureRq.setDescription(getDescription(feature, uri));
		startFeatureRq.setCodeRef(getCodeRef(uri, 0));
		startFeatureRq.setName(buildName(featureKeyword, AbstractReporter.COLON_INFIX, featureName));
		execute(feature.getUri(), f -> startFeatureRq.setAttributes(extractAttributes(f.getTags())));
		startFeatureRq.setStartTime(Instant.now());
		startFeatureRq.setType(getFeatureTestItemType());
		return startFeatureRq;
	}

	/**
	 * Start Cucumber Feature
	 *
	 * @param startFeatureRq feature start request
	 * @return feature item id
	 */
	@Nonnull
	protected Maybe<String> startFeature(@Nonnull StartTestItemRQ startFeatureRq) {
		Optional<Maybe<String>> root = getRootItemId();
		return root.map(r -> getLaunch().startTestItem(r, startFeatureRq)).orElseGet(() -> getLaunch().startTestItem(startFeatureRq));
	}

	private void addToTree(Feature feature, Maybe<String> featureId) {
		getItemTree().getTestItems().put(createKey(feature.getUri()), TestItemTree.createTestItemLeaf(featureId));
	}

	/**
	 * Starts a Cucumber Test Case start, also starts corresponding Feature if is not started already.
	 *
	 * @param event Cucumber's Test Case started event object
	 */
	protected void handleStartOfTestCase(@Nonnull TestCaseStarted event) {
		TestCase testCase = event.getTestCase();
		URI uri = testCase.getUri();
		execute(
				uri, f -> {
					//noinspection ReactiveStreamsUnusedPublisher
					if (f.getId().equals(Maybe.empty())) {
						getRootItemId(); // trigger root item creation
						StartTestItemRQ featureRq = buildStartFeatureRequest(f.getFeature(), uri);
						f.setId(startFeature(featureRq));
						if (getLaunch().getParameters().isCallbackReportingEnabled()) {
							addToTree(f.getFeature(), f.getId());
						}
					}
				}
		);
		execute(
				testCase, (f, s) -> {
					s.setTestCase(testCase);
					beforeScenario(f.getFeature(), testCase);
				}
		);
	}

	protected void handleSourceEvents(TestSourceParsed parseEvent) {
		URI uri = parseEvent.getUri();
		parseEvent.getNodes().forEach(n -> {
			if (n instanceof Feature) {
				featureContextMap.put(uri, new FeatureContext(uri, (Feature) n));
			} else {
				LOGGER.warn("Unknown node type: {}", n.getClass().getSimpleName());
			}
		});
	}

	protected EventHandler<TestRunStarted> getTestRunStartedHandler() {
		return event -> beforeLaunch();
	}

	protected EventHandler<TestSourceParsed> getTestSourceParsedHandler() {
		return this::handleSourceEvents;
	}

	protected EventHandler<TestCaseStarted> getTestCaseStartedHandler() {
		return this::handleStartOfTestCase;
	}

	protected EventHandler<TestStepStarted> getTestStepStartedHandler() {
		return this::handleTestStepStarted;
	}

	protected EventHandler<TestStepFinished> getTestStepFinishedHandler() {
		return this::handleTestStepFinished;
	}

	protected EventHandler<TestCaseFinished> getTestCaseFinishedHandler() {
		return this::afterScenario;
	}

	protected EventHandler<TestRunFinished> getTestRunFinishedHandler() {
		return event -> {
			handleEndOfFeature();
			afterLaunch();
		};
	}

	protected EventHandler<EmbedEvent> getEmbedEventHandler() {
		return event -> embedding(event.getName(), event.getMediaType(), event.getData());
	}

	protected EventHandler<WriteEvent> getWriteEventHandler() {
		return event -> sendLog(event.getText());
	}

	/**
	 * Registers an event handler for a specific event.
	 * <p>
	 * The available events types are:
	 * <ul>
	 * <li>{@link TestRunStarted} - the first event sent.
	 * <li>{@link TestSourceRead} - sent for each feature file read, contains the feature file source.
	 * <li>{@link TestCaseStarted} - sent before starting the execution of a Test Case(/Pickle/Scenario), contains the Test Case
	 * <li>{@link TestStepStarted} - sent before starting the execution of a Test Step, contains the Test Step
	 * <li>{@link TestStepFinished} - sent after the execution of a Test Step, contains the Test Step and its Result.
	 * <li>{@link TestCaseFinished} - sent after the execution of a Test Case(/Pickle/Scenario), contains the Test Case and its Result.
	 * <li>{@link TestRunFinished} - the last event sent.
	 * <li>{@link EmbedEvent} - calling scenario.embed in a hook triggers this event.
	 * <li>{@link WriteEvent} - calling scenario.write in a hook triggers this event.
	 * </ul>
	 */
	@Override
	public void setEventPublisher(EventPublisher publisher) {
		publisher.registerHandlerFor(TestRunStarted.class, getTestRunStartedHandler());
		publisher.registerHandlerFor(TestSourceParsed.class, getTestSourceParsedHandler());
		publisher.registerHandlerFor(TestCaseStarted.class, getTestCaseStartedHandler());
		publisher.registerHandlerFor(TestStepStarted.class, getTestStepStartedHandler());
		publisher.registerHandlerFor(TestStepFinished.class, getTestStepFinishedHandler());
		publisher.registerHandlerFor(TestCaseFinished.class, getTestCaseFinishedHandler());
		publisher.registerHandlerFor(TestRunFinished.class, getTestRunFinishedHandler());
		publisher.registerHandlerFor(EmbedEvent.class, getEmbedEventHandler());
		publisher.registerHandlerFor(WriteEvent.class, getWriteEventHandler());
	}

	private void removeFromTree(Feature feature) {
		itemTree.getTestItems().remove(createKey(feature.getUri()));
	}

	protected void handleEndOfFeature() {
		featureContextMap.values().forEach(f -> {
			Instant featureCompletionDateTime = featureEndTime.get(f.getUri());
			f.getCurrentRule().ifPresent(r -> finishTestItem(r.getId(), null, featureCompletionDateTime));
			finishTestItem(f.getId(), null, featureCompletionDateTime);
			removeFromTree(f.getFeature());
		});
		featureContextMap.clear();
	}

	protected void handleTestStepStarted(@Nonnull TestStepStarted event) {
		TestStep testStep = event.getTestStep();
		TestCase testCase = event.getTestCase();
		if (testStep instanceof HookTestStep) {
			beforeHooks(testCase, (HookTestStep) testStep);
		} else if (testStep instanceof PickleStepTestStep) {
			beforeStep(testCase, (PickleStepTestStep) testStep);
		} else {
			LOGGER.warn("Unable to start unknown step type: {}", testStep.getClass().getSimpleName());
		}
	}

	protected void handleTestStepFinished(@Nonnull TestStepFinished event) {
		TestStep testStep = event.getTestStep();
		TestCase testCase = event.getTestCase();
		if (testStep instanceof HookTestStep) {
			afterHooks(testCase, (HookTestStep) testStep, event.getResult());
		} else if (testStep instanceof PickleStepTestStep) {
			afterStep(testCase, (PickleStepTestStep) testStep, event.getResult());
		} else {
			LOGGER.warn("Unable to finish unknown step type: {}", testStep.getClass().getSimpleName());
		}
	}

	/**
	 * Build finish test item request object
	 *
	 * @param itemId     item ID reference
	 * @param finishTime a datetime object to use as item end time
	 * @param status     item result status
	 * @return finish request
	 */
	@Nonnull
	protected FinishTestItemRQ buildFinishTestItemRequest(@Nonnull Maybe<String> itemId, @Nullable Instant finishTime,
			@Nullable ItemStatus status) {
		FinishTestItemRQ rq = new FinishTestItemRQ();
		if (status == ItemStatus.FAILED) {
			Optional<String> currentDescription = Optional.ofNullable(descriptionsMap.remove(itemId));
			Optional<Throwable> currentError = Optional.ofNullable(errorMap.remove(itemId));
			currentDescription.flatMap(description -> currentError.map(errorMessage -> resolveDescriptionErrorMessage(
					description,
					errorMessage
			))).ifPresent(rq::setDescription);
		}
		ofNullable(status).ifPresent(s -> rq.setStatus(s.name()));
		rq.setEndTime(finishTime);
		return rq;
	}

	/**
	 * Resolve description
	 *
	 * @param currentDescription Current description
	 * @param error              Error message
	 * @return Description with error
	 */
	private String resolveDescriptionErrorMessage(String currentDescription, Throwable error) {
		String errorStr = getReportPortal().getParameters().isExceptionTruncate() ?
				format(ERROR_FORMAT, getStackTrace(error, new Throwable())) :
				format(ERROR_FORMAT, ExceptionUtils.getStackTrace(error));
		return Optional.ofNullable(currentDescription)
				.filter(StringUtils::isNotBlank)
				.map(description -> MarkdownUtils.asTwoParts(currentDescription, errorStr))
				.orElse(errorStr);
	}

	/**
	 * Finish a test item with specified status
	 *
	 * @param itemId   an ID of the item
	 * @param status   the status of the item
	 * @param dateTime a date and time object to use as feature end time
	 * @return a date and time object of the finish event
	 */
	protected Instant finishTestItem(@Nullable Maybe<String> itemId, @Nullable ItemStatus status, @Nullable Instant dateTime) {
		if (itemId == null) {
			LOGGER.error("BUG: Trying to finish unspecified test item.");
			return null;
		}
		Instant endTime = ofNullable(dateTime).orElse(Instant.now());
		FinishTestItemRQ rq = buildFinishTestItemRequest(itemId, endTime, status);
		//noinspection ReactiveStreamsUnusedPublisher
		getLaunch().finishTestItem(itemId, rq);
		return endTime;
	}

	/**
	 * Map Cucumber statuses to RP item statuses
	 *
	 * @param status - Cucumber status
	 * @return RP test item status and null if status is null
	 */
	@Nullable
	protected ItemStatus mapItemStatus(@Nullable Status status) {
		if (status == null) {
			return null;
		} else {
			if (STATUS_MAPPING.get(status) == null) {
				LOGGER.error("Unable to find direct mapping between Cucumber and ReportPortal for TestItem with status: '{}'.", status);
				return ItemStatus.SKIPPED;
			}
			return STATUS_MAPPING.get(status);
		}
	}

	/**
	 * Finish a test item with specified status
	 *
	 * @param itemId an ID of the item
	 * @param status the status of the item
	 */
	protected void finishTestItem(@Nullable Maybe<String> itemId, @Nullable ItemStatus status) {
		finishTestItem(itemId, status, null);
	}

	/**
	 * Finish a test item with no specific status
	 *
	 * @param itemId an ID of the item
	 */
	protected void finishTestItem(@Nullable Maybe<String> itemId) {
		finishTestItem(itemId, null);
	}

	/**
	 * Converts a table represented as List of Lists to a formatted table string
	 *
	 * @param table a table object
	 * @return string representation of the table
	 */
	@Nonnull
	protected String formatDataTable(@Nonnull final List<List<String>> table) {
		return MarkdownUtils.formatDataTable(table);
	}

	/**
	 * Generate multiline argument (DataTable or DocString) representation
	 *
	 * @param step - Cucumber step object
	 * @return - transformed multiline argument (or empty string if there is
	 * none)
	 */
	@Nonnull
	protected String buildMultilineArgument(@Nonnull TestStep step) {
		List<List<String>> table = null;
		String docString = null;
		PickleStepTestStep pickleStep = (PickleStepTestStep) step;
		if (pickleStep.getStep().getArgument() != null) {
			StepArgument argument = pickleStep.getStep().getArgument();
			if (argument instanceof DocStringArgument) {
				docString = ((DocStringArgument) argument).getContent();
			} else if (argument instanceof DataTableArgument) {
				table = ((DataTableArgument) argument).cells();
			}
		}

		StringBuilder marg = new StringBuilder();
		if (table != null) {
			marg.append(formatDataTable(table));
		}

		if (docString != null) {
			marg.append(DOCSTRING_DECORATOR).append(docString).append(DOCSTRING_DECORATOR);
		}
		return marg.toString();
	}

	/**
	 * Returns code reference for mapped code
	 *
	 * @param testStep Cucumber's TestStep object
	 * @return a code reference, or null if not possible to determine (ambiguous, undefined, etc.)
	 */
	@Nullable
	protected String getCodeRef(@Nonnull TestStep testStep) {
		String cucumberLocation = testStep.getCodeLocation();
		try {
			Object stepDefinitionMatch = Accessible.on(testStep).field(DEFINITION_MATCH_FIELD_NAME).getValue();
			if (stepDefinitionMatch != null) {
				Object javaStepDefinition = Accessible.on(stepDefinitionMatch).field(STEP_DEFINITION_FIELD_NAME).getValue();
				if (javaStepDefinition != null) {
					Object codeLocationObject = Accessible.on(javaStepDefinition).method(GET_LOCATION_METHOD_NAME).invoke();
					if (codeLocationObject != null) {
						String codeLocation = codeLocationObject.toString();
						if (isNotBlank(codeLocation)) {
							int openingBracketIndex = codeLocation.indexOf(METHOD_OPENING_BRACKET);
							if (openingBracketIndex > 0) {
								return codeLocation.substring(0, codeLocation.indexOf(METHOD_OPENING_BRACKET));
							} else {
								return codeLocation;
							}
						}
					}
				}
			}
		} catch (Throwable e) {
			LOGGER.error("Unable to get java code reference for the Test Step: {}", cucumberLocation, e);
			return cucumberLocation;
		}
		return cucumberLocation;
	}

	/**
	 * Returns code reference for feature files by URI and text line number
	 *
	 * @param uri  a feature URI
	 * @param line a scenario line number
	 * @return a code reference
	 */
	@Nonnull
	protected String getCodeRef(@Nonnull URI uri, int line) {
		return WORKING_DIRECTORY.relativize(uri) + ":" + line;
	}

	@Nonnull
	private static Pair<String, String> parseJavaCodeRef(@Nonnull String codeRef) {
		int lastDelimiterIndex = codeRef.lastIndexOf('.');
		String className = codeRef.substring(0, lastDelimiterIndex);
		String methodName = codeRef.substring(lastDelimiterIndex + 1);
		return Pair.of(className, methodName);
	}

	@Nonnull
	private static Optional<Class<?>> getStepClass(String classCodeRef, String fullCodeRef) {
		try {
			return Optional.of(Class.forName(classCodeRef));
		} catch (ClassNotFoundException e1) {
			try {
				return Optional.of(Class.forName(fullCodeRef));
			} catch (ClassNotFoundException e2) {
				return Optional.empty();
			}
		}
	}

	@Nonnull
	private static Optional<Method> getStepMethod(@Nonnull Class<?> stepClass, @Nullable String methodName) {
		return Arrays.stream(stepClass.getMethods()).filter(m -> m.getName().equals(methodName)).findAny();
	}

	/**
	 * Returns static attributes defined by {@link Attributes} annotation in code.
	 *
	 * @param codeRef - a method reference to read parameters
	 * @return a set of attributes or null if no such method provided by the match object
	 */
	@Nullable
	protected Set<ItemAttributesRQ> getAttributes(@Nonnull String codeRef) {
		Pair<String, String> splitCodeRef = parseJavaCodeRef(codeRef);
		Optional<Class<?>> testStepClass = getStepClass(splitCodeRef.getKey(), codeRef);
		return testStepClass.flatMap(c -> getStepMethod(c, splitCodeRef.getValue()))
				.map(m -> m.getAnnotation(Attributes.class))
				.map(AttributeParser::retrieveAttributes)
				.orElse(null);
	}

	/**
	 * Returns a list of parameters for a step
	 *
	 * @param codeRef  a method code reference to retrieve parameter types
	 * @param testStep Cucumber's Step object
	 * @return a list of parameters or empty list if none
	 */
	@Nonnull
	protected List<ParameterResource> getParameters(@Nullable String codeRef, @Nonnull TestStep testStep) {
		if (!(testStep instanceof PickleStepTestStep)) {
			return Collections.emptyList();
		}

		PickleStepTestStep pickleStepTestStep = (PickleStepTestStep) testStep;
		List<Argument> arguments = pickleStepTestStep.getDefinitionArgument();
		List<Pair<String, String>> params = ofNullable(arguments).map(a -> a.stream()
				.map(arg -> Pair.of(arg.getParameterTypeName(), arg.getValue()))
				.collect(Collectors.toList())).orElse(new ArrayList<>());
		ofNullable(pickleStepTestStep.getStep().getArgument()).ifPresent(a -> {
			String value;
			if (a instanceof DocStringArgument) {
				value = ((DocStringArgument) a).getContent();
			} else if (a instanceof DataTableArgument) {
				value = formatDataTable(((DataTableArgument) a).cells());
			} else {
				value = a.toString();
			}
			params.add(Pair.of("arg", value));
		});
		return ParameterUtils.getParameters(codeRef, params);
	}

	/**
	 * Build an item description for a feature
	 *
	 * @param feature a Cucumber's Feature object
	 * @param uri     a feature URI
	 * @return item description
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected String getDescription(Feature feature, @Nonnull URI uri) {
		return uri.toString();
	}

	/**
	 * Build an item description for a scenario
	 *
	 * @param testCase a Cucumber's TestCase object
	 * @param uri      a feature URI
	 * @return item description
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected String getDescription(@Nonnull TestCase testCase, @Nonnull URI uri) {
		return uri.toString();
	}
}
