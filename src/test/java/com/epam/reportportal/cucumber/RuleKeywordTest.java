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
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class RuleKeywordTest {
	@CucumberOptions(features = "src/test/resources/features/RuleKeyword.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class SimpleTestStepReporterTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/RuleKeyword.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class SimpleTestScenarioReporterTest extends AbstractTestNGCucumberTests {

	}

	// Step reporter
	private final String launchId = CommonUtils.namedId("launch_");
	private final String featureId = CommonUtils.namedId("feature_");
	private final List<String> ruleIds = Arrays.asList(CommonUtils.namedId("rule_"), CommonUtils.namedId("rule_"));
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("scenario_")).limit(3).collect(Collectors.toList());
	private final List<Pair<String, List<String>>> tests = Stream.concat(Stream.of(Pair.of(ruleIds.get(0), testIds.subList(0, 2))),
			Stream.of(Pair.of(ruleIds.get(1), testIds.subList(2, 3)))
	)
			.collect(Collectors.toList());
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(6).collect(Collectors.toList());
	private final List<Pair<String, String>> steps = IntStream.range(0, testIds.size())
			.boxed()
			.flatMap(i -> Stream.of(Pair.of(testIds.get(i), stepIds.get(i * 2)), Pair.of(testIds.get(i), stepIds.get((i * 2) + 1))))
			.collect(Collectors.toList());

	// Scenario reporter
	private final String suiteId = CommonUtils.namedId("suite_");
	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@Test
	public void verify_rule_keyword_step_reporter() {
		TestUtils.mockLaunch(client, launchId, featureId, tests);
		TestUtils.mockLogging(client);
		TestUtils.mockNestedSteps(client, steps);
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);

		TestUtils.runTests(SimpleTestStepReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		ArgumentCaptor<StartTestItemRQ> ruleRqCapture = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(featureId), ruleRqCapture.capture());

		List<StartTestItemRQ> ruleRqs = ruleRqCapture.getAllValues();
		assertThat(ruleRqs.get(0).getName(), equalTo("Rule: The first rule"));
		assertThat(ruleRqs.get(1).getName(), equalTo("Rule: The second rule"));
		ruleRqs.forEach(r -> assertThat(r.getType(), equalTo("SUITE")));

		ArgumentCaptor<StartTestItemRQ> testRqCapture = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(ruleIds.get(0)), testRqCapture.capture());
		verify(client, times(1)).startTestItem(same(ruleIds.get(1)), testRqCapture.capture());
		testRqCapture.getAllValues().forEach(t -> assertThat(t.getType(), equalTo("SCENARIO")));

	}

	@Test
	public void verify_rule_keyword_scenario_reporter() {
		TestUtils.mockLaunch(client, launchId, suiteId, featureId, ruleIds);
		TestUtils.mockLogging(client);
		TestUtils.mockNestedSteps(client,
				tests.stream().flatMap(e -> e.getValue().stream().map(v -> Pair.of(e.getKey(), v))).collect(Collectors.toList())
		);
		TestUtils.mockNestedSteps(client, steps);

		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);

		TestUtils.runTests(SimpleTestScenarioReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		ArgumentCaptor<StartTestItemRQ> ruleRqCapture = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(featureId), ruleRqCapture.capture());

		List<StartTestItemRQ> ruleRqs = ruleRqCapture.getAllValues();
		assertThat(ruleRqs.get(0).getName(), equalTo("Rule: The first rule"));
		assertThat(ruleRqs.get(1).getName(), equalTo("Rule: The second rule"));
		ruleRqs.forEach(r -> assertThat(r.getType(), equalTo("SUITE")));

		ArgumentCaptor<StartTestItemRQ> testRqCapture = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(ruleIds.get(0)), testRqCapture.capture());
		verify(client, times(1)).startTestItem(same(ruleIds.get(1)), testRqCapture.capture());
		testRqCapture.getAllValues().forEach(t -> assertThat(t.getType(), equalTo("STEP")));
	}
}
