/*
 * Copyright 2025 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.cucumber;

import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class CachedStep {
	private final StartTestItemRQ startTestItemRQ;
	private final List<CachedLog> logs;
	private FinishTestItemRQ finishTestItemRQ;

	public CachedStep(@Nonnull StartTestItemRQ startTestItemRQ) {
		this.startTestItemRQ = startTestItemRQ;
		logs = new ArrayList<>();
	}

	@Nonnull
	public StartTestItemRQ getStartTestItemRQ() {
		return startTestItemRQ;
	}

	public void addLog(@Nonnull CachedLog log) {
		logs.add(log);
	}

	@Nonnull
	public List<CachedLog> getLogs() {
		return new ArrayList<>(logs);
	}

	@Nullable
	public FinishTestItemRQ getFinishTestItemRQ() {
		return finishTestItemRQ;
	}

	public void setFinishTestItemRQ(@Nullable FinishTestItemRQ finishTestItemRQ) {
		this.finishTestItemRQ = finishTestItemRQ;
	}
}
