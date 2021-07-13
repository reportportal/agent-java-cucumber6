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
import com.epam.reportportal.cucumber.integration.embed.image.EmbeddingStepdefs;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.Constants;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.cucumber.integration.util.TestUtils.filterLogs;
import static java.util.Optional.ofNullable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class EmbeddingTest {
	@CucumberOptions(features = "src/test/resources/features/embedding/ImageEmbeddingFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.image" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class ImageStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/embedding/TextEmbeddingFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.text" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class TextStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/embedding/PdfEmbeddingFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.pdf" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class PdfStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/embedding/ArchiveEmbeddingFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.zip" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class ZipStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/embedding/EmbedImageWithoutName.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.image" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class ImageNoNameStepReporter extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/embedding/EmbedImageWithEmptyName.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.image" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestStepReporter" })
	public static class ImageEmptyNameStepReporter extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(2).collect(Collectors.toList());
	private final List<Pair<String, List<String>>> tests = testIds.stream()
			.map(id -> Pair.of(id, Stream.generate(() -> CommonUtils.namedId("step_")).limit(2).collect(Collectors.toList())))
			.collect(Collectors.toList());

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, tests);
		TestUtils.mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
		TestStepReporter.RP.set(reportPortal);
	}

	private List<SaveLogRQ> getLogsWithFiles(ArgumentCaptor<List<MultipartBody.Part>> logCaptor) {
		return filterLogs(logCaptor, l -> Objects.nonNull(l.getFile()));
	}

	private List<MultipartBody.Part> getLogFiles(String name, ArgumentCaptor<List<MultipartBody.Part>> logCaptor) {
		return logCaptor.getAllValues()
				.stream()
				.flatMap(Collection::stream)
				.filter(p -> ofNullable(p.headers()).map(headers -> headers.get("Content-Disposition"))
						.map(h -> h.contains(Constants.LOG_REQUEST_BINARY_PART) && h.contains(name))
						.orElse(false))
				.collect(Collectors.toList());
	}

	@Test
	public void verify_image_embedding() {
		TestUtils.runTests(ImageStepReporter.class);

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, atLeastOnce()).log(logCaptor.capture());
		List<SaveLogRQ> logs = getLogsWithFiles(logCaptor);
		logs.forEach(l -> assertThat(l.getMessage(), equalTo(EmbeddingStepdefs.IMAGE_NAME)));

		List<String> types = logs.stream()
				.flatMap(l -> getLogFiles(l.getFile().getName(), logCaptor).stream())
				.flatMap(f -> ofNullable(f.body().contentType()).map(MediaType::toString).map(Stream::of).orElse(Stream.empty()))
				.collect(Collectors.toList());
		assertThat(types, hasSize(3));
		assertThat(types, containsInAnyOrder("image/jpeg", "image/png", "image/jpeg"));

	}

	@Test
	public void verify_text_embedding() {
		TestUtils.runTests(TextStepReporter.class);

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, atLeastOnce()).log(logCaptor.capture());
		List<SaveLogRQ> logs = getLogsWithFiles(logCaptor);

		List<String> types = logs.stream()
				.flatMap(l -> getLogFiles(l.getFile().getName(), logCaptor).stream())
				.flatMap(f -> ofNullable(f.body().contentType()).map(MediaType::toString).map(Stream::of).orElse(Stream.empty()))
				.collect(Collectors.toList());

		assertThat(types, hasSize(3));
		assertThat(types, containsInAnyOrder("text/plain", "image/png", "application/octet-stream"));
	}

	@Test
	public void verify_pfd_embedding() {
		TestUtils.runTests(PdfStepReporter.class);

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, atLeastOnce()).log(logCaptor.capture());
		List<SaveLogRQ> logs = getLogsWithFiles(logCaptor);

		assertThat(logs, hasSize(3));

		List<String> types = logs.stream()
				.flatMap(l -> getLogFiles(l.getFile().getName(), logCaptor).stream())
				.flatMap(f -> ofNullable(f.body().contentType()).map(MediaType::toString).map(Stream::of).orElse(Stream.empty()))
				.collect(Collectors.toList());
		assertThat(types, hasSize(3));
		assertThat(types, containsInAnyOrder("application/pdf", "image/png", "application/octet-stream"));
	}

	@Test
	public void verify_archive_embedding() {
		TestUtils.runTests(ZipStepReporter.class);

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, atLeastOnce()).log(logCaptor.capture());
		List<SaveLogRQ> logs = getLogsWithFiles(logCaptor);

		List<String> types = logs.stream()
				.flatMap(l -> getLogFiles(l.getFile().getName(), logCaptor).stream())
				.flatMap(f -> ofNullable(f.body().contentType()).map(MediaType::toString).map(Stream::of).orElse(Stream.empty()))
				.collect(Collectors.toList());
		assertThat(types, hasSize(3));
		assertThat(types, containsInAnyOrder("application/zip", "image/png", "application/octet-stream"));
	}

	@Test
	public void verify_image_no_name_embedding() {
		TestUtils.runTests(ImageNoNameStepReporter.class);

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, atLeastOnce()).log(logCaptor.capture());
		List<SaveLogRQ> logs = getLogsWithFiles(logCaptor);

		logs.forEach(l -> assertThat(l.getMessage(), equalTo("image")));

		List<String> types = logs.stream()
				.flatMap(l -> getLogFiles(l.getFile().getName(), logCaptor).stream())
				.flatMap(f -> ofNullable(f.body().contentType()).map(MediaType::toString).map(Stream::of).orElse(Stream.empty()))
				.collect(Collectors.toList());
		assertThat(types, hasSize(1));
		assertThat(types, containsInAnyOrder("image/jpeg"));
	}

	@Test
	public void verify_image_empty_name_embedding() {
		TestUtils.runTests(ImageEmptyNameStepReporter.class);

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, atLeastOnce()).log(logCaptor.capture());
		List<SaveLogRQ> logs = getLogsWithFiles(logCaptor);

		logs.forEach(l -> assertThat(l.getMessage(), equalTo("image")));

		List<String> types = logs.stream()
				.flatMap(l -> getLogFiles(l.getFile().getName(), logCaptor).stream())
				.flatMap(f -> ofNullable(f.body().contentType()).map(MediaType::toString).map(Stream::of).orElse(Stream.empty()))
				.collect(Collectors.toList());
		assertThat(types, hasSize(1));
		assertThat(types, containsInAnyOrder("image/jpeg"));
	}
}
