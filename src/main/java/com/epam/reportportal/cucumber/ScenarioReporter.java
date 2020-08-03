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
    private static final String RP_TEST_TYPE = "TEST";
    private static final String RP_STEP_TYPE = "STEP";
    private static final String HOOK_COLON = "Hook:";
    private static final String COLON_ = ": ";
    private static final String STEP_ = "STEP ";
    private static final String EMPTY_SUFFIX = "";
    private static final String INFO = "INFO";
    private static final String RP_STORY_TYPE = "STORY";
    private static final String DUMMY_ROOT_SUITE_NAME = "Root User Story";

    protected Supplier<Maybe<String>> rootSuiteId;

    @Override
    protected void beforeLaunch() {
        super.beforeLaunch();
        startRootItem();
    }

    @Override
    protected void beforeStep(TestStep testStep) {
        RunningContext.ScenarioContext currentScenarioContext = getCurrentScenarioContext();
        Messages.GherkinDocument.Feature.Step step = currentScenarioContext.getStep(testStep);
        int lineInFeatureFile = step.getLocation().getLine();
        String decoratedStepName = lineInFeatureFile + decorateMessage(Utils.buildNodeName(currentScenarioContext.getStepPrefix(),
                step.getKeyword(),
                Utils.getStepName(testStep),
                EMPTY_SUFFIX
        ));
        String multilineArg = Utils.buildMultilineArgument(testStep);
        Utils.sendLog(decoratedStepName + multilineArg, INFO);
    }

    @Override
    protected void afterStep(Result result) {
        if (result.getStatus() != Status.PASSED) {
            reportResult(result, decorateMessage(STEP_ + result.getStatus().name().toUpperCase()));
        }
    }

    @Override
    protected void beforeHooks(HookType hookType) {
        // noop
    }

    @Override
    protected void afterHooks(Boolean isBefore) {
        // noop
    }

    @Override
    protected void hookFinished(HookTestStep step, Result result, Boolean isBefore) {
        reportResult(result, step.getHookType().name() + HOOK_COLON + result.getStatus() + COLON_ + step.getCodeLocation());
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
