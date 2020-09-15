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

import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import io.cucumber.gherkin.GherkinDocumentBuilder;
import io.cucumber.gherkin.Parser;
import io.cucumber.gherkin.TokenMatcher;
import io.cucumber.messages.IdGenerator;
import io.cucumber.messages.Messages;
import io.cucumber.plugin.event.*;
import io.reactivex.Maybe;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
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
 * @author Vadzim Hushchanskou
 */
public class RunningContext {

	private RunningContext() {
		throw new AssertionError("No instances should exist for the class!");
	}

	public static class FeatureContext {
		private static final Map<URI, TestSourceRead> PATH_TO_READ_EVENT_MAP = new ConcurrentHashMap<>();
		private final URI currentFeatureUri;
		private final Messages.GherkinDocument.Feature currentFeature;
		private final Set<ItemAttributesRQ> attributes;
		private Maybe<String> currentFeatureId;
		private RuleContext rule;

		public FeatureContext(TestCase testCase) {
			TestSourceRead event = PATH_TO_READ_EVENT_MAP.get(testCase.getUri());
			currentFeature = getFeature(event.getSource());
			currentFeatureUri = event.getUri();
			attributes = Utils.extractAttributes(currentFeature.getTagsList());
		}

		public static void addTestSourceReadEvent(URI uri, TestSourceRead event) {
			PATH_TO_READ_EVENT_MAP.put(uri, event);
		}

		public ScenarioContext getScenarioContext(TestCase testCase) {
			Pair<Messages.GherkinDocument.Feature.Scenario, RuleContext> scenario = getScenario(testCase);
			ScenarioContext context = new ScenarioContext();
			context.processTags(testCase.getTags());
			context.processScenario(scenario.getKey());
			context.setTestCase(testCase);
			context.processBackground(getBackground());
			context.processScenarioOutline(scenario.getKey());
			context.setFeatureUri(getUri());
			context.setRule(scenario.getValue());
			return context;
		}

		public Messages.GherkinDocument.Feature getFeature(String source) {
			Parser<Messages.GherkinDocument.Builder> parser = new Parser<>(new GherkinDocumentBuilder(new IdGenerator.UUID()));
			TokenMatcher matcher = new TokenMatcher();
			Messages.GherkinDocument gherkinDocument = parser.parse(source, matcher).build();
			return gherkinDocument.getFeature();
		}

		public Messages.GherkinDocument.Feature.Background getBackground() {
			Messages.GherkinDocument.Feature.FeatureChild scenario = getFeature().getChildren(0);
			return scenario.hasBackground() ? scenario.getBackground() : null;
		}

		public Messages.GherkinDocument.Feature getFeature() {
			return currentFeature;
		}

		public Set<ItemAttributesRQ> getAttributes() {
			return attributes;
		}

		public URI getUri() {
			return currentFeatureUri;
		}

		public Maybe<String> getFeatureId() {
			return currentFeatureId;
		}

		public void setFeatureId(Maybe<String> featureId) {
			this.currentFeatureId = featureId;
		}

		public Pair<Messages.GherkinDocument.Feature.Scenario, RuleContext> getScenario(TestCase testCase) {
			List<Messages.GherkinDocument.Feature.FeatureChild> featureChildren = getFeature().getChildrenList();
			for (Messages.GherkinDocument.Feature.FeatureChild child : featureChildren) {
				if (child.hasScenario()) {
					Messages.GherkinDocument.Feature.Scenario result = getScenario(child.getScenario(), testCase);
					if (result != null) {
						return Pair.of(result, null);
					}
				}
				if (child.hasRule()) {
					Messages.GherkinDocument.Feature.FeatureChild.Rule rule = child.getRule();
					Messages.GherkinDocument.Feature.Scenario result = getScenario(rule, testCase);
					if (result != null) {
						return Pair.of(result, new RuleContext(getUri(), rule, testCase));
					}
				}
			}
			throw new IllegalStateException("Scenario can't be null!");
		}

		public Messages.GherkinDocument.Feature.Scenario getScenario(Messages.GherkinDocument.Feature.Scenario scenario,
				TestCase testCase) {
			if (testCase.getLocation().getLine() == scenario.getLocation().getLine() && testCase.getName().equals(scenario.getName())) {
				return scenario;
			} else {
				if (scenario.getExamplesCount() > 0) {
					for (Messages.GherkinDocument.Feature.Scenario.Examples example : scenario.getExamplesList()) {
						for (Messages.GherkinDocument.Feature.TableRow tableRow : example.getTableBodyList()) {
							if (tableRow.getLocation().getLine() == testCase.getLocation().getLine()) {
								return scenario;
							}
						}
					}
				}
			}
			return null;
		}

		public Messages.GherkinDocument.Feature.Scenario getScenario(Messages.GherkinDocument.Feature.FeatureChild.Rule rule,
				TestCase testCase) {
			List<Messages.GherkinDocument.Feature.FeatureChild.RuleChild> ruleChildren = rule.getChildrenList();
			for (Messages.GherkinDocument.Feature.FeatureChild.RuleChild child : ruleChildren) {
				if (child.hasScenario()) {
					Messages.GherkinDocument.Feature.Scenario result = getScenario(child.getScenario(), testCase);
					if (result != null) {
						return result;
					}
				}
			}
			return null;
		}

