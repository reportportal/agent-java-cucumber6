package com.epam.reportportal.cucumber.integration.feature;

import com.epam.reportportal.annotations.ParameterKey;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportsTestWithParameters {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReportsTestWithParameters.class);

	@Given("It is test with parameters")
	public void infoLevel() {
		LOGGER.info("It is test with parameters");
	}

	@When("I have parameter {string}")
	public void iHaveParameterStr(String str) {
		LOGGER.info("String parameter {}", str);
	}

	@When("I have a docstring parameter:")
	public void iHaveParameterDocstring(String str) {
		iHaveParameterStr(str);
	}

	@Then("I emit number {int} on level info")
	public void infoLevel(int parameters) {
		LOGGER.info("Test with parameters: " + parameters);
	}

	@Given("It is a step with an integer parameter {int}")
	public void iHaveAnIntInlineParameter(int parameter) {
		LOGGER.info("Integer parameter: " + parameter);
	}

	@When("I have a step with a string parameter {string}")
	public void iHaveAnStrInlineParameter(String str) {
		LOGGER.info("String parameter {}", str);
	}

	@When("I have a step with a named string parameter {string}")
	public void iHaveANamedStrInlineParameter(@ParameterKey("my name") String str) {
		LOGGER.info("String parameter {}", str);
	}
}
