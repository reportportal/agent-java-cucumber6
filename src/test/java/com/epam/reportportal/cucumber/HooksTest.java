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

import com.epam.reportportal.cucumber.integration.TestScenarioReporter;
import com.epam.reportportal.cucumber.integration.TestStepReporter;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import okhttp3.MultipartBody;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.cucumber.integration.util.TestUtils.filterLogs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.any;

/**
 * TODO: finish unfinished tests
 */
public class HooksTest {

	@CucumberOptions(features = "src/test/resources/features/DummyScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.hooks.step" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class StepHooksReporterTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/DummyScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.hooks.scenario" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class ScenarioHooksReporterTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/DummyScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.hooks.all" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class AllHooksReporterTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/DummyScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class NoHooksReporterTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/DummyScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.hooks.skip" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class SkipStepHooksReporterTest extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final String featureId = CommonUtils.namedId("feature_");
	private final List<String> scenarioIds = Stream.generate(() -> CommonUtils.namedId("scenario_")).limit(3).collect(Collectors.toList());
	private final List<Pair<String, String>> steps = scenarioIds.stream()
			.flatMap(s -> Stream.generate(() -> CommonUtils.namedId("step_")).limit(6).map(ns -> Pair.of(s, ns)))
			.collect(Collectors.toList());

	private final List<String> stepIds = steps.stream().map(Pair::getValue).collect(Collectors.toList());

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, featureId, scenarioIds);
		TestUtils.mockNestedSteps(client, steps);
		TestUtils.mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	@AfterEach
	public void tearDown() {
		CommonUtils.shutdownExecutorService(executorService);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_before_after_step_reported_in_steps() {
		TestUtils.runTests(StepHooksReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		verify(client, times(6)).startTestItem(same(featureId), any());
		verify(client, times(6)).log(any(List.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_before_after_scenario_reported_in_steps() {
		TestUtils.runTests(ScenarioHooksReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		verify(client, times(4)).startTestItem(same(featureId), any());
		verify(client, times(4)).log(any(List.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_before_after_all_reported_in_steps() {
		TestUtils.runTests(AllHooksReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());

		// @BeforeAll and @AfterAll hooks does not emit any events, see: https://github.com/cucumber/cucumber-jvm/issues/2422
		verify(client, times(2)).startTestItem(same(featureId), any());
		verify(client, times(3)).log(any(List.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_before_after_not_reported_in_steps() {
		TestUtils.runTests(NoHooksReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		verify(client, times(2)).startTestItem(same(featureId), any());
		verify(client, times(2)).log(any(List.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_skip_exception_in_before_step_hook_reported_correctly() {
		TestUtils.runTests(SkipStepHooksReporterTest.class);

		// Capture log entries
		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, times(2)).log(logCaptor.capture());

		// Verify the first "Before" step has reported single Log entry with ERROR level and "SkipException" with "Skipping test" message
		List<SaveLogRQ> errorLogs = filterLogs(
				logCaptor,
				l -> l.getLevel() != null && l.getLevel().equals("ERROR") && l.getMessage() != null && l.getMessage()
						.contains("SkipException") && l.getMessage().contains("Skipping test")
		);
		assertThat(errorLogs, hasSize(1));
		SaveLogRQ skipExceptionLog = errorLogs.get(0);
		assertThat(skipExceptionLog.getMessage(), containsString("SkipException"));
		assertThat(skipExceptionLog.getMessage(), containsString("Skipping test"));
		assertThat(skipExceptionLog.getLevel(), equalTo("ERROR"));

		// Verify step statuses - all main steps and Before should be SKIPPED, After should be PASSED
		ArgumentCaptor<FinishTestItemRQ> stepsFinishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(eq(stepIds.get(0)), stepsFinishCaptor.capture());
		verify(client).finishTestItem(eq(stepIds.get(1)), stepsFinishCaptor.capture());
		verify(client).finishTestItem(eq(stepIds.get(2)), stepsFinishCaptor.capture());
		verify(client).finishTestItem(eq(stepIds.get(3)), stepsFinishCaptor.capture());
		verify(client).finishTestItem(eq(stepIds.get(4)), stepsFinishCaptor.capture());
		verify(client).finishTestItem(eq(stepIds.get(5)), stepsFinishCaptor.capture());
		List<FinishTestItemRQ> finishSteps = stepsFinishCaptor.getAllValues();
		assertThat(
				finishSteps.stream().map(FinishExecutionRQ::getStatus).collect(Collectors.toList()), containsInAnyOrder(
						ItemStatus.SKIPPED.name(),
						ItemStatus.SKIPPED.name(),
						ItemStatus.PASSED.name(),
						ItemStatus.SKIPPED.name(),
						ItemStatus.SKIPPED.name(),
						ItemStatus.SKIPPED.name()
				)
		);

		// Verify the Scenario finish event is reported as SKIPPED
		ArgumentCaptor<FinishTestItemRQ> scenarioFinishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(eq(scenarioIds.get(0)), scenarioFinishCaptor.capture());
		assertThat(scenarioFinishCaptor.getValue().getStatus(), equalTo(ItemStatus.SKIPPED.name()));
	}
}


