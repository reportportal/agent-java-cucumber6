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

import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.utils.MemoizingSupplier;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import io.cucumber.plugin.event.HookTestStep;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCase;
import io.reactivex.Maybe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Calendar;
import java.util.Optional;

/**
 * Cucumber reporter for ReportPortal that reports scenarios as test methods.
 * <p>
 * Mapping between Cucumber and ReportPortal is as follows:
 * <ul>
 * <li>feature - TEST</li>
 * <li>scenario - STEP</li>
 * <li>step - log item</li>
 * </ul>
 * <p>
 * Dummy "Root Test Suite" is created because in current implementation of RP
 * test items cannot be immediate children of a launch
 * <p>
 * Background steps and hooks are reported as part of corresponding scenarios.
 * Outline example rows are reported as individual scenarios with [ROW NUMBER]
 * after the name.
 *
 * @author Vadzim Hushchanskou
 */
public class ScenarioReporter extends AbstractReporter {
	private static final String RP_STORY_TYPE = ItemType.SUITE.name();
	private static final String RP_TEST_TYPE = ItemType.STORY.name();
	private static final String RP_STEP_TYPE = ItemType.STEP.name();
	private static final String DUMMY_ROOT_SUITE_NAME = "Root User Story";

	protected MemoizingSupplier<Maybe<String>> rootSuiteId;

	@Override
	protected void beforeLaunch() {
		super.beforeLaunch();
		startRootItem();
	}

	@Override
	@Nonnull
	protected StartTestItemRQ buildStartStepRequest(@Nonnull PickleStepTestStep testStep, @Nullable String stepPrefix,
			@Nullable String keyword) {
		StartTestItemRQ rq = super.buildStartStepRequest(testStep, stepPrefix, keyword);
		rq.setHasStats(false);
		return rq;
	}

	@Override
	protected void beforeStep(@Nonnull TestCase testCase, @Nonnull PickleStepTestStep testStep) {
		super.beforeStep(testCase, testStep);
		String description = buildMultilineArgument(testStep).trim();
		if (!description.isEmpty()) {
			sendLog(description);
		}
	}

	@Override
	@Nonnull
	protected StartTestItemRQ buildStartHookRequest(@Nonnull TestCase testCase, @Nonnull HookTestStep testStep) {
		StartTestItemRQ rq = super.buildStartHookRequest(testCase, testStep);
		rq.setHasStats(false);
		return rq;
	}

	@Override
	@Nonnull
	protected String getFeatureTestItemType() {
		return RP_TEST_TYPE;
	}

	@Override
	@Nonnull
	protected String getScenarioTestItemType() {
		return RP_STEP_TYPE;
	}

	@Override
	@Nonnull
	protected Optional<Maybe<String>> getRootItemId() {
		return Optional.of(rootSuiteId.get());
	}

	@Override
	protected void afterLaunch() {
		finishRootItem();
		super.afterLaunch();
	}

	/**
	 * Finish root suite
	 */
	protected void finishRootItem() {
		if (rootSuiteId.isInitialized()) {
			finishTestItem(rootSuiteId.get());
			rootSuiteId = null;
		}
	}

	/**
	 * Start root suite
	 */
	protected void startRootItem() {
		rootSuiteId = new MemoizingSupplier<>(() -> {
			StartTestItemRQ rq = new StartTestItemRQ();
			rq.setName(DUMMY_ROOT_SUITE_NAME);
			rq.setStartTime(Calendar.getInstance().getTime());
			rq.setType(RP_STORY_TYPE);
			return getLaunch().startTestItem(rq);
		});
	}
}
