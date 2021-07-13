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
import com.epam.ta.reportportal.ws.model.ParameterResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import okhttp3.MultipartBody;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.epam.reportportal.cucumber.integration.util.TestUtils.filterLogs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class ParameterStepReporterTest {

	@CucumberOptions(features = "src/test/resources/features/BasicScenarioOutlineParameters.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class RunOutlineParametersTestStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/TwoScenarioOutlineParameters.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class RunTwoOutlineParametersTestStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/BasicInlineParameters.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class InlineParametersTestStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/DocStringParameters.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class DocstringParameterTestStepReporter extends AbstractTestNGCucumberTests {
	}

	@CucumberOptions(features = "src/test/resources/features/DataTableParameter.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class DataTableParameterTestStepReporter extends AbstractTestNGCucumberTests {
	}

	private static final String DOCSTRING_PARAM = "My very long parameter\nWith some new lines";
	private static final String TABLE_PARAM = Utils.formatDataTable(Arrays.asList(
			Arrays.asList("key", "value"),
			Arrays.asList("myKey", "myValue")
	));

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("test_")).limit(3).collect(Collectors.toList());
	private final List<Pair<String, List<String>>> tests = testIds.stream()
			.map(id -> Pair.of(id, Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList())))
			.collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ListenerParameters parameters = TestUtils.standardParameters();
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, parameters, executorService);

	@BeforeEach
	public void initLaunch() {
		TestUtils.mockLaunch(client, launchId, suiteId, tests);
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	public static final List<Pair<String, Object>> PARAMETERS = Arrays.asList(Pair.of("java.lang.String", "\"first\""),
			Pair.of("int", 123),
			Pair.of("java.lang.String", "\"second\""),
			Pair.of("int", 12345),
			Pair.of("java.lang.String", "\"third\""),
			Pair.of("int", 12345678)
	);

	public static final List<String> STEP_NAMES = Arrays.asList(String.format("When I have parameter %s", PARAMETERS.get(0).getValue()),
			String.format("Then I emit number %s on level info", PARAMETERS.get(1).getValue().toString()),
			String.format("When I have parameter %s", PARAMETERS.get(2).getValue()),
			String.format("Then I emit number %s on level info", PARAMETERS.get(3).getValue().toString()),
			String.format("When I have parameter %s", PARAMETERS.get(4).getValue()),
			String.format("Then I emit number %s on level info", PARAMETERS.get(5).getValue().toString())
	);

	@Test
	public void verify_agent_retrieves_parameters_from_request() {
		TestUtils.runTests(RunOutlineParametersTestStepReporter.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(3)).startTestItem(same(suiteId), any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(testIds.get(0)), captor.capture());
		verify(client, times(3)).startTestItem(same(testIds.get(1)), captor.capture());
		verify(client, times(3)).startTestItem(same(testIds.get(2)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		List<StartTestItemRQ> parameterizedSteps = items.stream()
				.filter(i -> "STEP".equals(i.getType()) && !i.getName().startsWith("Given"))
				.collect(Collectors.toList());
		IntStream.range(0, parameterizedSteps.size()).mapToObj(i -> Pair.of(i, parameterizedSteps.get(i))).forEach(e -> {
			StartTestItemRQ step = e.getValue();
			assertThat(step, notNullValue());
			String expectedName = STEP_NAMES.get(e.getKey());
			assertThat(step.getName(), equalTo(expectedName));
			assertThat(step.getParameters(), allOf(notNullValue(), hasSize(1)));
			ParameterResource param = e.getValue().getParameters().get(0);
			Pair<String, Object> expectedParam = PARAMETERS.get(e.getKey());

			assertThat(param.getKey(), equalTo(expectedParam.getKey()));
			assertThat(param.getValue(), equalTo(expectedParam.getValue().toString()));
		});

		List<StartTestItemRQ> notParameterizedSteps = items.stream()
				.filter(i -> i.getName().startsWith("Given"))
				.collect(Collectors.toList());
		notParameterizedSteps.forEach(i -> assertThat(i.getParameters(), emptyIterable()));
	}

	@Test
	public void verify_agent_retrieves_two_parameters_from_request() {
		TestUtils.runTests(RunTwoOutlineParametersTestStepReporter.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(3)).startTestItem(same(suiteId), any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(testIds.get(0)), captor.capture());
		verify(client, times(3)).startTestItem(same(testIds.get(1)), captor.capture());
		verify(client, times(3)).startTestItem(same(testIds.get(2)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		List<StartTestItemRQ> twoParameterItems = items.stream()
				.filter(i -> "STEP".equals(i.getType()) && i.getName().startsWith("Given"))
				.collect(Collectors.toList());
		List<StartTestItemRQ> oneParameterItems = items.stream()
				.filter(i -> "STEP".equals(i.getType()) && i.getName().startsWith("Then"))
				.collect(Collectors.toList());
		twoParameterItems.forEach(i -> assertThat(i.getParameters(), allOf(notNullValue(), hasSize(2))));
		oneParameterItems.forEach(i -> assertThat(i.getParameters(), allOf(notNullValue(), hasSize(1))));
	}

	@Test
	public void verify_inline_parameters() {
		TestUtils.runTests(InlineParametersTestStepReporter.class);

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(testIds.get(0)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		assertThat(items.get(0).getParameters(), allOf(notNullValue(), hasSize(1)));
		ParameterResource param1 = items.get(0).getParameters().get(0);
		assertThat(param1.getKey(), equalTo("int"));
		assertThat(param1.getValue(), equalTo("42"));

		assertThat(items.get(1).getParameters(), allOf(notNullValue(), hasSize(1)));
		ParameterResource param2 = items.get(1).getParameters().get(0);
		assertThat(param2.getKey(), equalTo("java.lang.String"));
		assertThat(param2.getValue(), equalTo("\"string\""));

		assertThat(items.get(2).getParameters(), allOf(notNullValue(), hasSize(1)));
		ParameterResource param3 = items.get(2).getParameters().get(0);
		assertThat(param3.getKey(), equalTo("my name"));
		assertThat(param3.getValue(), equalTo("\"string\""));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_docstring_parameters() {
		TestUtils.runTests(DocstringParameterTestStepReporter.class);

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(testIds.get(0)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		List<ParameterResource> params = items.get(1).getParameters();
		assertThat(params, allOf(notNullValue(), hasSize(1)));
		ParameterResource param1 = params.get(0);
		assertThat(param1.getKey(), equalTo("java.lang.String"));
		assertThat(param1.getValue(), equalTo(DOCSTRING_PARAM));

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, times(2)).log(logCaptor.capture());
		List<String> logs = filterLogs(logCaptor, l -> l.getItemUuid().equals(tests.get(0).getValue().get(1))).stream()
				.map(SaveLogRQ::getMessage)
				.collect(Collectors.toList());

		assertThat(logs, hasSize(1));
		assertThat(logs, not(hasItem(equalTo("\"\"\"\n" + DOCSTRING_PARAM + "\n\"\"\""))));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_data_table_parameters() {
		TestUtils.runTests(DataTableParameterTestStepReporter.class);

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(testIds.get(0)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		List<ParameterResource> params = items.get(0).getParameters();
		assertThat(params, allOf(notNullValue(), hasSize(1)));
		ParameterResource param1 = params.get(0);
		assertThat(param1.getKey(), equalTo("io.cucumber.datatable.DataTable"));
		assertThat(param1.getValue(), equalTo(TABLE_PARAM));

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, times(1)).log(logCaptor.capture());

		List<String> logs = filterLogs(logCaptor, l -> l.getItemUuid().equals(tests.get(0).getValue().get(0))).stream()
				.map(SaveLogRQ::getMessage)
				.collect(Collectors.toList());

		assertThat(logs, hasSize(1));
		assertThat(logs, not(hasItem(equalTo(TABLE_PARAM))));
	}
}
