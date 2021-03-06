package com.github.bordertech.config;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.commons.lang.StringUtils;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * The Config class is the central access point to the configuration mechanism, and is used to read or modify the
 * current configuration.
 *
 * <p>
 * The library is configured using the Apache Configuration API. This allows developers to programmatically integrate
 * the configuration with whatever mechanism is used to configure their applications.
 * <b>Note:</b>To prevent against accidental modifications, the default configuration is read-only, and any attempt to
 * modify it will result in a runtime exception.</p>
 * <p>
 * The default configuration can be overridden by setting properties in a file
 * <code>bordertech-config.properties</code>. Refer to {@link InitHelper} for property details.
 * </p>
 * <p>
 * The default resources Config looks for are:-
 * </p>
 * <ul>
 * <li><code>bordertech-defaults.properties</code> - framework defaults</li>
 * <li><code>bordertech-app.properties</code> - application properties</li>
 * <li><code>bordertech-local.properties</code> - local developer properties</li>
 * </ul>
 *
 * <p>
 * A touchfile can be set via the parameter <code>bordertech.config.touchfile</code>. The touchfile is checked when
 * {@link #getInstance()} is called. To avoid excessive IO an interval (in milli seconds) between checks can be set via
 * <code>bordertech.config.touchfile.interval</code> and defaults to <code>10000</code>.
 * </p>
 *
 * @author Joshua Barclay
 * @author Jonathan Austin
 * @since 1.0.0
 *
 * @see DefaultConfiguration
 * @see InitHelper
 * @see ConfigurationLoader
 */
public final class Config {

	/**
	 * Used to lock the config.
	 */
	private static final Object LOCK = new Object();

	/**
	 * Contains the complete set of property change listeners that have registered with this class.
	 */
	private static final Set<PropertyChangeListener> PROPERTY_CHANGE_LISTENERS = new HashSet<>();

	/**
	 * The current configuration.
	 */
	private static Configuration configuration;

	/**
	 * Touchfile (if configured).
	 */
	private static Touchfile touchfile;

	static {
		loadConfiguration();
	}

	/**
	 * Prevent instantiation of this utility class.
	 */
	private Config() {
	}

	/**
	 * @return the current configuration.
	 */
	public static Configuration getInstance() {
		// If a touchfile has been set, check if it has changed and reload if necessary
		if (touchfile != null) {
			synchronized (LOCK) {
				if (touchfile.hasChanged()) {
					loadConfiguration();
				}
			}
		}
		return configuration;
	}

	/**
	 * Resets the configuration back to the default internal configuration. All configuration changes which have been
	 * made will be lost. This method is primarily intended for unit testing.
	 */
	public static void reset() {
		synchronized (LOCK) {
			loadConfiguration();
		}
	}

	/**
	 * <p>
	 * Sets the current configuration.</p>
	 * <p>
	 * <b>Warning: </b> this will ignore any defined ConfigurationLoaders</p>
	 *
	 * @param configuration the configuration to set.
	 */
	public static void setConfiguration(final Configuration configuration) {
		synchronized (LOCK) {
			Config.configuration = configuration;
			configTouchfile();
			notifyListeners();
		}
	}

	/**
	 * Creates a deep-copy of the given configuration. This is useful for unit-testing.
	 *
	 * @param original the configuration to copy.
	 * @return a copy of the given configuration.
	 */
	public static Configuration copyConfiguration(final Configuration original) {
		Configuration copy = new MapConfiguration(new HashMap<>());

		for (Iterator<?> i = original.getKeys(); i.hasNext();) {
			String key = (String) i.next();
			Object value = original.getProperty(key);

			if (value instanceof List) {
				value = new ArrayList((List) value);
			}

			copy.setProperty(key, value);
		}
		return copy;
	}

	/**
	 * This method notifies all the {@link PropertyChangeListener}s that have registered with this object that a change
	 * has occurred.
	 */
	public static void notifyListeners() {
		// The trivial case is when there are no listeners.
		if (PROPERTY_CHANGE_LISTENERS.isEmpty()) {
			return;
		}

		// A possible improvement here would be to verify that one or more of the properties has actually changed due to
		// the refresh.
		//
		// If we get here, then there is at least one listener who wants to be notified when a refresh occurs. The first
		// step in this notification process is to create the PropertyChangeEvent object that will be sent to each of
		// the registered PropertyChangeListeners.
		// <code>PropertyChangeEvent event = new PropertyChangeEvent(this, "propertiesRefresh", null, null);</code>
		//
		// Finally, iterate through the complete set of PropertyChangeListeners and notify them that a change has
		// occurred.
		for (PropertyChangeListener listener : PROPERTY_CHANGE_LISTENERS) {
			listener.propertyChange(null);
		}
	}

	/**
	 * Registers a property change listener to receive notifications of configuration changes. Note that the listener
	 * will only be notified when this class knows that the configuration has changed. No notification is sent if a
	 * {@link Configuration} changes its values internally. In this case, a manual call to {@link #notifyListeners()}
	 * must be made to notify the listeners.
	 *
	 * @param listener the listener to add.
	 */
	public static void addPropertyChangeListener(final PropertyChangeListener listener) {
		PROPERTY_CHANGE_LISTENERS.add(listener);
	}

	/**
	 * Load the configuration.
	 */
	private static void loadConfiguration() {
		Configuration config = checkSPIConfiguration();
		if (config == null) {
			config = getDefaultConfiguration();
		}
		Config.configuration = config;
		configTouchfile();
		notifyListeners();
	}

	/**
	 * Configure the touchfile (if provided).
	 */
	private static void configTouchfile() {
		String file = getTouchFileName();
		if (StringUtils.isEmpty(file)) {
			touchfile = null;
		} else {
			touchfile = new Touchfile(file, getTouchFileInterval());
		}
	}

	/**
	 * @return the touch file name
	 */
	private static String getTouchFileName() {
		return configuration.getString("bordertech.config.touchfile");
	}

	/**
	 * @return the touch file interval (in milli seconds)
	 */
	private static long getTouchFileInterval() {
		return configuration.getLong("bordertech.config.touchfile.interval", 10000);
	}

	/**
	 * @return a SLI Configuration or null if none available
	 */
	private static Configuration checkSPIConfiguration() {

		if (!InitHelper.SPI_ENABLED) {
			return null;
		}

		// Find if there are classes implementing the ConfigurationLoader SPI.
		ServiceLoader<ConfigurationLoader> loaders = ServiceLoader.load(ConfigurationLoader.class);
		Iterator<ConfigurationLoader> iterator = loaders.iterator();

		// Use a CompositeConfiguration if there are custom ConfigurationLoader implementations.
		if (iterator.hasNext()) {
			CompositeConfiguration compositeConfig = new CompositeConfiguration(new MapConfiguration(new HashMap<>()));
			while (iterator.hasNext()) {
				compositeConfig.addConfiguration(iterator.next().getConfiguration());
			}

			//Add the base configuration last so it is overridden.
			if (InitHelper.SPI_APPEND_DEFAULT_CONFIG) {
				compositeConfig.addConfiguration(getDefaultConfiguration());
			}
			return compositeConfig;
		}
		return null;
	}

	/**
	 * @return the default configuration
	 */
	private static Configuration getDefaultConfiguration() {
		// Create Instance
		try {
			Class<Configuration> clazz = (Class<Configuration>) Class.forName(InitHelper.DEFAULT_CONFIG_IMPL.trim());
			return clazz.newInstance();
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
			throw new IllegalStateException("Failed to instantiate override default config of class " + InitHelper.DEFAULT_CONFIG_IMPL, e);
		}
	}

}
