package com.solfini.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The PropertyReader class allows program code to read configuration properties. These properties come from a specified configuration file
 * and may be overridden by command line arguments.
 */
public class PropertyReader {

  private static Properties properties = new Properties();

  private PropertyReader() {
    // hidden default constructor
  }

  /**
   * Initialize the PropertyReader static class.
   *
   * @param inputStream Input stream to read for loading properties.
   * @param overlay Peroperties to overlay on top of loaded properties.
   * @throws FileNotFoundException Thrown if the specified property file is not found.
   * @throws IOException Throws if the property file loading failed.
   */
  public static void initialize(final InputStream stream, final Properties overlay) throws IOException {
    properties = new Properties();

    if (null != stream) {
      properties.load(stream);
    }

    if (null != overlay) {
      for (final String key : overlay.stringPropertyNames()) {
        properties.setProperty(key, overlay.getProperty(key));
      }
    }

  }

  /**
   * Get the value of a property.
   *
   * @param key Property name.
   * @param defaultValue Default value to return if the property is not defined.
   * @return Value of the property as a string.
   */
  public static final String getProperty(final String key, final String defaultValue) {
    return properties.getProperty(key, defaultValue);
  }

  /**
   * Get the value of a property.
   *
   * @param key Property name.
   * @param defaultValue Default value to return if the property is not defined.
   * @return Value of the property as an integer.
   */
  public static final int getProperty(final String key, final int defaultValue) {
    final String value = properties.getProperty(key);
    if (null != value) {
      try {
        return Integer.parseInt(value);
      } catch (Exception e) {
        return defaultValue;
      }
    }

    return defaultValue;
  }

  /**
   * Get the value of a property.
   *
   * @param key Property name.
   * @param defaultValue Default value to return if the property is not defined.
   * @return Value of the property as an integer.
   */
  public static final double getProperty(final String key, final double defaultValue) {
    final String value = properties.getProperty(key);
    if (null != value) {
      try {
        return Double.parseDouble(value);
      } catch (Exception e) {
        return defaultValue;
      }
    }

    return defaultValue;
  }

  public static Properties getPropertyGroup(final String group) {
    final Properties result = new Properties();
    final String prefix = group + ".";
    for (final String key : properties.stringPropertyNames()) {
      if (key.startsWith(prefix)) {
        result.setProperty(key.substring(prefix.length()), properties.getProperty(key));
      }
    }

    return result;
  }
}
