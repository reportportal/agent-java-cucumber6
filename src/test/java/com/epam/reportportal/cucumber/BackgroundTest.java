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
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static com.epam.reportportal.cucumber.integration.util.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class BackgroundTest {

	@CucumberOptions(features = "src/test/resources/features/BackgroundScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class MyStepReporterTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/BackgroundScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class MyScenarioReporterTest extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("test_")).limit(2).collect(Collectors.toList());
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(4).collect(Collectors.toList());

	private final List<Pair<String, List<String>>> steps = IntStream.range(0, testIds.size())
			.mapToObj(i -> Pair.of(testIds.get(i), stepIds.subList(i * 2, i * 2 + 2)))
			.collect(Collectors.toList());

	private final List<String> nestedStepIds = Stream.generate(() -> CommonUtils.namedId("nested_step_"))
			.limit(4)
			.collect(Collectors.toList());

	private final List<Pair<String, String>> nestedSteps = Stream.of(
			Pair.of(stepIds.get(0), nestedStepIds.get(0)),
			Pair.of(stepIds.get(0), nestedStepIds.get(1)),
			Pair.of(stepIds.get(1), nestedStepIds.get(2)),
			Pair.of(stepIds.get(1), nestedStepIds.get(3))
	).collect(Collectors.toList());

	private final ListenerParameters params = standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		mockLaunch(client, launchId, suiteId, steps);
		mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	@AfterEach
	public void tearDown() {
		CommonUtils.shutdownExecutorService(executorService);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_background_step_reporter() {
		runTests(MyStepReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(2)).startTestItem(same(suiteId), any());
		ArgumentCaptor<StartTestItemRQ> firstStepStarts = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(testIds.get(0)), firstStepStarts.capture());
		ArgumentCaptor<StartTestItemRQ> secondStepStarts = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(testIds.get(1)), secondStepStarts.capture());
		verify(client, times(5)).log(any(List.class));

		assertThat(firstStepStarts.getAllValues()
				.stream()
				.filter(r -> r.getName().startsWith(AbstractReporter.BACKGROUND_PREFIX))
				.collect(Collectors.toList()), hasSize(1));

		assertThat(secondStepStarts.getAllValues()
				.stream()
				.filter(r -> r.getName().startsWith(AbstractReporter.BACKGROUND_PREFIX))
				.collect(Collectors.toList()), hasSize(1));

		ArgumentCaptor<FinishTestItemRQ> stepFinishes = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(stepIds.get(0)), stepFinishes.capture());
		verify(client).finishTestItem(same(stepIds.get(1)), stepFinishes.capture());
		verify(client).finishTestItem(same(stepIds.get(2)), stepFinishes.capture());
		verify(client).finishTestItem(same(stepIds.get(3)), stepFinishes.capture());

		List<Date> endDates = stepFinishes.getAllValues().stream().map(FinishExecutionRQ::getEndTime).filter(Objects::nonNull)
				.collect(Collectors.toList());
		assertThat(endDates, hasSize(4));

		List<String> statuses = stepFinishes.getAllValues().stream().map(FinishExecutionRQ::getStatus).filter(Objects::nonNull)
				.collect(Collectors.toList());
		assertThat(statuses, hasSize(4));
		assertThat(statuses, containsInAnyOrder(Collections.nCopies(4, equalTo(ItemStatus.PASSED.name()))));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_background_scenario_reporter() {
		mockNestedSteps(client, nestedSteps);
		runTests(MyScenarioReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		verify(client, times(2)).startTestItem(same(testIds.get(0)), any());
		ArgumentCaptor<StartTestItemRQ> firstStepStarts = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(0)), firstStepStarts.capture());
		ArgumentCaptor<StartTestItemRQ> secondStepStarts = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(1)), secondStepStarts.capture());
		verify(client, times(5)).log(any(List.class));

		assertThat(firstStepStarts.getAllValues()
				.stream()
				.filter(r -> r.getName().startsWith(AbstractReporter.BACKGROUND_PREFIX))
				.collect(Collectors.toList()), hasSize(1));

		assertThat(secondStepStarts.getAllValues()
				.stream()
				.filter(r -> r.getName().startsWith(AbstractReporter.BACKGROUND_PREFIX))
				.collect(Collectors.toList()), hasSize(1));

		ArgumentCaptor<FinishTestItemRQ> stepFinishes = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(nestedStepIds.get(0)), stepFinishes.capture());
		verify(client).finishTestItem(same(nestedStepIds.get(1)), stepFinishes.capture());
		verify(client).finishTestItem(same(nestedStepIds.get(2)), stepFinishes.capture());
		verify(client).finishTestItem(same(nestedStepIds.get(3)), stepFinishes.capture());

		List<Date> endDates = stepFinishes.getAllValues().stream().map(FinishExecutionRQ::getEndTime).filter(Objects::nonNull)
				.collect(Collectors.toList());
		assertThat(endDates, hasSize(4));

		List<String> statuses = stepFinishes.getAllValues().stream().map(FinishExecutionRQ::getStatus).filter(Objects::nonNull)
				.collect(Collectors.toList());
		assertThat(statuses, hasSize(4));
		assertThat(statuses, containsInAnyOrder(Collections.nCopies(4, equalTo(ItemStatus.PASSED.name()))));

		ArgumentCaptor<FinishTestItemRQ> testFinishes = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(stepIds.get(0)), testFinishes.capture());
		verify(client).finishTestItem(same(stepIds.get(1)), testFinishes.capture());

		endDates = testFinishes.getAllValues().stream().map(FinishExecutionRQ::getEndTime).filter(Objects::nonNull)
				.collect(Collectors.toList());
		assertThat(endDates, hasSize(2));

		statuses = testFinishes.getAllValues().stream().map(FinishExecutionRQ::getStatus).filter(Objects::nonNull)
				.collect(Collectors.toList());
		assertThat(statuses, hasSize(2));
		assertThat(statuses, containsInAnyOrder(Collections.nCopies(2, equalTo(ItemStatus.PASSED.name()))));
	}
}


