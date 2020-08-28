package com.epam.reportportal.cucumber.integration.hooks;

import io.cucumber.java.After;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeStep;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmptySteps {
	private static final Logger LOGGER = LoggerFactory.getLogger(EmptySteps.class);

	@Before
	public void my_before_hook() {
		LOGGER.info("Inside 'my_before_hook'");
	}

	@BeforeStep
	public void my_before_step_hook() {
		LOGGER.info("Inside 'my_before_step_hook'");
	}

	@AfterStep
	public void my_after_step_hook() {
		LOGGER.info("Inside 'my_after_step_hook'");
	}

	@Given("I have empty step")
	public void i_have_empty_step() {
		LOGGER.info("Inside 'I have empty step'");
	}

	@Then("I have another empty step")
	public void i_have_another_empty_step() {
		LOGGER.info("Inside 'I have another empty step'");
	}

	@After
	public void my_after_hook() {
		LOGGER.info("Inside 'my_after_hook'");
	}
}
