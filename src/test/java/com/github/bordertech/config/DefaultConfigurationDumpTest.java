package com.github.bordertech.config;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * DefaultConfigurationDumpTest - JUnit tests for {@link DefaultConfiguration}.
 */
public class DefaultConfigurationDumpTest {

	private PrintStream original;
	private ByteArrayOutputStream systemErr;

	private static final String LOG_START = "Properties loaded start";
	private static final String LOG_END = "Properties loaded end";
	private static final String LOG_PARAM = "simple.param1";

	@Before
	public void redirectSystemOutStream() {
		original = System.err;
		systemErr = new ByteArrayOutputStream();
		System.setErr(new PrintStream(systemErr));
	}

	@After
	public void restoreSystemOutStream() {
		System.setErr(original);
	}

	@Test
	public void dumpParametersEnabled() {
		DefaultConfiguration config = new DefaultConfiguration(
				"com/github/bordertech/config/DefaultConfigurationTestDumpEnabled.properties");
		// Check dump enabled
		Assert.assertTrue(config.getBoolean(DefaultConfiguration.DUMP));
		String log = systemErr.toString();
		Assert.assertTrue("Output should contain dump start load", log.contains(LOG_START));
		Assert.assertTrue("Output should contain dump start end", log.contains(LOG_END));
		Assert.assertTrue("Output should contain property", log.contains(LOG_PARAM));
	}

	@Test
	public void dumpParametersDisabled() {
		DefaultConfiguration config = new DefaultConfiguration(
				"com/github/bordertech/config/DefaultConfigurationTestDumpDisabled.properties");
		// Check dump disabled
		Assert.assertFalse(config.getBoolean(DefaultConfiguration.DUMP));
		String log = systemErr.toString();
		Assert.assertFalse("Output should not contain dump start load", log.contains(LOG_START));
		Assert.assertFalse("Output should not contain dump start end", log.contains(LOG_END));
		Assert.assertFalse("Output should not contain property", log.contains(LOG_PARAM));
	}

}
