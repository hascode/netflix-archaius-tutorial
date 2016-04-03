package com.hascode.tutorial;

import java.io.IOException;
import java.util.Properties;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServerMain;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;

import com.netflix.config.ConcurrentCompositeConfiguration;
import com.netflix.config.ConcurrentMapConfiguration;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.config.DynamicWatchedConfiguration;
import com.netflix.config.source.ZooKeeperConfigurationSource;

public class App {

	public static void main(String[] args) throws Exception {
		startZookeeperForDemonstration();

		// enable JMX dynamic configuration
		System.setProperty(DynamicPropertyFactory.ENABLE_JMX, "true");

		// enable config from properties file
		ConcurrentMapConfiguration configFile = new ConcurrentMapConfiguration(
				new PropertiesConfiguration("config.properties"));

		String zkConfigRootPath = "/[my-app]/config";

		CuratorFramework client = CuratorFrameworkFactory.newClient("", new ExponentialBackoffRetry(1000, 3));

		ZooKeeperConfigurationSource zkConfigSource = new ZooKeeperConfigurationSource(client, zkConfigRootPath);
		zkConfigSource.start();

		DynamicWatchedConfiguration zkDynamicConfig = new DynamicWatchedConfiguration(zkConfigSource);

		// composite configuration
		ConcurrentCompositeConfiguration config = new ConcurrentCompositeConfiguration();
		config.addConfiguration(configFile);
		config.addConfiguration(zkDynamicConfig);
		ConfigurationManager.install(config);

		DynamicStringProperty prop = DynamicPropertyFactory.getInstance().getStringProperty("search.url",
				"https://www.google.com/#q=");

		prop.addCallback(() -> System.out.println("search url has changed to " + prop.get()));

		System.out.println("search url is: " + prop.get());

		for (;;) {
		}
	}

	private static void startZookeeperForDemonstration() {
		Properties zkConfig = new Properties();
		zkConfig.setProperty("tickTime", "2000");
		zkConfig.setProperty("dataDir", "/tmp/zookeeper");
		zkConfig.setProperty("clientPort", "2181");

		QuorumPeerConfig quorumConfiguration = new QuorumPeerConfig();
		try {
			quorumConfiguration.parseProperties(zkConfig);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		ZooKeeperServerMain zooKeeperServer = new ZooKeeperServerMain();
		final ServerConfig configuration = new ServerConfig();
		configuration.readFrom(quorumConfiguration);

		new Thread() {
			@Override
			public void run() {
				try {
					zooKeeperServer.runFromConfig(configuration);
				} catch (IOException e) {
					System.err.println("zookeeper failed " + e.getMessage());
				}
			}
		}.start();
	}

}
