package us.kbase.userandjobstate.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import static us.kbase.common.test.TestCommon.assertExceptionCorrect;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.DB;
import com.mongodb.DBCollection;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthUser;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.schemamanager.SchemaManager;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.TestException;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.userandjobstate.authorization.AuthorizationStrategy;
import us.kbase.userandjobstate.authorization.UJSAuthorizer;
import us.kbase.userandjobstate.authorization.exceptions.UJSAuthorizationException;
import us.kbase.userandjobstate.jobstate.JobState;
import us.kbase.userandjobstate.kbase.WorkspaceAuthorizationFactory;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.test.WorkspaceTestCommon;

public class WorkspaceAuthTest {
	
	public static AuthorizationStrategy strat =
			new AuthorizationStrategy("kbaseworkspace");
	
	public static AuthUser U1;
	public static AuthUser U2;
	
	public static MongoController MONGO;
	public static WorkspaceServer WS;
	public static WorkspaceClient WSC1;
	public static WorkspaceClient WSC2;
	private static DBCollection JOBCOL;
	private static JobState JS;
	
	public static final String WS_DB_NAME = "ws";
	public static final String JOB_DB_NAME = "job";
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		String user1 = System.getProperty("test.user1");
		String user2 = System.getProperty("test.user2");
		String p1 = System.getProperty("test.pwd1");
		String p2 = System.getProperty("test.pwd2");
		
