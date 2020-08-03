package com.epam.reportportal.cucumber;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
@RunWith(MockitoJUnitRunner.class)
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