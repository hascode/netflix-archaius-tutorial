package com.hascode.tutorial;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.SystemConfiguration;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryOneTime;

import com.google.common.io.Files;
import com.netflix.config.ConcurrentCompositeConfiguration;
import com.netflix.config.ConcurrentMapConfiguration;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.config.DynamicWatchedConfiguration;
import com.netflix.config.source.ZooKeeperConfigurationSource;
import com.netflix.curator.test.TestingServer;

public class App {

	public static void main(String[] args) throws Exception {
		try (TestingServer zkTestServer = new TestingServer(2181, Files.createTempDir());
				CuratorFramework testClient = CuratorFrameworkFactory.newClient(zkTestServer.getConnectString(),
						new RetryOneTime(2000))) {
			testClient.start();
			testClient.create().forPath("/config");
			testClient.create().forPath("/config/search.url", "http://www.test.de".getBytes());

			// enable JMX dynamic configuration
			System.setProperty(DynamicPropertyFactory.ENABLE_JMX, "true");

			// enable config from properties file
			ConcurrentMapConfiguration configFile = new ConcurrentMapConfiguration(
					new PropertiesConfiguration("config.properties"));

			// enable configuration from system properties
			ConcurrentMapConfiguration systemProps = new ConcurrentMapConfiguration(new SystemConfiguration());

			CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:2181",
					new ExponentialBackoffRetry(1000, 3));
			client.start();

			ZooKeeperConfigurationSource zkConfigSource = new ZooKeeperConfigurationSource(client, "/config");
			zkConfigSource.start();
			System.out.println("value in zookeeper: " + zkConfigSource.getCurrentData().get("search.url"));
			DynamicWatchedConfiguration zkDynamicConfig = new DynamicWatchedConfiguration(zkConfigSource);

			// composite configuration
			ConcurrentCompositeConfiguration config = new ConcurrentCompositeConfiguration();
			config.addConfiguration(zkDynamicConfig);
			config.addConfiguration(configFile);
			config.addConfiguration(systemProps);
			ConfigurationManager.install(config);

			DynamicStringProperty prop = DynamicPropertyFactory.getInstance().getStringProperty("search.url",
					"https://www.google.com/#q=");

			prop.addCallback(() -> System.out.println("search url has changed to " + prop.get()));

			System.out.println("search url is: " + prop.get());

			testClient.setData().forPath("/config/search.url", "http://www.foo.de".getBytes());

			System.console().readLine("press enter to shutdown application\n");

			client.close();
		}
	}

}
