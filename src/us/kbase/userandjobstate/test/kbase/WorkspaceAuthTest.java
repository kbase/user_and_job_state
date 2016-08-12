package us.kbase.userandjobstate.test.kbase;

import static org.junit.Assert.fail;

import static us.kbase.common.test.TestCommon.assertExceptionCorrect;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.DB;
import com.mongodb.DBCollection;

import us.kbase.auth.AuthConfig;
import us.kbase.auth.AuthToken;
import us.kbase.auth.AuthUser;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.common.exceptions.UnimplementedException;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.schemamanager.SchemaManager;
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
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.database.WorkspaceUserMetadata;

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
		final ConfigurableAuthService auth = new ConfigurableAuthService(
				new AuthConfig().withKBaseAuthServerURL(
						TestCommon.getAuthUrl()));
		final AuthToken t1 = TestCommon.getToken(1, auth);
		final AuthToken t2 = TestCommon.getToken(2, auth);
		if (t1.getUserName().equals(t2.getUserName())) {
			throw new TestException("user1 cannot equal user2: " +
					t1.getUserName());
		}
		final String p1 = TestCommon.getPwdNullIfToken(1); 
		
		U1 = auth.getUserFromToken(t1);
		U2 = auth.getUserFromToken(t2);
		MONGO = new MongoController(
				TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()));
		System.out.println("Using Mongo temp dir " + MONGO.getTempDir());
		String mongohost = "localhost:" + MONGO.getServerPort();
		System.out.println("mongo on " + mongohost);
		
		WS = JSONRPCLayerTestUtils.startupWorkspaceServer(
				mongohost, WS_DB_NAME, "ws_types", t1, p1, t2.getUserName());
		final int port = WS.getServerPort();
		WSC1 = new WorkspaceClient(new URL("http://localhost:" + port), t1);
		WSC2 = new WorkspaceClient(new URL("http://localhost:" + port), t2);
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

		@Override
		protected void externallyAuthorizeCancel(String user, Job j)
				throws UJSAuthorizationException {
			throw new UnimplementedException();
			
		}
		
		@Override
		protected void externallyAuthorizeDelete(String user, Job j)
				throws UJSAuthorizationException {
			throw new UnimplementedException();
			
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
		
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "r", U2.getUserId());
		failCreate(wa2, strat, "1", new UJSAuthorizationException(
				String.format("User %s cannot write to workspace 1",
						U2.getUserId())));
		
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "w", U2.getUserId());
		wa2.authorizeCreate(strat, "1");
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "a", U2.getUserId());
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
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "r", user2);
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
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "n", user2);
		failSingleRead(wa2, user2, j, new UJSAuthorizationException(
				String.format("User %s cannot read workspace 1", user2)));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "w", user2);
		wa2.authorizeRead(user2, j);
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "a", user2);
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
	
	@Test
	public void testCancel() throws Exception {
		WorkspaceAuthorizationFactory wafac =
				new WorkspaceAuthorizationFactory(
						new URL("http://localhost:" + WS.getServerPort()));
		UJSAuthorizer wa1 = wafac.buildAuthorizer(U1.getToken());
		UJSAuthorizer wa2 = wafac.buildAuthorizer(U2.getToken());
		String user1 = U1.getUserId();
		String user2 = U2.getUserId();
		
		WSC1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("foo"));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "w", user2);
		WorkspaceUserMetadata mt = new WorkspaceUserMetadata();
		
		//test that deleting a workspace keeps the job visible to the owner
		String id = JS.createJob(user1, wa1, strat, "1", mt);
		Job j = JS.getJob(user1, id, wa1);
		wa2.authorizeCancel(user2, j);
		WSC1.deleteWorkspace(new WorkspaceIdentity().withId(1L));
		failCancel(wa2, user2, j, new UJSAuthorizationException(
				"Error contacting the workspace service to get permissions: " +
				"Workspace 1 is deleted"));
		wa1.authorizeCancel(user1, j);
		WSC1.undeleteWorkspace(new WorkspaceIdentity().withId(1L));
		wa2.authorizeCancel(user2, j);
		
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "n", user2);
		failCancel(wa2, user2, j, new UJSAuthorizationException(
				String.format("User %s cannot write to workspace 1", user2)));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "r", user2);
		failCancel(wa2, user2, j, new UJSAuthorizationException(
				String.format("User %s cannot write to workspace 1", user2)));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "a", user2);
		wa2.authorizeCancel(user2, j);
		
		failCancel(wa2, user1, j, new IllegalStateException(
				"A programming error occured: the token username and the " +
				"supplied username do not match"));
		
		// test bad auth strat
		String id2 = JS.createJob(user1, LENIENT,
				new AuthorizationStrategy("foo"), "foo", mt);
		Job j2 = JS.getJob(user1, id2, LENIENT);
		failCancel(wa1, user1, j2, new UJSAuthorizationException(
				"Invalid authorization strategy: foo"));
	}
	
	@Test
	public void testDelete() throws Exception {
		WorkspaceAuthorizationFactory wafac =
				new WorkspaceAuthorizationFactory(
						new URL("http://localhost:" + WS.getServerPort()));
		UJSAuthorizer wa1 = wafac.buildAuthorizer(U1.getToken());
		UJSAuthorizer wa2 = wafac.buildAuthorizer(U2.getToken());
		String user1 = U1.getUserId();
		String user2 = U2.getUserId();
		
		WSC1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("foo"));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "a", user2);
		WorkspaceUserMetadata mt = new WorkspaceUserMetadata();
		
		//test that deleting a workspace keeps the job visible to the owner
		String id = JS.createJob(user1, wa1, strat, "1", mt);
		Job j = JS.getJob(user1, id, wa1);
		wa2.authorizeDelete(user2, j);
		WSC1.deleteWorkspace(new WorkspaceIdentity().withId(1L));
		failDelete(wa2, user2, j, new UJSAuthorizationException(
				"Error contacting the workspace service to get permissions: " +
				"Workspace 1 is deleted"));
		wa1.authorizeDelete(user1, j);
		WSC1.undeleteWorkspace(new WorkspaceIdentity().withId(1L));
		wa2.authorizeDelete(user2, j);
		
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "n", user2);
		failDelete(wa2, user2, j, new UJSAuthorizationException(String.format(
				"User %s does not have administration privileges for " +
				"workspace 1", user2)));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "r", user2);
		failDelete(wa2, user2, j, new UJSAuthorizationException(String.format(
				"User %s does not have administration privileges for " +
				"workspace 1", user2)));;
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "w", user2);
		failDelete(wa2, user2, j, new UJSAuthorizationException(String.format(
				"User %s does not have administration privileges for " +
				"workspace 1", user2)));;
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "a", user2);
		wa2.authorizeDelete(user2, j);
		
		
		failDelete(wa2, user1, j, new IllegalStateException(
				"A programming error occured: the token username and the " +
				"supplied username do not match"));
		
		// test bad auth strat
		String id2 = JS.createJob(user1, LENIENT,
				new AuthorizationStrategy("foo"), "foo", mt);
		Job j2 = JS.getJob(user1, id2, LENIENT);
		failDelete(wa1, user1, j2, new UJSAuthorizationException(
				"Invalid authorization strategy: foo"));
	}
	
	private void failDelete(
			final UJSAuthorizer auth,
			final String user,
			final Job j,
			final Exception exp) {
		try {
			auth.authorizeDelete(user, j);
			fail("authorized bad delete");
		} catch (Exception got) {
			assertExceptionCorrect(got, exp);
		}
	}

	private void failCancel(
			final UJSAuthorizer auth,
			final String user,
			final Job j,
			final Exception exp) {
		try {
			auth.authorizeCancel(user, j);
			fail("authorized bad cancel");
		} catch (Exception got) {
			assertExceptionCorrect(got, exp);
		}
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
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "r", user2);
		JSONRPCLayerTestUtils.setPermissions(WSC1, 3, "r", user2);
		
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
		JSONRPCLayerTestUtils.setPermissions(WSC1, 3, "n", user2);
		//check fencepost error
		failMultipleRead(wa2, strat, user2, Arrays.asList("1", "3"),
				new UJSAuthorizationException(String.format(
				"User %s cannot read workspace 3", user2)));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 3, "w", user2);
		wa2.authorizeRead(strat, user2, Arrays.asList("1", "3"));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 3, "a", user2);
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
