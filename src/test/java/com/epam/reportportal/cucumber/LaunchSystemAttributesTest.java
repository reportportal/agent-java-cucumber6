package com.epam.reportportal.cucumber;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import io.reactivex.Maybe;
import io.reactivex.MaybeEmitter;
import io.reactivex.MaybeOnSubscribe;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rp.com.google.common.collect.Lists;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
public class LaunchSystemAttributesTest {

	private static final Map<String, Pattern> properties = new HashMap<String, Pattern>();

	private static final String SKIPPED_ISSUE_KEY = "skippedIssue";

	private StepReporter stepReporter;

	@Mock
	private ReportPortalClient reportPortalClient;

	@Mock
	private ListenerParameters listenerParameters;

	@BeforeClass
	public static void initKeys() {
		properties.put("os", Pattern.compile("^.+\\|.+\\|.+$"));
		properties.put("jvm", Pattern.compile("^.+\\|.+\\|.+$"));
		properties.put("agent", Pattern.compile("^test-agent\\|test-1\\.0$"));
	}

	@Before
	public void initLaunch() {
		MockitoAnnotations.initMocks(this);
		when(listenerParameters.getEnable()).thenReturn(true);
		when(listenerParameters.getBaseUrl()).thenReturn("http://example.com");
		when(listenerParameters.getIoPoolSize()).thenReturn(10);
		when(listenerParameters.getBatchLogsSize()).thenReturn(5);
		stepReporter = new StepReporter() {
			@Override
			protected ReportPortal buildReportPortal() {
				return ReportPortal.create(reportPortalClient, listenerParameters);
			}
		};
	}

	@Test
	public void shouldRetrieveSystemAttributes() {
		when(reportPortalClient.startLaunch(any(StartLaunchRQ.class))).then(new Answer<Object>() {
			@Override
			public Object answer(InvocationOnMock t) throws Throwable {
				return Maybe.create(new MaybeOnSubscribe<Object>() {
					@Override
					public void subscribe(MaybeEmitter<Object> emitter) throws Exception {
						emitter.onSuccess("launchId");
						emitter.onComplete();
					}
				}).cache();
			}
		});

		stepReporter.beforeLaunch();

		stepReporter.launch.get().start().blockingGet();

		ArgumentCaptor<StartLaunchRQ> launchRQArgumentCaptor = ArgumentCaptor.forClass(StartLaunchRQ.class);
		verify(reportPortalClient, times(1)).startLaunch(launchRQArgumentCaptor.capture());

		StartLaunchRQ startLaunchRequest = launchRQArgumentCaptor.getValue();

		Assert.assertNotNull(startLaunchRequest.getAttributes());

		List<ItemAttributesRQ> attributes = Lists.newArrayList(startLaunchRequest.getAttributes());

		for (int index = 0; index < attributes.size(); index++) {
			if (SKIPPED_ISSUE_KEY.equals(attributes.get(index).getKey())) {
				attributes.remove(attributes.get(index));
			}
		}

		Assert.assertEquals(3, attributes.size());

		for (ItemAttributesRQ attribute : attributes) {
			Assert.assertTrue(attribute.isSystem());

			Pattern pattern = LaunchSystemAttributesTest.this.getPattern(attribute);
			Assert.assertNotNull(pattern);
			Assert.assertTrue(pattern.matcher(attribute.getValue()).matches());
		}

	}

	private Pattern getPattern(ItemAttributesRQ attribute) {
		return properties.get(attribute.getKey());

	}

}
