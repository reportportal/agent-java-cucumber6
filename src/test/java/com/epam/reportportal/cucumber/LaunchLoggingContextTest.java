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

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class LaunchLoggingContextTest {

	@Mock
	private ReportPortal reportPortal;

	@Mock
	private ListenerParameters listenerParameters;

	@Mock
	private Launch launch;

	@Test
	public void verifyLaunchLoggingContextInit() {
		StepReporter stepReporter = new StepReporter() {
			@Override
			protected ReportPortal buildReportPortal() {
				return reportPortal;
			}
		};

		when(reportPortal.getParameters()).thenReturn(listenerParameters);
		when(reportPortal.newLaunch(any())).thenReturn(launch);
		stepReporter.beforeLaunch();

		verify(launch, times(1)).start();
	}

	@Test
	public void verifyLaunchLoggingContextInitScenarioReporter() {
		ScenarioReporter scenarioReporter = new ScenarioReporter() {
			@Override
			protected ReportPortal buildReportPortal() {
				return reportPortal;
			}
		};

		when(reportPortal.getParameters()).thenReturn(listenerParameters);
		when(reportPortal.newLaunch(any())).thenReturn(launch);
		scenarioReporter.beforeLaunch();

		verify(launch, times(1)).start();
	}
}