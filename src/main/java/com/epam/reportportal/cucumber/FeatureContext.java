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

import io.cucumber.core.gherkin.Feature;
import io.cucumber.plugin.event.Node;
import io.reactivex.Maybe;
import jakarta.annotation.Nonnull;

import java.net.URI;
import java.util.*;

import static java.util.Optional.ofNullable;

public class FeatureContext {

	private final URI uri;
	private final Feature feature;
	private final Map<Integer, ScenarioContext> scenarios = new HashMap<>();
	private final Set<String> tags;

	private Maybe<String> id = Maybe.empty();
	private RuleContext currentRule;

	private void handleNode(@Nonnull Deque<RuleContext> ruleQueue, @Nonnull URI uri, @Nonnull Node node) {
		if (node instanceof Node.Rule) {
			Node.Rule rule = (Node.Rule) node;
			RuleContext ruleContext = new RuleContext(uri, (Node.Rule) node);
			ruleQueue.add(ruleContext);
			rule.elements().forEach(n -> handleNode(ruleQueue, uri, n));
		}
		if (node instanceof Node.Scenario) {
			Node.Scenario scenario = (Node.Scenario) node;
			int line = scenario.getLocation().getLine();
			scenarios.put(line, new ScenarioContext(uri, ruleQueue.peekLast(), scenario));
		}
		if (node instanceof Node.ScenarioOutline) {
			Node.ScenarioOutline scenarioOutline = (Node.ScenarioOutline) node;
			scenarioOutline.elements()
					.stream()
					.flatMap(e -> e.elements().stream())
					.forEach(e -> scenarios.put(
							e.getLocation().getLine(),
							new ScenarioContext(uri, ruleQueue.peekLast(), scenarioOutline, e)
					));
		}
	}

	private <T extends Node> void handleNodes(@Nonnull URI uri, @Nonnull Collection<T> nodes) {
		Deque<RuleContext> ruleQueue = new LinkedList<>();
		nodes.forEach(n -> handleNode(ruleQueue, uri, n));
	}

	public FeatureContext(@Nonnull URI featureUri, @Nonnull Feature featureNode) {
		uri = featureUri;
		feature = featureNode;
		handleNodes(featureUri, featureNode.elements());
		tags = Utils.getTags(featureNode);
	}

	@Nonnull
	public Feature getFeature() {
		return feature;
	}

	@Nonnull
	public URI getUri() {
		return uri;
	}

	@Nonnull
	public Set<String> getTags() {
		return tags;
	}

	@Nonnull
	public Maybe<String> getId() {
		return id;
	}

	public void setId(@Nonnull Maybe<String> id) {
		this.id = id;
	}

	@Nonnull
	public Optional<ScenarioContext> getScenario(@Nonnull Integer line) {
		return ofNullable(scenarios.get(line));
	}

	@Nonnull
	public Optional<RuleContext> getCurrentRule() {
		return ofNullable(currentRule);
	}

	public void setCurrentRule(@Nonnull RuleContext rule) {
		currentRule = rule;
	}
}
