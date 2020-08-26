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

import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import io.cucumber.messages.Messages;
import io.cucumber.plugin.event.*;
import io.reactivex.Maybe;
import org.apache.commons.lang3.tuple.Pair;
import rp.com.google.common.base.Supplier;
import rp.com.google.common.base.Suppliers;

import java.util.Calendar;

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
 * @author Sergey_Gvozdyukevich
 * @author Serhii Zharskyi
 * @author Vitaliy Tsvihun
 */
public class ScenarioReporter extends AbstractReporter {
    private static final String SEPARATOR = " --- ";
    private static final String RP_STORY_TYPE = "SUITE";
    private static final String RP_TEST_TYPE = "STORY";
    private static final String RP_STEP_TYPE = "STEP";
    private static final String HOOK_COLON = "Hook:";
    private static final String COLON_ = ": ";
    private static final String STEP_ = "STEP ";
    private static final String EMPTY_SUFFIX = "";
    private static final String INFO = "INFO";
    private static final String DUMMY_ROOT_SUITE_NAME = "Root User Story";

    protected Supplier<Maybe<String>> rootSuiteId;

    @Override
    protected void beforeLaunch() {
        super.beforeLaunch();
        startRootItem();
    }

    @Override
    protected void beforeStep(TestStep testStep) {
        RunningContext.ScenarioContext context = getCurrentScenarioContext();
        Messages.GherkinDocument.Feature.Step step = context.getStep(testStep);
        StartTestItemRQ rq = Utils.buildStartStepRequest(context.getStepPrefix(), testStep, step, false);
        context.setCurrentStepId(launch.get().startTestItem(context.getId(), rq));
    }

    @Override
    protected void afterStep(Result result) {
        reportResult(result, null);
        RunningContext.ScenarioContext context = getCurrentScenarioContext();
        Utils.finishTestItem(launch.get(), context.getCurrentStepId(), result.getStatus());
        context.setCurrentStepId(null);
    }

    @Override
    protected void beforeHooks(HookType hookType) {
        StartTestItemRQ rq = new StartTestItemRQ();
        rq.setHasStats(false);
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
        Utils.finishTestItem(launch.get(), context.getHookStepId(), context.getHookStatus());
        context.setHookStepId(null);
    }

    @Override
    protected void hookFinished(HookTestStep step, Result result, Boolean isBefore) {
        reportResult(result, (isBefore ? "Before" : "After") + " hook: " + step.getCodeLocation());
        getCurrentScenarioContext().setHookStatus(result.getStatus());
    }

    @Override
    protected String getFeatureTestItemType() {
        return RP_TEST_TYPE;
    }

    @Override
    protected String getScenarioTestItemType() {
        return RP_STEP_TYPE;
    }

    @Override
    protected Maybe<String> getRootItemId() {
        return rootSuiteId.get();
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
        Utils.finishTestItem(launch.get(), rootSuiteId.get());
        rootSuiteId = null;
    }

    /**
     * Start root suite
     */
    protected void startRootItem() {
        rootSuiteId = Suppliers.memoize(() -> {
            StartTestItemRQ rq = new StartTestItemRQ();
            rq.setName(DUMMY_ROOT_SUITE_NAME);
            rq.setStartTime(Calendar.getInstance().getTime());
            rq.setType(RP_STORY_TYPE);
            return launch.get().startTestItem(rq);
        });
    }

    /**
     * Add separators to log item to distinguish from real log messages
     *
     * @param message to decorate
     * @return decorated message
     */
    protected String decorateMessage(String message) {
        return SEPARATOR + message;
    }
}
