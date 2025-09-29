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

package com.epam.reportportal.cucumber;

import io.cucumber.plugin.event.Node;
import io.cucumber.plugin.event.TestCase;
import io.reactivex.Maybe;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.net.URI;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public class ScenarioContext {

	private final int line;
	private final URI uri;
	private final RuleContext rule;
	private final Node.Scenario scenario;
	private final Node.ScenarioOutline scenarioOutline;
	private final Node.Example example;

	private TestCase testCase;
	private Maybe<String> id = Maybe.empty();
	private Maybe<String> hookId = Maybe.empty();
	private Maybe<String> stepId = Maybe.empty();

	public ScenarioContext(@Nonnull URI scenarioFilePath, @Nullable RuleContext ruleNode, @Nonnull Node.Scenario scenarioNode) {
		uri = scenarioFilePath;
		rule = ruleNode;
		scenario = scenarioNode;
		scenarioOutline = null;
		example = null;
		line = scenario.getLocation().getLine();
	}

	public ScenarioContext(@Nonnull URI scenarioFilePath, @Nullable RuleContext ruleNode, @Nonnull Node.ScenarioOutline scenarioOutlineNode,
			@Nonnull Node.Example exampleNode) {
		uri = scenarioFilePath;
		rule = ruleNode;
		scenario = null;
		scenarioOutline = scenarioOutlineNode;
		example = exampleNode;
		line = scenarioOutline.getLocation().getLine();
	}

	public int getLine() {
		return line;
	}

	@Nonnull
	@SuppressWarnings("unused")
	public Optional<TestCase> getTestCase() {
		return ofNullable(testCase);
	}

	public void setTestCase(@Nullable TestCase testCase) {
		this.testCase = testCase;
	}

	@Nonnull
	public Optional<RuleContext> getRule() {
		return ofNullable(rule);
	}

	@Nonnull
	public URI getUri() {
		return uri;
	}

	@Nonnull
	public Maybe<String> getId() {
		return id;
	}

	public void setId(@Nonnull Maybe<String> id) {
		this.id = id;
	}

	public void setHookId(@Nonnull Maybe<String> hookStepId) {
		hookId = hookStepId;
	}

	@Nonnull
	public Maybe<String> getHookId() {
		return hookId;
	}

	public void setStepId(@Nonnull Maybe<String> currentStepId) {
		stepId = currentStepId;
	}

	@Nonnull
	public Maybe<String> getStepId() {
		return stepId;
	}

	@Nonnull
	public Optional<Node.Example> getExample() {
		return ofNullable(example);
	}
}
