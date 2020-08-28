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

import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import io.cucumber.gherkin.GherkinDocumentBuilder;
import io.cucumber.gherkin.Parser;
import io.cucumber.gherkin.TokenMatcher;
import io.cucumber.messages.IdGenerator;
import io.cucumber.messages.Messages;
import io.cucumber.plugin.event.*;
import io.reactivex.Maybe;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Running context that contains mostly manipulations with Gherkin objects.
 * Keeps necessary information regarding current Feature, Scenario and Step
 *
 * @author Serhii Zharskyi
 * @author Vitaliy Tsvihun
 */
class RunningContext {

	private RunningContext() {
		throw new AssertionError("No instances should exist for the class!");
	}

	static class FeatureContext {
		private static final Map<URI, TestSourceRead> PATH_TO_READ_EVENT_MAP = new ConcurrentHashMap<>();
		private URI currentFeatureUri;
		private Maybe<String> currentFeatureId;
		private Messages.GherkinDocument.Feature currentFeature;
		private Set<ItemAttributesRQ> attributes;

		FeatureContext() {
			attributes = new HashSet<>();
		}

		static void addTestSourceReadEvent(URI uri, TestSourceRead event) {
			PATH_TO_READ_EVENT_MAP.put(uri, event);
		}

		ScenarioContext getScenarioContext(TestCase testCase) {
			Messages.GherkinDocument.Feature.Scenario scenario = getScenario(testCase);
			ScenarioContext context = new ScenarioContext();
			context.processTags(testCase.getTags());
			context.processScenario(scenario);
			context.setTestCase(testCase);
			context.processBackground(getBackground());
			context.processScenarioOutline(scenario);
			return context;
		}

		FeatureContext processTestSourceReadEvent(TestCase testCase) {
			TestSourceRead event = PATH_TO_READ_EVENT_MAP.get(testCase.getUri());
			currentFeature = getFeature(event.getSource());
			currentFeatureUri = event.getUri();
			attributes = Utils.extractAttributes(currentFeature.getTagsList());
			return this;
		}

		Messages.GherkinDocument.Feature getFeature(String source) {
			Parser<Messages.GherkinDocument.Builder> parser = new Parser<>(new GherkinDocumentBuilder(new IdGenerator.UUID()));
			TokenMatcher matcher = new TokenMatcher();
			Messages.GherkinDocument gherkinDocument = parser.parse(source, matcher).build();
			return gherkinDocument.getFeature();
		}

		Messages.GherkinDocument.Feature.Background getBackground() {
			Messages.GherkinDocument.Feature.FeatureChild scenario = getFeature().getChildren(0);
			return scenario.hasBackground() ? scenario.getBackground() : null;
		}

		Messages.GherkinDocument.Feature getFeature() {
			return currentFeature;
		}

		Set<ItemAttributesRQ> getAttributes() {
			return attributes;
		}

		URI getUri() {
			return currentFeatureUri;
		}

		Maybe<String> getFeatureId() {
			return currentFeatureId;
		}

		void setFeatureId(Maybe<String> featureId) {
			this.currentFeatureId = featureId;
		}

		@SuppressWarnings("unchecked")
		<T extends Messages.GherkinDocument.Feature.Scenario> T getScenario(TestCase testCase) {
			List<Messages.GherkinDocument.Feature.FeatureChild> featureScenarios = getFeature().getChildrenList();
			for (Messages.GherkinDocument.Feature.FeatureChild child : featureScenarios) {
				if (!child.hasScenario()) {
					continue;
				}
				Messages.GherkinDocument.Feature.Scenario scenario = child.getScenario();
				if (testCase.getLocation().getLine() == scenario.getLocation().getLine() && testCase.getName().equals(scenario.getName())) {
					return (T) scenario;
				} else {
					if (scenario.getExamplesCount() > 0) {
						for (Messages.GherkinDocument.Feature.Scenario.Examples example : scenario.getExamplesList()) {
							for (Messages.GherkinDocument.Feature.TableRow tableRow : example.getTableBodyList()) {
								if (tableRow.getLocation().getLine() == testCase.getLocation().getLine()) {
									return (T) scenario;
								}
							}
						}
					}
				}
			}
			throw new IllegalStateException("Scenario can't be null!");
		}
	}

	static class ScenarioContext {
		private static final Map<Messages.GherkinDocument.Feature.Scenario, List<Integer>> scenarioOutlineMap = new ConcurrentHashMap<>();

		private Maybe<String> currentStepId;
		private Maybe<String> hookStepId;
		private Status hookStatus;
		private Maybe<String> id;
		private Messages.GherkinDocument.Feature.Background background;
		private Messages.GherkinDocument.Feature.Scenario scenario;
		private final Queue<Messages.GherkinDocument.Feature.Step> backgroundSteps;
		private final Map<Integer, Messages.GherkinDocument.Feature.Step> scenarioLocationMap;
		private Set<ItemAttributesRQ> attributes;
		private TestCase testCase;
		private boolean hasBackground = false;
		private String scenarioDesignation;
		private String outlineIteration;

