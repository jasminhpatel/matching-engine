package com.solfini.util;

import java.util.Properties;
import org.junit.Assert;
import org.junit.Test;

public class PropertyReaderTest {

	private static final String PROPERTIES_PATH = "/PropertyReaderTest.properties";

	@Test
	public void getProperty() {
		// load
		try {
			PropertyReader.initialize(getClass().getResourceAsStream(PROPERTIES_PATH), null);
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}

		// get string values
		Assert.assertEquals("me01", PropertyReader.getProperty("INSTANCE", null));
		Assert.assertEquals("primary", PropertyReader.getProperty("TYPE", null));
		Assert.assertEquals(null, PropertyReader.getProperty("NAME", null));
		Assert.assertEquals("DEFAULT", PropertyReader.getProperty("NAME", "DEFAULT"));

		// get int values
		Assert.assertEquals(1, PropertyReader.getProperty("MODE", 0));
		Assert.assertEquals(100, PropertyReader.getProperty("CAPACITY", 100));

		// property group
		Properties group = PropertyReader.getPropertyGroup("KAFKA.CONSUMER");
		Assert.assertNotNull(group);
		Assert.assertEquals("3.209.193.87:4455", group.getProperty("bootstrap.servers"));
		Assert.assertEquals("test5", group.getProperty("group.id"));
		Assert.assertEquals("true", group.getProperty("enable.auto.commit"));
	}

	@Test
	public void getPropertyOverride() {
		Properties overlay = new Properties() {{
			setProperty("TYPE", "secondary");
			setProperty("CAPACITY", "500");
			setProperty("KAFKA.CONSUMER.enable.auto.commit", "false");
		}};

		// load
		try {
			PropertyReader.initialize(getClass().getResourceAsStream(PROPERTIES_PATH), overlay);
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}

		// get string values
		Assert.assertEquals("me01", PropertyReader.getProperty("INSTANCE", null));
		Assert.assertEquals("secondary", PropertyReader.getProperty("TYPE", null));
		Assert.assertEquals(null, PropertyReader.getProperty("NAME", null));
		Assert.assertEquals("DEFAULT", PropertyReader.getProperty("NAME", "DEFAULT"));

		// get int values
		Assert.assertEquals(1, PropertyReader.getProperty("MODE", 0));
		Assert.assertEquals(500, PropertyReader.getProperty("CAPACITY", 100));

		// property group
		Properties group = PropertyReader.getPropertyGroup("KAFKA.CONSUMER");
		Assert.assertNotNull(group);
		Assert.assertEquals("3.209.193.87:4455", group.getProperty("bootstrap.servers"));
		Assert.assertEquals("test5", group.getProperty("group.id"));
		Assert.assertEquals("false", group.getProperty("enable.auto.commit"));
	}
}
