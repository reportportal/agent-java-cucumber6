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

package com.epam.reportportal.cucumber.integration.feature;

import com.epam.reportportal.annotations.Step;
import com.epam.reportportal.annotations.attribute.Attribute;
import com.epam.reportportal.annotations.attribute.AttributeValue;
import com.epam.reportportal.annotations.attribute.Attributes;
import com.epam.reportportal.util.test.CommonUtils;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NestedSteps {
	private static final Logger LOGGER = LoggerFactory.getLogger(NestedSteps.class);

	public static final long PARAM1 = 7L;

	public static final String PARAM2 = "second param";

	@Given("I have a step")
	public void i_have_empty_step() throws InterruptedException {
		LOGGER.info("Inside 'I have a step'");
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
		and_a_step_inside_step();
	}

	@Step("A step inside step")
	public void and_a_step_inside_step() throws InterruptedException {
		LOGGER.info("Inside 'and_a_step_inside_nested_step'");
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
		and_a_step_inside_nested_step();
	}

	@Step("A step inside nested step")
	public void and_a_step_inside_nested_step() {
		LOGGER.info("Inside 'and_a_step_inside_nested_step'");
	}

	@When("I have one more step")
	public void i_have_one_more_step() throws InterruptedException {
		LOGGER.info("Inside 'I have one more step'");
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
		with_a_step_with_parameters(PARAM1, PARAM2);
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
		with_a_step_with_attributes();
	}

	@Step("A step with parameters")
	public void with_a_step_with_parameters(long one, String two) throws InterruptedException {
		LOGGER.info("Inside 'with_a_step_with_parameters': '" + one + "'; '" + two + "'");
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
	}

	@Step("A step with attributes")
	@Attributes(attributes = @Attribute(key = "key", value = "value"), attributeValues = @AttributeValue("tag"))
	public void with_a_step_with_attributes() throws InterruptedException {
		LOGGER.info("Inside 'with_a_step_with_attributes'");
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
	}
}
