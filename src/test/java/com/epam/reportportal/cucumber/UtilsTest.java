package com.epam.reportportal.cucumber;

import com.epam.ta.reportportal.ws.model.ParameterResource;
import io.cucumber.plugin.event.Argument;
import io.cucumber.plugin.event.Group;
import org.junit.Test;
import rp.com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class UtilsTest {

	@Test
	public void retrieveParamsFromTestWithoutParamsTest() {
		List<ParameterResource> parameters = Utils.getParameters(Collections.emptyList(), "Test without params");
		assertNotNull(parameters);
		assertTrue(parameters.isEmpty());
	}

	@Test
	public void retrieveSingleParameterTest() {
		String parameterName = "parameter";
		String parameterValue = "\"value\"";
		List<ParameterResource> parameters = Utils.getParameters(Lists.newArrayList(getTestArgument("\"" + parameterValue + "\"")),
				String.format("Test with <%s>", parameterName)
		);
		assertNotNull(parameters);
		assertEquals(1, parameters.size());
		parameters.forEach(it -> {
			assertEquals(parameterName, it.getKey());
			assertEquals(parameterValue, it.getValue());
		});
	}

	@Test
	public void retrieveParametersTest() {
		List<String> parameterNames = Lists.newArrayList("String", "int");
		List<String> parameterValues = Lists.newArrayList("val1", "val2");
		List<ParameterResource> parameters = Utils.getParameters(parameterValues.stream()
						.map(p -> getTestArgument("\"" + p + "\""))
						.collect(Collectors.toList()),
				String.format("Test with %s", "<" + String.join("> <", parameterNames) + ">")
		);
		assertNotNull(parameters);
		assertEquals(2, parameters.size());
		IntStream.range(0, parameters.size()).forEach(index -> {
			assertEquals(parameterNames.get(index), parameters.get(index).getKey());
			assertEquals(parameterValues.get(index), parameters.get(index).getValue());
		});
	}

	private static Argument getTestArgument(String value) {
		return new Argument() {
			@Override
			public String getParameterTypeName() {
				return "string";
			}

			@Override
			public String getValue() {
				return value;
			}

			@Override
			public int getStart() {
				return 0;
			}

			@Override
			public int getEnd() {
				return 0;
			}

			@Override
			public Group getGroup() {
				return null;
			}
		};
	}
}