		ScenarioContext() {
			backgroundSteps = new ArrayDeque<>();
			scenarioLocationMap = new HashMap<>();
			attributes = new HashSet<>();
		}

		void processScenario(Messages.GherkinDocument.Feature.Scenario scenario) {
			this.scenario = scenario;
			for (Messages.GherkinDocument.Feature.Step step : scenario.getStepsList()) {
				scenarioLocationMap.put(step.getLocation().getLine(), step);
			}
		}

		void processBackground(Messages.GherkinDocument.Feature.Background background) {
			if (background != null) {
				this.background = background;
				hasBackground = true;
				backgroundSteps.addAll(background.getStepsList());
				mapBackgroundSteps(background);
			}
		}

		public Set<ItemAttributesRQ> getAttributes() {
			return attributes;
		}

		/**
		 * Takes the serial number of scenario outline and links it to the executing scenario
		 **/
		void processScenarioOutline(Messages.GherkinDocument.Feature.Scenario scenarioOutline) {
			if (isScenarioOutline(scenarioOutline)) {
				scenarioOutlineMap.computeIfAbsent(scenarioOutline,
						k -> scenarioOutline.getExamplesList()
								.stream()
								.flatMap(e -> e.getTableBodyList().stream())
								.map(r -> r.getLocation().getLine())
								.collect(Collectors.toList())
				);
				int iterationIdx = IntStream.range(0, scenarioOutlineMap.get(scenarioOutline).size())
						.filter(i -> getLine() == scenarioOutlineMap.get(scenarioOutline).get(i))
						.findFirst()
						.orElseThrow(() -> new IllegalStateException(String.format("No outline iteration number found for scenario %s",
								scenarioDesignation
						)));
				outlineIteration = String.format("[%d]", iterationIdx + 1);
			}
		}

		void processTags(List<String> tags) {
			attributes = Utils.extractAttributes(tags);
		}

		void mapBackgroundSteps(Messages.GherkinDocument.Feature.Background background) {
			for (Messages.GherkinDocument.Feature.Step step : background.getStepsList()) {
				scenarioLocationMap.put(step.getLocation().getLine(), step);
			}
		}

		String getName() {
			return scenario.getName();
		}

		String getKeyword() {
			return scenario.getKeyword();
		}

		int getLine() {
			return isScenarioOutline(scenario) ? testCase.getLocation().getLine() : scenario.getLocation().getLine();
		}

		String getStepPrefix() {
			return hasBackground() && withBackground() ? background.getKeyword().toUpperCase() + AbstractReporter.COLON_INFIX : "";
		}

		Messages.GherkinDocument.Feature.Step getStep(TestStep testStep) {
			PickleStepTestStep pickleStepTestStep = (PickleStepTestStep) testStep;
			Messages.GherkinDocument.Feature.Step step = scenarioLocationMap.get(pickleStepTestStep.getStep().getLine());
			if (step != null) {
				return step;
			}
			throw new IllegalStateException(String.format("Trying to get step for unknown line in feature. " + "Scenario: %s, line: %s",
					scenario.getName(),
					getLine()
			));
		}

		Maybe<String> getId() {
			return id;
		}

		void setId(Maybe<String> newId) {
			if (id != null) {
				throw new IllegalStateException("Attempting re-set scenario ID for unfinished scenario: " + getName());
			}
			id = newId;
		}

		void setTestCase(TestCase testCase) {
			this.testCase = testCase;
			scenarioDesignation = testCase.getScenarioDesignation();
		}

		void nextBackgroundStep() {
			backgroundSteps.poll();
		}

		boolean isScenarioOutline(Messages.GherkinDocument.Feature.Scenario scenario) {
			return scenario.getExamplesCount() > 0;
		}

		boolean withBackground() {
			return !backgroundSteps.isEmpty();
		}

		boolean hasBackground() {
			return hasBackground && background != null;
		}

		String getOutlineIteration() {
			return outlineIteration;
		}

		public Maybe<String> getCurrentStepId() {
			return currentStepId;
		}

		public void setCurrentStepId(Maybe<String> currentStepId) {
			this.currentStepId = currentStepId;
		}

		public Maybe<String> getHookStepId() {
			return hookStepId;
		}

		public void setHookStepId(Maybe<String> hookStepId) {
			this.hookStepId = hookStepId;
		}

		public Status getHookStatus() {
			return hookStatus;
		}

		public void setHookStatus(Status hookStatus) {
			this.hookStatus = hookStatus;
		}
	}
}
