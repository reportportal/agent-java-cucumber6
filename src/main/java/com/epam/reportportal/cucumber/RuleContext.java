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
import io.reactivex.Maybe;
import jakarta.annotation.Nonnull;

import java.net.URI;

public class RuleContext {

	private final int line;
	private final URI uri;
	private final Node.Rule rule;

	private Maybe<String> id = Maybe.empty();

	public RuleContext(@Nonnull URI ruleFilePath, @Nonnull Node.Rule ruleNode) {
		uri = ruleFilePath;
		rule = ruleNode;
		line = ruleNode.getLocation().getLine();
	}

	@Nonnull
	public Maybe<String> getId() {
		return id;
	}

	public void setId(@Nonnull Maybe<String> id) {
		this.id = id;
	}

	public int getLine() {
		return line;
	}

	@Nonnull
	public Node.Rule getRule() {
		return rule;
	}

	@SuppressWarnings("unused")
	public URI getUri() {
		return uri;
	}
}