		public void setCurrentRule(RuleContext rule) {
			this.rule = rule;
		}

		public RuleContext getCurrentRule() {
			return rule;
		}
	}

	public static class ScenarioContext {
		private static final Map<Messages.GherkinDocument.Feature.Scenario, List<Integer>> scenarioOutlineMap = new ConcurrentHashMap<>();

		private final Queue<Messages.GherkinDocument.Feature.Step> backgroundSteps = new ArrayDeque<>();
		private final Map<Integer, Messages.GherkinDocument.Feature.Step> scenarioLocationMap = new HashMap<>();
		private Set<ItemAttributesRQ> attributes = new HashSet<>();
		private Maybe<String> currentStepId;
		private Maybe<String> hookStepId;
		private Status hookStatus;
		private Maybe<String> id;
		private Messages.GherkinDocument.Feature.Background background;
		private Messages.GherkinDocument.Feature.Scenario scenario;
		private TestCase testCase;
		private boolean hasBackground = false;
		private String outlineIteration;
		private URI uri;
		private String text;
		private RuleContext rule;

		public void processScenario(Messages.GherkinDocument.Feature.Scenario scenario) {
			this.scenario = scenario;
			for (Messages.GherkinDocument.Feature.Step step : scenario.getStepsList()) {
				scenarioLocationMap.put(step.getLocation().getLine(), step);
			}
		}

		public void processBackground(Messages.GherkinDocument.Feature.Background background) {
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
		public void processScenarioOutline(Messages.GherkinDocument.Feature.Scenario scenarioOutline) {
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
								Utils.getCodeRef(uri, getLine())
						)));
				outlineIteration = String.format("[%d]", iterationIdx + 1);
			}
		}

		public void processTags(List<String> tags) {
			attributes = Utils.extractAttributes(tags);
		}

		public void mapBackgroundSteps(Messages.GherkinDocument.Feature.Background background) {
			for (Messages.GherkinDocument.Feature.Step step : background.getStepsList()) {
				scenarioLocationMap.put(step.getLocation().getLine(), step);
			}
		}

		public String getName() {
			return scenario.getName();
		}

		public String getKeyword() {
			return scenario.getKeyword();
		}

		public int getLine() {
			return isScenarioOutline(scenario) ? testCase.getLocation().getLine() : scenario.getLocation().getLine();
		}

		public String getStepPrefix() {
			return hasBackground() && withBackground() ? background.getKeyword().toUpperCase() + AbstractReporter.COLON_INFIX : "";
		}

		public Messages.GherkinDocument.Feature.Step getStep(TestStep testStep) {
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

		public Maybe<String> getId() {
			return id;
		}

		public void setId(Maybe<String> newId) {
			if (id != null) {
				throw new IllegalStateException("Attempting re-set scenario ID for unfinished scenario: " + getName());
			}
			id = newId;
		}

		public void setTestCase(TestCase testCase) {
			this.testCase = testCase;
		}

		public void nextBackgroundStep() {
			backgroundSteps.poll();
		}

		public boolean isScenarioOutline(Messages.GherkinDocument.Feature.Scenario scenario) {
			return scenario.getExamplesCount() > 0;
		}

		public boolean withBackground() {
			return !backgroundSteps.isEmpty();
		}

		public boolean hasBackground() {
			return hasBackground && background != null;
		}

		public String getOutlineIteration() {
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

		public void setFeatureUri(URI featureUri) {
			this.uri = featureUri;
		}

		public URI getFeatureUri() {
			return uri;
		}

		public void setCurrentText(String stepText) {
			this.text = stepText;
		}

		public String getCurrentText() {
			return text;
		}

		public void setRule(RuleContext rule) {
			this.rule = rule;
		}

		public RuleContext getRule() {
			return rule;
		}
	}

	public static class RuleContext {
		private final String name;
		private final String description;
		private final String keyword;
		private final Set<ItemAttributesRQ> attributes;
		private final int line;
		private final URI uri;
		private Maybe<String> id;

		public RuleContext(@Nonnull URI featureUri, @Nonnull Messages.GherkinDocument.Feature.FeatureChild.Rule rule,
				@Nonnull TestCase testCase) {
			name = rule.getName();
			description = rule.getDescription();
			keyword = rule.getKeyword();
			attributes = Utils.extractAttributes(testCase.getTags());
			line = rule.getLocation().getLine();
			uri = featureUri;
		}

		@Nonnull
		public String getName() {
			return name;
		}

		public String getDescription() {
			return description;
		}

		@Nonnull
		public String getKeyword() {
			return keyword;
		}

		public Set<ItemAttributesRQ> getAttributes() {
			return attributes;
		}

		public int getLine() {
			return line;
		}

		@Nonnull
		public URI getUri() {
			return uri;
		}

		public Maybe<String> getId() {
			return id;
		}

		public void setId(Maybe<String> id) {
			this.id = id;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof RuleContext)) {
				return false;
			}
			RuleContext that = (RuleContext) o;
			return line == that.line && uri.equals(that.uri);
		}

		@Override
		public int hashCode() {
			return Objects.hash(line, uri);
		}
	}
}