		try {
			U1 = AuthService.login(user1, p1);
		} catch (Exception e) {
			throw new TestException("Could not log in test user test.user1: " +
					user1, e);
		}
		try {
			U2 = AuthService.login(user2, p2);
		} catch (Exception e) {
			throw new TestException("Could not log in test user test.user2: " +
					user2, e);
		}
		MONGO = new MongoController(
				TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()));
		System.out.println("Using Mongo temp dir " + MONGO.getTempDir());
		String mongohost = "localhost:" + MONGO.getServerPort();
		System.out.println("mongo on " + mongohost);
		
		WS = startupWorkspaceServer(mongohost, WS_DB_NAME, "ws_types", p1);
		final int port = WS.getServerPort();
		try {
			WSC1 = new WorkspaceClient(new URL("http://localhost:" + port),
					user1, p1);
		} catch (UnauthorizedException ue) {
			throw new TestException("Unable to login with test.user1: " + user1 +
					"\nPlease check the credentials in the test configuration.", ue);
		}
		try {
			WSC2 = new WorkspaceClient(new URL("http://localhost:" + port), user2, p2);
		} catch (UnauthorizedException ue) {
			throw new TestException("Unable to login with test.user2: " + user2 +
					"\nPlease check the credentials in the test configuration.", ue);
		}
		WSC1.setIsInsecureHttpConnectionAllowed(true);
		WSC2.setIsInsecureHttpConnectionAllowed(true);
		final DB db = GetMongoDB.getDB(
				"localhost:" + MONGO.getServerPort(), JOB_DB_NAME, 0, 0);
		JOBCOL = db.getCollection("jobstate");
		final DBCollection schemacol = db.getCollection("schema");
		JS = new JobState(JOBCOL, new SchemaManager(schemacol));
	}
	
	@AfterClass
	public static void afterClass() throws Exception {
		if (MONGO != null) {
			MONGO.destroy(TestCommon.getDeleteTempFiles());
		}
		if (WS != null) {
			WS.stopServer();
		}
	}
	
	@Before
	public void before() throws Exception {
		DB db = GetMongoDB.getDB("localhost:" + MONGO.getServerPort(),
				WS_DB_NAME);
		TestCommon.destroyDB(db);
		db = GetMongoDB.getDB("localhost:" + MONGO.getServerPort(),
				JOB_DB_NAME);
		TestCommon.destroyDB(db);
	}
	
	//TODO ZZLATER make the JSONRPCLayerTester method public & use
	private static WorkspaceServer startupWorkspaceServer(String mongohost,
			String dbname, String typedb, String user1Password)
			throws InvalidHostException, UnknownHostException, IOException,
			NoSuchFieldException, IllegalAccessException, Exception,
			InterruptedException {
		DB db = GetMongoDB.getDB(mongohost, dbname);
		WorkspaceTestCommon.initializeGridFSWorkspaceDB(db, typedb);
		
		//write the server config file:
		File iniFile = File.createTempFile("test", ".cfg",
				new File(TestCommon.getTempDir()));
		if (iniFile.exists()) {
			iniFile.delete();
		}
		System.out.println("Created temporary config file: " +
		iniFile.getAbsolutePath());
		Ini ini = new Ini();
		Section ws = ini.add("Workspace");
		ws.add("mongodb-host", mongohost);
		ws.add("mongodb-database", db.getName());
		ws.add("backend-secret", "foo");
		ws.add("ws-admin", U2.getUserId());
		ws.add("kbase-admin-user", U1.getUserId());
		ws.add("kbase-admin-pwd", user1Password);
		ws.add("temp-dir", Paths.get(TestCommon.getTempDir())
				.resolve("tempForWorkspaceForUJSAuthTest"));
		ws.add("ignore-handle-service", "true");
		ini.store(iniFile);
		iniFile.deleteOnExit();
		
		//set up env
		Map<String, String> env = TestCommon.getenv();
		env.put("KB_DEPLOYMENT_CONFIG", iniFile.getAbsolutePath());
		env.put("KB_SERVICE_NAME", "Workspace");

		WorkspaceServer.clearConfigForTests();
		WorkspaceServer server = new WorkspaceServer();
		new ServerThread(server).start();
		System.out.println("Main thread waiting for server to start up");
		while (server.getServerPort() == null) {
			Thread.sleep(1000);
		}
		return server;
	}
	
	protected static class ServerThread extends Thread {
		private WorkspaceServer server;
		
		protected ServerThread(WorkspaceServer server) {
			this.server = server;
		}
		
		public void run() {
			try {
				server.startupServer();
			} catch (Exception e) {
				System.err.println("Can't start server:");
				e.printStackTrace();
			}
		}
	}
	
	@Test
	public void testFactoryInit() throws Exception {
		try {
			new WorkspaceAuthorizationFactory(null);
			fail("created factory w/ bad args");
		} catch (NullPointerException e) {
			assertExceptionCorrect(e, new NullPointerException(
					"workspaceURL"));
		}
		try {
			new WorkspaceAuthorizationFactory(
					new URL("http://localhost:" + (WS.getServerPort() + 1)));
			fail("created factory w/ bad args");
		} catch (IOException e) {
			assertExceptionCorrect(e, new IOException("Connection refused"));
		}
	}
	
	@Test
	public void testBuild() throws Exception {
		try {
			new WorkspaceAuthorizationFactory(new URL("http://localhost:" +
					WS.getServerPort())).buildAuthorizer(null);
			fail("ran bad build");
		} catch (Exception e) {
			assertExceptionCorrect(e, new NullPointerException("token"));
		}
	}
	
	@Test
	public void testAuthorizeCreate() throws Exception {
		WorkspaceAuthorizationFactory wafac =
				new WorkspaceAuthorizationFactory(
						new URL("http://localhost:" + WS.getServerPort()));
		UJSAuthorizer wa = wafac.buildAuthorizer(U1.getToken());
		WSC1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("foo"));
		wa.authorizeCreate(strat, "1");
		
		failCreate(wa, new AuthorizationStrategy("foo"), "foo",
				new UJSAuthorizationException(
						"Invalid authorization strategy: foo"));
		failCreate(wa, strat, "foo",
				new UJSAuthorizationException(
						"The string foo is not a valid integer workspace ID"));

		failCreate(wa, strat, "2",
				new UJSAuthorizationException(
						"Error contacting the workspace service to get " +
						"permissions: No workspace with id 2 exists"));
		WSC1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("foo1"));
		wa.authorizeCreate(strat, "2");
		
		UJSAuthorizer wa2 = wafac.buildAuthorizer(U2.getToken());
		failCreate(wa2, strat, "1", new UJSAuthorizationException(
				String.format("User %s cannot write to workspace 1",
						U2.getUserId())));
		
		setPermissions(WSC1, 1, "r", U2.getUserId());
		failCreate(wa2, strat, "1", new UJSAuthorizationException(
				String.format("User %s cannot write to workspace 1",
						U2.getUserId())));
		
		setPermissions(WSC1, 1, "w", U2.getUserId());
		wa2.authorizeCreate(strat, "1");
		setPermissions(WSC1, 1, "a", U2.getUserId());
		wa2.authorizeCreate(strat, "1");
	}

	private void setPermissions(WorkspaceClient wsc, long id, String perm,
			String user) throws Exception {
		wsc.setPermissions(new SetPermissionsParams().withId(id)
				.withNewPermission(perm)
				.withUsers(Arrays.asList(user)));
	}
	
	private void failCreate(UJSAuthorizer auth, AuthorizationStrategy strat,
			String param, Exception exp) throws Exception {
		try {
			auth.authorizeCreate(strat, param);
			fail("authorized create with bad auth");
		} catch (Exception got) {
			assertExceptionCorrect(got, exp);
		}
		
	}

}
