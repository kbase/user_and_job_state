package us.kbase.userandjobstate.test.kbase;

import static org.junit.Assert.fail;

import static us.kbase.common.test.TestCommon.assertExceptionCorrect;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
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
import us.kbase.common.exceptions.UnimplementedException;
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
import us.kbase.userandjobstate.jobstate.Job;
import us.kbase.userandjobstate.jobstate.JobState;
import us.kbase.userandjobstate.kbase.WorkspaceAuthorizationFactory;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.database.WorkspaceUserMetadata;
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
	
	private static UJSAuthorizer LENIENT = new UJSAuthorizer() {
		
		@Override
		protected void externallyAuthorizeRead(AuthorizationStrategy strat,
				String user, List<String> authParams)
				throws UJSAuthorizationException {
			throw new UnimplementedException();
		}
		
		@Override
		protected void externallyAuthorizeRead(String user, Job j)
				throws UJSAuthorizationException {
			// go ahead
		}
		
		@Override
		protected void externallyAuthorizeCreate(AuthorizationStrategy strat,
				String authParam) throws UJSAuthorizationException {
			//I'll allow it
		}
	};
	
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
	
	@Test
	public void testAuthorizeSingleRead() throws Exception {
		WorkspaceAuthorizationFactory wafac =
				new WorkspaceAuthorizationFactory(
						new URL("http://localhost:" + WS.getServerPort()));
		UJSAuthorizer wa1 = wafac.buildAuthorizer(U1.getToken());
		UJSAuthorizer wa2 = wafac.buildAuthorizer(U2.getToken());
		String user1 = U1.getUserId();
		String user2 = U2.getUserId();
		
		WSC1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("foo"));
		setPermissions(WSC1, 1, "w", user2);
		WorkspaceUserMetadata mt = new WorkspaceUserMetadata();
		
		//test that deleting a workspace keeps the job visible to the owner
		String id = JS.createJob(user1, wa1, strat, "1", mt);
		Job j = JS.getJob(user1, id, wa1);
		wa2.authorizeRead(user2, j);
		WSC1.deleteWorkspace(new WorkspaceIdentity().withId(1L));
		failSingleRead(wa2, user2, j, new UJSAuthorizationException(
				"Error contacting the workspace service to get permissions: " +
				"Workspace 1 is deleted"));
		JS.startJob(user1, id, "foo", "stat1", "desc1", null);
		JS.updateJob(user1, id, "foo", "stat1", null, null);
		JS.completeJob(user1, id, "foo", "stat2", null, null);
		wa1.authorizeRead(user1, j);
		WSC1.undeleteWorkspace(new WorkspaceIdentity().withId(1L));
		wa2.authorizeRead(user2, j);
		setPermissions(WSC1, 1, "n", user2);
		failSingleRead(wa2, user2, j, new UJSAuthorizationException(
				String.format("User %s cannot read workspace 1", user2)));
		setPermissions(WSC1, 1, "w", user2);
		wa2.authorizeRead(user2, j);
		setPermissions(WSC1, 1, "a", user2);
		wa2.authorizeRead(user2, j);
		
		failSingleRead(wa2, user1, j, new IllegalStateException(
				"A programming error occured: the token username and the " +
				"supplied username do not match"));
		
		// test bad auth strat
		String id2 = JS.createJob(user1, LENIENT,
				new AuthorizationStrategy("foo"), "foo", mt);
		Job j2 = JS.getJob(user1, id2, LENIENT);
		failSingleRead(wa1, user1, j2, new UJSAuthorizationException(
				"Invalid authorization strategy: foo"));
		
	}
	
	private void failSingleRead(UJSAuthorizer auth, String user, Job j,
			Exception exp) {
		try {
			auth.authorizeRead(user, j);
			fail("authorized bad read");
		} catch (Exception got) {
			assertExceptionCorrect(got, exp);
		}
	}
	
	@Test
	public void testMultipleRead() throws Exception {
		WorkspaceAuthorizationFactory wafac =
				new WorkspaceAuthorizationFactory(
						new URL("http://localhost:" + WS.getServerPort()));
		UJSAuthorizer wa1 = wafac.buildAuthorizer(U1.getToken());
		UJSAuthorizer wa2 = wafac.buildAuthorizer(U2.getToken());
		String user1 = U1.getUserId();
		String user2 = U2.getUserId();
		
		WSC1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("foo"));
		WSC1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("foo1"));
		WSC1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("foo2"));
		setPermissions(WSC1, 1, "r", user2);
		setPermissions(WSC1, 3, "r", user2);
		
		wa1.authorizeRead(strat, user1, Arrays.asList("1", "2", "3"));
		wa2.authorizeRead(strat, user2, Arrays.asList("1", "3"));
		failMultipleRead(wa2, strat, user2, Arrays.asList("1", "2", "3"),
				new UJSAuthorizationException(String.format(
						"User %s cannot read workspace 2", user2)));
		
		WSC1.deleteWorkspace(new WorkspaceIdentity().withId(1L));
		failMultipleRead(wa2, strat, user2, Arrays.asList("1", "3"),
				new UJSAuthorizationException("Error contacting the " +
						"workspace service to get permissions: Workspace 1 " +
						"is deleted"));
		failMultipleRead(wa2, strat, user2, Arrays.asList("3", "4"),
				new UJSAuthorizationException("Error contacting the " +
						"workspace service to get permissions: No workspace " +
						"with id 4 exists"));
		wa2.authorizeRead(strat, user2, Arrays.asList("3"));
		WSC1.undeleteWorkspace(new WorkspaceIdentity().withId(1L));
		wa2.authorizeRead(strat, user2, Arrays.asList("1", "3"));
		setPermissions(WSC1, 3, "n", user2);
		//check fencepost error
		failMultipleRead(wa2, strat, user2, Arrays.asList("1", "3"),
				new UJSAuthorizationException(String.format(
				"User %s cannot read workspace 3", user2)));
		setPermissions(WSC1, 3, "w", user2);
		wa2.authorizeRead(strat, user2, Arrays.asList("1", "3"));
		setPermissions(WSC1, 3, "a", user2);
		wa2.authorizeRead(strat, user2, Arrays.asList("1", "3"));
		
		
		
		failMultipleRead(wa1, strat, user1, Arrays.asList("1", "2", "3", "4",
				"5", "6", "7", "8", "9", "10", "11"),
				new UJSAuthorizationException(
						"No more than 10 workspace IDs may be specified"));
		failMultipleRead(wa1, strat, user1, Arrays.asList("1", "2", "3", "4",
				"5", "6", "7", "8", "9", "foo"),
				new UJSAuthorizationException(
						"The string foo is not a valid integer workspace ID"));
		failMultipleRead(wa1, strat, user2, Arrays.asList("1"),
				new IllegalStateException("A programming error occured: the " +
						"token username and the supplied username do not " +
						"match"));
		
		failMultipleRead(wa1, new AuthorizationStrategy("foo"), user1,
				Arrays.asList("1"),
				new UJSAuthorizationException(
						"Invalid authorization strategy: foo"));
	}

	private void failMultipleRead(UJSAuthorizer auth,
			AuthorizationStrategy strat, String user, List<String> params,
			Exception exp) {
		try {
			auth.authorizeRead(strat, user, params);
			fail("authorized bad read");
		} catch (Exception got) {
			assertExceptionCorrect(got, exp);
		}
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
