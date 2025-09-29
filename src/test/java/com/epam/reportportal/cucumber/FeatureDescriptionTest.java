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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.any;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class FeatureDescriptionTest {

	@CucumberOptions(features = "src/test/resources/features/belly.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class BellyScenarioReporterTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/belly.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class BellyStepReporterTest extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("test_")).limit(3).collect(Collectors.toList());
	private final List<Pair<String, List<String>>> tests = testIds.stream()
			.map(id -> Pair.of(id, Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList())))
			.collect(Collectors.toList());

	private final ListenerParameters parameters = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, parameters, executorService);

	@BeforeEach
	public void initLaunch() {
		TestUtils.mockLaunch(client, launchId, suiteId, tests);
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	private static final Pair<String, String> FEATURE_CODE_REFERENCES = Pair.of(
			"file:///",
			"/agent-java-cucumber6/src/test/resources/features/belly.feature"
	);

	private static final Pair<String, String> SCENARIO_CODE_REFERENCES = Pair.of(
			"file:///",
			"/agent-java-cucumber6/src/test/resources/features/belly.feature"
	);

	@Test
	public void verify_code_reference_scenario_reporter() {
		TestUtils.runTests(BellyScenarioReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(suiteId), captor.capture());
		verify(client, times(1)).startTestItem(same(testIds.get(0)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();

		StartTestItemRQ feature = items.get(0);
		StartTestItemRQ scenario = items.get(1);

		assertThat(
				feature.getDescription(),
				allOf(notNullValue(), startsWith(FEATURE_CODE_REFERENCES.getKey()), endsWith(FEATURE_CODE_REFERENCES.getValue()))
		);
		assertThat(
				scenario.getDescription(),
				allOf(notNullValue(), startsWith(SCENARIO_CODE_REFERENCES.getKey()), endsWith(SCENARIO_CODE_REFERENCES.getValue()))
		);
	}

	@Test
	public void verify_code_reference_step_reporter() {
		TestUtils.runTests(BellyStepReporterTest.class);

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(captor.capture());
		verify(client, times(1)).startTestItem(same(suiteId), captor.capture());
		verify(client, times(3)).startTestItem(same(testIds.get(0)), any());

		List<StartTestItemRQ> items = captor.getAllValues();
		StartTestItemRQ feature = items.get(0);
		StartTestItemRQ scenario = items.get(1);

		assertThat(
				feature.getDescription(),
				allOf(notNullValue(), startsWith(FEATURE_CODE_REFERENCES.getKey()), endsWith(FEATURE_CODE_REFERENCES.getValue()))
		);
		assertThat(
				scenario.getDescription(),
				allOf(notNullValue(), startsWith(SCENARIO_CODE_REFERENCES.getKey()), endsWith(SCENARIO_CODE_REFERENCES.getValue()))
		);
	}
}
