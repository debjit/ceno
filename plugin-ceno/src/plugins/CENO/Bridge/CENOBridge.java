package plugins.CENO.Bridge;

import freenet.keys.FreenetURI;
import freenet.pluginmanager.*;
import freenet.support.Logger;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;

import plugins.CENO.Configuration;
import plugins.CENO.Version;
import plugins.CENO.FreenetInterface.HighLevelSimpleClientInterface;
import plugins.CENO.FreenetInterface.NodeInterface;


public class CENOBridge implements FredPlugin, FredPluginVersioned, FredPluginRealVersioned {

	private PluginRespirator pluginRespirator;

	public static final Integer cacheLookupPort = 3091;
	public static final Integer requestReceiverPort = 3093;
	public static final Integer bundleServerPort = 3094;
	public static final Integer bundleInserterPort = 3095;

	/** The HTTP Server to handle requests from other agents */
	private Server cenoHttpServer;

	// Interface objects with fred
	private HighLevelSimpleClientInterface client;
	public static NodeInterface nodeInterface;
	private RequestReceiver reqReceiver;

	// Plugin-specific configuration
	public static final String pluginUri = "/plugins/plugins.CENO.CENOBridge";
	public static final String pluginName = "CENOBridge";
	public static Configuration initConfig;
	private Version version = new Version(Version.PluginType.BRIDGE);
	private static final String configPath = System.getProperty("user.home") + "/.CENO/bridge.properties";

	public static final String bridgeFreemail = "DEFLECTBridge@ih5ixq57yetjdbrspbtskdp6fjake4rdacziriiefnjkwlvhgw3a.freemail";
	public static final String clientFreemail = "CENO@54u2ko3lssqgalpvfqbq44gwfquqrejm3itl4rxj5nt7v6mjy22q.freemail";


	public void runPlugin(PluginRespirator pr)
	{
		// Initialize interfaces with fred
		pluginRespirator = pr;
		client = new HighLevelSimpleClientInterface(pluginRespirator.getHLSimpleClient());
		nodeInterface = new NodeInterface(pluginRespirator.getNode(), pluginRespirator);

		// Read properties of the configuration file
		initConfig = new Configuration(configPath);
		initConfig.readProperties();
		// If CENO has no private key for inserting freesites,
		// generate a new key pair and store it in the configuration file
		if (initConfig.getProperty("insertURI") == null) {
			Logger.warning(this, "CENOBridge will generate a new public key for inserting bundles.");
			FreenetURI[] keyPair = nodeInterface.generateKeyPair();
			initConfig.setProperty("insertURI", keyPair[0].toString());
			initConfig.setProperty("requestURI", keyPair[1].toString());
			initConfig.storeProperties();
		}

		// Initialize RequestReceiver
		reqReceiver = new RequestReceiver(new String[]{bridgeFreemail});
		// Start a thread for polling for new freemails
		reqReceiver.loopFreemailBoxes();

		// Configure CENO's jetty embedded server
		cenoHttpServer = new Server();
		configHttpServer(cenoHttpServer);

		// Start server and wait until it gets interrupted
		try {
			cenoHttpServer.start();
			cenoHttpServer.join();
		} catch (InterruptedException interruptedEx) {
			Logger.normal(this, "HTTP Server interrupted. Terminating plugin...");
			terminate();
			return;
		} catch (Exception ex) {
			Logger.error(this, "HTTP Server terminated abnormally");
			Logger.error(this, ex.getMessage());
		}
	}

	/**
	 * Configure CENO's embedded server
	 * 
	 * @param cenoHttpServer the jetty server to be configured
	 */
	private void configHttpServer(Server cenoHttpServer) {
		// Create a collection of ContextHandlers for the server
		ContextHandlerCollection handlers = new ContextHandlerCollection();
		cenoHttpServer.setHandler(handlers);

		// Add a ServerConnector for the BundlerInserter agent
		ServerConnector bundleInserterConnector = new ServerConnector(cenoHttpServer);
		bundleInserterConnector.setName("bundleInserter");
		bundleInserterConnector.setPort(bundleInserterPort);

		// Add the connector to the server
		cenoHttpServer.addConnector(bundleInserterConnector);

		// Configure ContextHandlers to listen to a specific port
		// and upon request call the appropriate CENOJettyHandler subclass
		ContextHandler cacheInsertCtxHandler = new ContextHandler();
		cacheInsertCtxHandler.setHandler(new BundleInserterHandler());
		cacheInsertCtxHandler.setVirtualHosts(new String[]{"@cacheInsert"});

		// Add the configured ContextHandler to the server
		handlers.addHandler(cacheInsertCtxHandler);

		/*
		 * Uncomment the following block if you need a lookup handler in the bridge side
		 */
		/*
		ServerConnector httpConnector = new ServerConnector(cenoHttpServer);
		httpConnector.setName("cacheLookup");
		httpConnector.setPort(cacheLookupPort);
		cenoHttpServer.addConnector(httpConnector);

		ContextHandler cacheLookupCtxHandler = new ContextHandler();
		cacheLookupCtxHandler.setHandler(new CacheLookupHandler());
		cacheLookupCtxHandler.setVirtualHosts(new String[]{"@cacheLookup"});

		handlers.addHandler(cacheLookupCtxHandler);
		*/
	}

	public String getVersion() {
		return version.getVersion();
	}

	public long getRealVersion() {
		return version.getRealVersion();
	}

	/**
	 * Method called before termination of the CENO bridge plugin
	 * Terminates ceNoHttpServer and releases resources
	 */
	public void terminate()
	{
		// Stop the thread that is polling for freemails
		reqReceiver.stopLooping();

		// Stop cenoHttpServer and unbind ports
		if (cenoHttpServer != null) {
			try {
				cenoHttpServer.stop();
			} catch (Exception e) {
				Logger.error(this, "Exception while terminating HTTP server.");
				Logger.error(this, e.getMessage());
			}
		}

		Logger.normal(this, pluginName + " terminated.");
	}

}