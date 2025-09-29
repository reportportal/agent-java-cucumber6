/*
 * Copyright 2021 EPAM Systems
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

package com.epam.reportportal.cucumber.integration.util;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.utils.http.HttpRequestUtils;
import com.epam.ta.reportportal.ws.model.BatchSaveOperatingRS;
import com.epam.ta.reportportal.ws.model.Constants;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.epam.ta.reportportal.ws.model.item.ItemCreatedRS;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRS;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.fasterxml.jackson.core.type.TypeReference;
import io.reactivex.Maybe;
import okhttp3.MultipartBody;
import okio.Buffer;
import org.apache.commons.lang3.tuple.Pair;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.testng.TestNG;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.epam.reportportal.util.test.CommonUtils.generateUniqueId;
import static java.util.Optional.ofNullable;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class TestUtils {

	public static final String TEST_NAME = "TestContainer";

	public static TestNG runTests(Class<?>... classes) {
		final TestNG testNG = new TestNG(true);
		testNG.setTestClasses(classes);
		testNG.setDefaultTestName(TEST_NAME);
		testNG.setExcludedGroups("optional");
		testNG.run();
		return testNG;
	}

	public static void mockLaunch(ReportPortalClient client, String launchUuid, String suiteUuid, String testClassUuid,
			String testMethodUuid) {
		mockLaunch(client, launchUuid, suiteUuid, testClassUuid, Collections.singleton(testMethodUuid));
	}

	public static void mockLaunch(ReportPortalClient client, String launchUuid, String suiteUuid, String testClassUuid,
			Collection<String> testMethodUuidList) {
		mockLaunch(client, launchUuid, suiteUuid, Collections.singletonList(Pair.of(testClassUuid, testMethodUuidList)));
	}

	@SuppressWarnings("unchecked")

	public static <T extends Collection<String>> void mockLaunch(ReportPortalClient client, String launchUuid, String suiteUuid,
			Collection<Pair<String, T>> testSteps) {
		when(client.startLaunch(any())).thenReturn(Maybe.just(new StartLaunchRS(launchUuid, 1L)));

		Maybe<ItemCreatedRS> suiteMaybe = Maybe.just(new ItemCreatedRS(suiteUuid, suiteUuid));
		when(client.startTestItem(any())).thenReturn(suiteMaybe);

		List<Maybe<ItemCreatedRS>> testResponses = testSteps.stream()
				.map(Pair::getKey)
				.map(uuid -> Maybe.just(new ItemCreatedRS(uuid, uuid)))
				.collect(Collectors.toList());

		Maybe<ItemCreatedRS> first = testResponses.get(0);
		Maybe<ItemCreatedRS>[] other = testResponses.subList(1, testResponses.size()).toArray(new Maybe[0]);
		when(client.startTestItem(same(suiteUuid), any())).thenReturn(first, other);

		testSteps.forEach(test -> {
			String testClassUuid = test.getKey();
			List<Maybe<ItemCreatedRS>> stepResponses = test.getValue()
					.stream()
					.map(uuid -> Maybe.just(new ItemCreatedRS(uuid, uuid)))
					.collect(Collectors.toList());

			Maybe<ItemCreatedRS> myFirst = stepResponses.get(0);
			Maybe<ItemCreatedRS>[] myOther = stepResponses.subList(1, stepResponses.size()).toArray(new Maybe[0]);
			when(client.startTestItem(same(testClassUuid), any())).thenReturn(myFirst, myOther);
			new HashSet<>(test.getValue()).forEach(testMethodUuid -> when(client.finishTestItem(same(testMethodUuid), any())).thenReturn(
					Maybe.just(new OperationCompletionRS())));
			when(client.finishTestItem(same(testClassUuid), any())).thenReturn(Maybe.just(new OperationCompletionRS()));
		});

		Maybe<OperationCompletionRS> suiteFinishMaybe = Maybe.just(new OperationCompletionRS());
		when(client.finishTestItem(eq(suiteUuid), any())).thenReturn(suiteFinishMaybe);

		when(client.finishLaunch(eq(launchUuid), any())).thenReturn(Maybe.just(new OperationCompletionRS()));
	}

	@SuppressWarnings("unchecked")
	public static void mockLogging(ReportPortalClient client) {
		when(client.log(any(List.class))).thenReturn(Maybe.just(new BatchSaveOperatingRS()));
	}

	public static void mockNestedSteps(ReportPortalClient client, Pair<String, String> parentNestedPair) {
		mockNestedSteps(client, Collections.singletonList(parentNestedPair));
	}

	@SuppressWarnings("unchecked")
	public static void mockNestedSteps(final ReportPortalClient client, final List<Pair<String, String>> parentNestedPairs) {
		Map<String, List<String>> responseOrders = parentNestedPairs.stream()
				.collect(Collectors.groupingBy(Pair::getKey, Collectors.mapping(Pair::getValue, Collectors.toList())));
		responseOrders.forEach((k, v) -> {
			List<Maybe<ItemCreatedRS>> responses = v.stream()
					.map(uuid -> Maybe.just(new ItemCreatedRS(uuid, uuid)))
					.collect(Collectors.toList());

			Maybe<ItemCreatedRS> first = responses.get(0);
			Maybe<ItemCreatedRS>[] other = responses.subList(1, responses.size()).toArray(new Maybe[0]);
			when(client.startTestItem(eq(k), any())).thenReturn(first, other);
		});
		parentNestedPairs.forEach(p -> when(client.finishTestItem(
				same(p.getValue()),
				any()
		)).thenAnswer((Answer<Maybe<OperationCompletionRS>>) invocation -> Maybe.just(new OperationCompletionRS())));
	}

	public static ListenerParameters standardParameters() {
		ListenerParameters result = new ListenerParameters();
		result.setClientJoin(false);
		result.setBatchLogsSize(1);
		result.setLaunchName("My-test-launch" + generateUniqueId());
		result.setProjectName("test-project");
		result.setEnable(true);
		result.setBaseUrl("http://localhost:8080");
		return result;
	}

	public static List<SaveLogRQ> extractJsonParts(List<MultipartBody.Part> parts) {
		return parts.stream()
				.filter(p -> ofNullable(p.headers()).map(headers -> headers.get("Content-Disposition"))
						.map(h -> h.contains(Constants.LOG_REQUEST_JSON_PART))
						.orElse(false))
				.map(MultipartBody.Part::body)
				.map(b -> {
					Buffer buf = new Buffer();
					try {
						b.writeTo(buf);
					} catch (IOException ignore) {
					}
					return buf.readByteArray();
				})
				.map(b -> {
					try {
						return HttpRequestUtils.MAPPER.readValue(
								b, new TypeReference<>() {
								}
						);
					} catch (IOException e) {
						return Collections.<SaveLogRQ>emptyList();
					}
				})
				.flatMap(Collection::stream)
				.collect(Collectors.toList());
	}

	public static List<SaveLogRQ> filterLogs(ArgumentCaptor<List<MultipartBody.Part>> logCaptor, Predicate<SaveLogRQ> filter) {
		return logCaptor.getAllValues().stream().flatMap(l -> extractJsonParts(l).stream()).filter(filter).collect(Collectors.toList());
	}
}
