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

import com.epam.reportportal.service.Launch;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import io.cucumber.messages.Messages;
import io.cucumber.plugin.event.*;
import io.reactivex.Maybe;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Calendar;

/**
 * Cucumber reporter for ReportPortal that reports individual steps as test
 * methods.
 * <p>
 * Mapping between Cucumber and ReportPortal is as follows:
 * <ul>
 * <li>feature - SUITE</li>
 * <li>scenario - TEST</li>
 * <li>step - STEP</li>
 * </ul>
 * Background steps are reported as part of corresponding scenarios. Outline
 * example rows are reported as individual scenarios with [ROW NUMBER] after the
 * name. Hooks are reported as BEFORE/AFTER_METHOD items (NOTE: all screenshots
 * created in hooks will be attached to these, and not to the actual failing
 * steps!)
 *
 * @author Sergey_Gvozdyukevich
 * @author Serhii Zharskyi
 * @author Vitaliy Tsvihun
 */
public class StepReporter extends AbstractReporter {
	private static final String RP_STORY_TYPE = "STORY";
	private static final String RP_TEST_TYPE = "SCENARIO";

	public StepReporter() {
		super();
	}

	@Override
	protected Maybe<String> getRootItemId() {
		return null;
	}

	@Override
	protected void beforeStep(TestStep testStep) {
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		Messages.GherkinDocument.Feature.Step step = context.getStep(testStep);
		StartTestItemRQ rq = Utils.buildStartStepRequest(context.getStepPrefix(), testStep, step, true);
		context.setCurrentStepId(launch.get().startTestItem(context.getId(), rq));
	}

	@Override
	protected void afterStep(Result result) {
		reportResult(result, null);
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		Launch myLaunch = launch.get();
		myLaunch.getStepReporter().finishPreviousStep();
		Utils.finishTestItem(myLaunch, context.getCurrentStepId(), result.getStatus());
		context.setCurrentStepId(null);
	}

	@Override
	protected void beforeHooks(HookType hookType) {
		StartTestItemRQ rq = new StartTestItemRQ();
		Pair<String, String> typeName = Utils.getHookTypeAndName(hookType);
		rq.setType(typeName.getKey());
		rq.setName(typeName.getValue());
		rq.setStartTime(Calendar.getInstance().getTime());

		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		context.setHookStepId(launch.get().startTestItem(getCurrentScenarioContext().getId(), rq));
		context.setHookStatus(Status.PASSED);
	}

	@Override
	protected void afterHooks(Boolean isBefore) {
		RunningContext.ScenarioContext context = getCurrentScenarioContext();
		Launch myLaunch = launch.get();
		myLaunch.getStepReporter().finishPreviousStep();
		Utils.finishTestItem(myLaunch, context.getHookStepId(), context.getHookStatus());
		context.setHookStepId(null);
	}

	@Override
	protected void hookFinished(HookTestStep step, Result result, Boolean isBefore) {
		reportResult(result, (isBefore ? "Before" : "After") + " hook: " + step.getCodeLocation());
		getCurrentScenarioContext().setHookStatus(result.getStatus());
	}

	@Override
	protected String getFeatureTestItemType() {
		return RP_STORY_TYPE;
	}

	@Override
	protected String getScenarioTestItemType() {
		return RP_TEST_TYPE;
	}
}
