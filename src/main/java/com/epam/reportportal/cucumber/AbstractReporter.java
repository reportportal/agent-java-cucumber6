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

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.message.ReportPortalMessage;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.utils.properties.SystemAttributesExtractor;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.*;
import io.reactivex.Maybe;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.base.Supplier;
import rp.com.google.common.base.Suppliers;
import rp.com.google.common.io.ByteSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.epam.reportportal.cucumber.Utils.getCodeRef;
import static com.epam.reportportal.cucumber.Utils.getDescription;
import static rp.com.google.common.base.Strings.isNullOrEmpty;
import static rp.com.google.common.base.Throwables.getStackTraceAsString;

/**
 * Abstract Cucumber 5.x formatter for Report Portal
 *
 * @author Sergey Gvozdyukevich
 * @author Andrei Varabyeu
 * @author Serhii Zharskyi
 * @author Vitaliy Tsvihun
 * @author Vadzim Hushchanskou
 */
public abstract class AbstractReporter implements ConcurrentEventListener {
	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractReporter.class);

	private static final String AGENT_PROPERTIES_FILE = "agent.properties";

	protected Supplier<Launch> launch;
	static final String COLON_INFIX = ": ";
	private static final String SKIPPED_ISSUE_KEY = "skippedIssue";
	private final Map<URI, RunningContext.FeatureContext> currentFeatureContextMap = new ConcurrentHashMap<>();

	private final Map<Pair<String, URI>, RunningContext.ScenarioContext> currentScenarioContextMap = new ConcurrentHashMap<>();

	// There is no event for recognizing end of feature in Cucumber.
	// This map is used to record the last scenario time and its feature uri.
	// End of feature occurs once launch is finished.
	private final Map<URI, Date> featureEndTime = new ConcurrentHashMap<>();

	private final ThreadLocal<RunningContext.ScenarioContext> currentScenarioContext = new ThreadLocal<>();

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
		publisher.registerHandlerFor(TestSourceRead.class, getTestSourceReadHandler());
		publisher.registerHandlerFor(TestCaseStarted.class, getTestCaseStartedHandler());
		publisher.registerHandlerFor(TestStepStarted.class, getTestStepStartedHandler());
		publisher.registerHandlerFor(TestStepFinished.class, getTestStepFinishedHandler());
		publisher.registerHandlerFor(TestCaseFinished.class, getTestCaseFinishedHandler());
		publisher.registerHandlerFor(TestRunFinished.class, getTestRunFinishedHandler());
		publisher.registerHandlerFor(EmbedEvent.class, getEmbedEventHandler());
		publisher.registerHandlerFor(WriteEvent.class, getWriteEventHandler());
	}

	protected RunningContext.ScenarioContext getCurrentScenarioContext() {
		return currentScenarioContext.get();
	}

	/**
	 * Manipulations before the launch starts
	 */
	protected void beforeLaunch() {
		startLaunch();
		launch.get().start();
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
		finishLaunchRq.setEndTime(Calendar.getInstance().getTime());
		launch.get().finish(finishLaunchRq);
	}

	/**
	 * Start Cucumber scenario
	 */
	protected void beforeScenario(RunningContext.FeatureContext currentFeatureContext,
			RunningContext.ScenarioContext currentScenarioContext, String scenarioName) {
		String description = getDescription(currentFeatureContext.getUri());
		String codeRef = getCodeRef(currentFeatureContext.getUri(), currentScenarioContext.getLine());
		Maybe<String> id = Utils.startNonLeafNode(launch.get(),
				currentFeatureContext.getFeatureId(),
				scenarioName,
				description,
				codeRef,
				currentScenarioContext.getAttributes(),
				getScenarioTestItemType()
		);
		currentScenarioContext.setId(id);
	}

	/**
	 * Finish Cucumber scenario
	 * Put scenario end time in a map to check last scenario end time per feature
	 */
	protected void afterScenario(TestCaseFinished event) {
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		for (Map.Entry<Pair<String, URI>, RunningContext.ScenarioContext> scenarioContext : currentScenarioContextMap.entrySet()) {
			if (scenarioContext.getValue().getLine() == context.getLine()) {
				currentScenarioContextMap.remove(scenarioContext.getKey());
				Date endTime = Utils.finishTestItem(launch.get(), context.getId(), event.getResult().getStatus());
				URI featureURI = scenarioContext.getKey().getValue();
				featureEndTime.put(featureURI, endTime);
				break;
			}
		}
		currentScenarioContext.set(null);
	}

	/**
	 * Start RP launch
	 */
	protected void startLaunch() {
		launch = Suppliers.memoize(new Supplier<Launch>() {

			/* should no be lazy */
			private final Date startTime = Calendar.getInstance().getTime();

			@Override
			public Launch get() {
				final ReportPortal reportPortal = buildReportPortal();
				ListenerParameters parameters = reportPortal.getParameters();

				StartLaunchRQ rq = new StartLaunchRQ();
				rq.setName(parameters.getLaunchName());
				rq.setStartTime(startTime);
				rq.setMode(parameters.getLaunchRunningMode());
				rq.setAttributes(parameters.getAttributes());
				rq.getAttributes()
						.addAll(SystemAttributesExtractor.extract(AGENT_PROPERTIES_FILE, AbstractReporter.class.getClassLoader()));
				rq.setDescription(parameters.getDescription());
				rq.setRerun(parameters.isRerun());
				if (!isNullOrEmpty(parameters.getRerunOf())) {
					rq.setRerunOf(parameters.getRerunOf());
				}

				if (null != parameters.getSkippedAnIssue()) {
					ItemAttributesRQ skippedIssueAttribute = new ItemAttributesRQ();
					skippedIssueAttribute.setKey(SKIPPED_ISSUE_KEY);
					skippedIssueAttribute.setValue(parameters.getSkippedAnIssue().toString());
					skippedIssueAttribute.setSystem(true);
					rq.getAttributes().add(skippedIssueAttribute);
				}

				return reportPortal.newLaunch(rq);
			}
		});
	}

	/**
	 * Start Cucumber step
	 *
	 * @param step Step object
	 */
	protected abstract void beforeStep(TestStep step);

	/**
	 * Finish Cucumber step
	 *
	 * @param result Step result
	 */
	protected abstract void afterStep(Result result);

	/**
	 * Called when before/after-hooks are started
	 *
	 * @param hookType a hook type
	 */
	protected abstract void beforeHooks(HookType hookType);

	/**
	 * Called when before/after-hooks are finished
	 *
	 * @param isBefore - if true, before-hook is finished, if false - after-hook
	 */
	protected abstract void afterHooks(Boolean isBefore);

	/**
	 * Called when a specific before/after-hook is finished
	 *
	 * @param step     TestStep object
	 * @param result   Hook result
	 * @param isBefore - if true, before-hook, if false - after-hook
	 */
	protected abstract void hookFinished(HookTestStep step, Result result, Boolean isBefore);

	/**
	 * Return RP launch test item name mapped to Cucumber feature
	 *
	 * @return test item name
	 */
	protected abstract String getFeatureTestItemType();

	/**
	 * Return RP launch test item name mapped to Cucumber scenario
	 *
	 * @return test item name
	 */
	protected abstract String getScenarioTestItemType();

	/**
	 * Report test item result and error (if present)
	 *
	 * @param result  - Cucumber result object
	 * @param message - optional message to be logged in addition
	 */
	void reportResult(Result result, String message) {
		String cukesStatus = result.getStatus().toString();
		String level = Utils.mapLevel(cukesStatus);
		if (message != null) {
			Utils.sendLog(message, level);
		}
		if (result.getError() != null) {
			Utils.sendLog(getStackTraceAsString(result.getError()), level);
		}
	}

	private static final ThreadLocal<Tika> TIKA_THREAD_LOCAL = ThreadLocal.withInitial(Tika::new);

	private volatile MimeTypes mimeTypes = null;

	private MimeTypes getMimeTypes() {
		if (mimeTypes == null) {
			mimeTypes = MimeTypes.getDefaultMimeTypes();
		}
		return mimeTypes;
	}

	/**
	 * Send a log with data attached.
	 *
	 * @param mimeType an attachment type
	 * @param data     data to attach
	 */
	protected void embedding(String mimeType, byte[] data) {
		String type = mimeType;
		try {
			type = TIKA_THREAD_LOCAL.get().detect(new ByteArrayInputStream(data));
		} catch (IOException e) {
			// nothing to do we will use bypassed mime type
			LOGGER.warn("Mime-type not found", e);
		}
		String prefix = "";
		try {

			MediaType mt = getMimeTypes().forName(type).getType();
			prefix = mt.getType();
		} catch (MimeTypeException e) {
			LOGGER.warn("Mime-type not found", e);
		}
		ReportPortal.emitLog(new ReportPortalMessage(ByteSource.wrap(data), type, prefix), "UNKNOWN", Calendar.getInstance().getTime());
	}

	protected void write(String text) {
		Utils.sendLog(text, "INFO");
	}

	private boolean isBefore(TestStep step) {
		return HookType.BEFORE == ((HookTestStep) step).getHookType();
	}

	protected abstract Maybe<String> getRootItemId();

	private RunningContext.FeatureContext startFeatureContext(RunningContext.FeatureContext context) {
		String featureKeyword = context.getFeature().getKeyword();
		String featureName = context.getFeature().getName();
		StartTestItemRQ rq = new StartTestItemRQ();
		Maybe<String> root = getRootItemId();
		rq.setDescription(getDescription(context.getUri()));
		rq.setCodeRef(getCodeRef(context.getUri(), 0));
		rq.setName(Utils.buildNodeName(featureKeyword, AbstractReporter.COLON_INFIX, featureName, null));
		rq.setAttributes(context.getAttributes());
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(getFeatureTestItemType());
		context.setFeatureId(root == null ? launch.get().startTestItem(rq) : launch.get().startTestItem(root, rq));
		return context;
	}

	/**
	 * Private part that responsible for handling events
	 */

	protected EventHandler<TestRunStarted> getTestRunStartedHandler() {
		return event -> beforeLaunch();
	}

	protected EventHandler<TestSourceRead> getTestSourceReadHandler() {
		return event -> RunningContext.FeatureContext.addTestSourceReadEvent(event.getUri(), event);
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
		return event -> embedding(event.getMediaType(), event.getData());
	}

	protected EventHandler<WriteEvent> getWriteEventHandler() {
		return event -> write(event.getText());
	}

	protected void handleEndOfFeature() {
		for (RunningContext.FeatureContext value : currentFeatureContextMap.values()) {
			Date featureCompletionDateTime = featureEndTime.get(value.getUri());
			Utils.finishFeature(launch.get(), value.getFeatureId(), featureCompletionDateTime);
		}
		currentFeatureContextMap.clear();
	}

	protected void handleStartOfTestCase(TestCaseStarted event) {
		TestCase testCase = event.getTestCase();
		RunningContext.FeatureContext newFeatureContext = new RunningContext.FeatureContext(testCase);
		URI featureUri = newFeatureContext.getUri();
		RunningContext.FeatureContext featureContext = currentFeatureContextMap.computeIfAbsent(
				featureUri,
				u -> startFeatureContext(newFeatureContext)
		);

		if (!featureContext.getUri().equals(testCase.getUri())) {
			throw new IllegalStateException("Scenario URI does not match Feature URI.");
		}

		RunningContext.ScenarioContext newScenarioContext = featureContext.getScenarioContext(testCase);
		String scenarioName = Utils.buildNodeName(newScenarioContext.getKeyword(),
				AbstractReporter.COLON_INFIX,
				newScenarioContext.getName(),
				newScenarioContext.getOutlineIteration()
		);

		Pair<String, URI> scenarioNameFeatureURI = Pair.of(testCase.getScenarioDesignation(), featureContext.getUri());
		RunningContext.ScenarioContext scenarioContext = currentScenarioContextMap.get(scenarioNameFeatureURI);

		if (scenarioContext == null) {
			scenarioContext = newScenarioContext;
			currentScenarioContextMap.put(scenarioNameFeatureURI, scenarioContext);
			currentScenarioContext.set(scenarioContext);
		}

		beforeScenario(featureContext, scenarioContext, scenarioName);
	}

	protected void handleTestStepStarted(TestStepStarted event) {
		TestStep testStep = event.getTestStep();
		if (testStep instanceof HookTestStep) {
			beforeHooks(((HookTestStep) testStep).getHookType());
		} else {
			if (getCurrentScenarioContext().withBackground()) {
				getCurrentScenarioContext().nextBackgroundStep();
			}
			beforeStep(testStep);
		}
	}

	protected void handleTestStepFinished(TestStepFinished event) {
		if (event.getTestStep() instanceof HookTestStep) {
			hookFinished((HookTestStep) event.getTestStep(), event.getResult(), isBefore(event.getTestStep()));
			afterHooks(isBefore(event.getTestStep()));
		} else {
			afterStep(event.getResult());
		}
	}
}
