package us.kbase.userandjobstate.test.kbase;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
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

import com.google.common.collect.ImmutableMap;
import com.mongodb.DB;
import com.mongodb.DBCollection;

import us.kbase.auth.AuthToken;
import us.kbase.common.exceptions.UnimplementedException;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.schemamanager.SchemaManager;
import us.kbase.common.service.UObject;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.test.auth2.authcontroller.AuthController;
import us.kbase.userandjobstate.authorization.AuthorizationStrategy;
import us.kbase.userandjobstate.authorization.UJSAuthorizer;
import us.kbase.userandjobstate.authorization.exceptions.UJSAuthorizationException;
import us.kbase.userandjobstate.jobstate.Job;
import us.kbase.userandjobstate.jobstate.JobState;
import us.kbase.userandjobstate.kbase.WorkspaceAuthorizationFactory;
import us.kbase.userandjobstate.test.controllers.workspace.WorkspaceController;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.database.WorkspaceUserMetadata;

public class WorkspaceAuthTest {
	
	public static AuthorizationStrategy strat =
			new AuthorizationStrategy("kbaseworkspace");
	
	public static final String USER1 = "user1";
	public static final String USER2 = "user2";
	public static AuthToken TOKEN1;
	public static AuthToken TOKEN2;
	
	public static MongoController MONGO;
	public static WorkspaceController WS;
	public static WorkspaceClient WSC1;
	public static WorkspaceClient WSC2;
	private static DBCollection JOBCOL;
	private static JobState JS;
	
	private static AuthController AUTHC;
	
	public static final String WS_DB_NAME = "ws";
	public static final String JOB_DB_NAME = "job";
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		MONGO = new MongoController(
				TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using Mongo temp dir " + MONGO.getTempDir());
		String mongohost = "localhost:" + MONGO.getServerPort();
		System.out.println("mongo on " + mongohost);
		
		// set up auth
		final String dbname = JSONRPCLayerTest.class.getSimpleName() + "Auth";
		AUTHC = new AuthController(
				TestCommon.getJarsDir(),
				"localhost:" + MONGO.getServerPort(),
				dbname,
				Paths.get(TestCommon.getTempDir()));
		
		// set up ws
		final URL authURL = new URL("http://localhost:" + AUTHC.getServerPort() + "/testmode");
		System.out.println("started auth server at " + authURL);
		TestCommon.createAuthUser(authURL, USER1, "display1");
		final String token1 = TestCommon.createLoginToken(authURL, USER1);
		TestCommon.createAuthUser(authURL, USER2, "display2");
		final String token2 = TestCommon.createLoginToken(authURL, USER2);
		TOKEN1 = new AuthToken(token1, USER1);
		TOKEN2 = new AuthToken(token2, USER2);
		
		WS = new WorkspaceController(
				TestCommon.getJarsDir(),
				mongohost,
				WS_DB_NAME,
				USER2,
				authURL,
				Paths.get(TestCommon.getTempDir()).resolve("tempForWorkspaceForUJSAuthTest"));
		
		final int port = WS.getServerPort();
		WSC1 = new WorkspaceClient(new URL("http://localhost:" + port), TOKEN1);
		WSC2 = new WorkspaceClient(new URL("http://localhost:" + port), TOKEN2);
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
		if (AUTHC != null) {
			AUTHC.destroy(TestCommon.getDeleteTempFiles());
		}
		if (WS != null) {
			WS.destroy(TestCommon.getDeleteTempFiles());
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
			// for some reason, testing in travis winds up w/ a different error message than
			// testing locally. JDK version probably
			assertThat("incorrect message", e.getMessage(), containsString("Connection refused"));
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
		UJSAuthorizer wa = wafac.buildAuthorizer(TOKEN1);
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
		
		UJSAuthorizer wa2 = wafac.buildAuthorizer(TOKEN2);
		failCreate(wa2, strat, "1", new UJSAuthorizationException(
				String.format("User %s cannot write to workspace 1",
						USER2)));
		
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "r", USER2);
		failCreate(wa2, strat, "1", new UJSAuthorizationException(
				String.format("User %s cannot write to workspace 1",
						USER2)));
		
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "w", USER2);
		wa2.authorizeCreate(strat, "1");
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "a", USER2);
		wa2.authorizeCreate(strat, "1");
	}
	
	@Test
	public void testAuthorizeSingleRead() throws Exception {
		WorkspaceAuthorizationFactory wafac =
				new WorkspaceAuthorizationFactory(
						new URL("http://localhost:" + WS.getServerPort()));
		UJSAuthorizer wa1 = wafac.buildAuthorizer(TOKEN1);
		UJSAuthorizer wa2 = wafac.buildAuthorizer(TOKEN2);
		
		WSC1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("foo"));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "r", USER2);
		WorkspaceUserMetadata mt = new WorkspaceUserMetadata();
		
		//test that deleting a workspace keeps the job visible to the owner
		String id = JS.createJob(USER1, wa1, strat, "1", mt);
		Job j = JS.getJob(USER1, id, wa1);
		wa2.authorizeRead(USER2, j);
		WSC1.deleteWorkspace(new WorkspaceIdentity().withId(1L));
		failSingleRead(wa2, USER2, j, new UJSAuthorizationException(
				"Error contacting the workspace service to get permissions: " +
				"Workspace 1 is deleted"));
		JS.startJob(USER1, id, "foo", "stat1", "desc1", null);
		JS.updateJob(USER1, id, "foo", "stat1", null, null);
		JS.completeJob(USER1, id, "foo", "stat2", null, null);
		wa1.authorizeRead(USER1, j);
		WSC2.administer(new UObject(ImmutableMap.of("command", "undeleteWorkspace",
				"params", new WorkspaceIdentity().withId(1L))));
		wa2.authorizeRead(USER2, j);
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "n", USER2);
		failSingleRead(wa2, USER2, j, new UJSAuthorizationException(
				String.format("User %s cannot read workspace 1", USER2)));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "w", USER2);
		wa2.authorizeRead(USER2, j);
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "a", USER2);
		wa2.authorizeRead(USER2, j);
		
		// test globally readable workspace
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "n", USER2);
		failSingleRead(wa2, USER2, j, new UJSAuthorizationException(
				String.format("User %s cannot read workspace 1", USER2)));
		WSC1.setGlobalPermission(new SetGlobalPermissionsParams().withId(1L)
				.withNewPermission("r"));
		wa2.authorizeRead(USER2, j);
		
		failSingleRead(wa2, USER1, j, new IllegalStateException(
				"A programming error occured: the token username and the " +
				"supplied username do not match"));
		
		// test bad auth strat
		String id2 = JS.createJob(USER1, LENIENT,
				new AuthorizationStrategy("foo"), "foo", mt);
		Job j2 = JS.getJob(USER1, id2, LENIENT);
		failSingleRead(wa1, USER1, j2, new UJSAuthorizationException(
				"Invalid authorization strategy: foo"));
	}
	
	@Test
	public void testCancel() throws Exception {
		WorkspaceAuthorizationFactory wafac =
				new WorkspaceAuthorizationFactory(
						new URL("http://localhost:" + WS.getServerPort()));
		UJSAuthorizer wa1 = wafac.buildAuthorizer(TOKEN1);
		UJSAuthorizer wa2 = wafac.buildAuthorizer(TOKEN2);
		
		WSC1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("foo"));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "w", USER2);
		WorkspaceUserMetadata mt = new WorkspaceUserMetadata();
		
		//test that deleting a workspace keeps the job visible to the owner
		String id = JS.createJob(USER1, wa1, strat, "1", mt);
		Job j = JS.getJob(USER1, id, wa1);
		wa2.authorizeCancel(USER2, j);
		WSC1.deleteWorkspace(new WorkspaceIdentity().withId(1L));
		failCancel(wa2, USER2, j, new UJSAuthorizationException(
				"Error contacting the workspace service to get permissions: " +
				"Workspace 1 is deleted"));
		wa1.authorizeCancel(USER1, j);
		WSC2.administer(new UObject(ImmutableMap.of("command", "undeleteWorkspace",
				"params", new WorkspaceIdentity().withId(1L))));
		wa2.authorizeCancel(USER2, j);
		
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "n", USER2);
		failCancel(wa2, USER2, j, new UJSAuthorizationException(
				String.format("User %s cannot write to workspace 1", USER2)));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "r", USER2);
		failCancel(wa2, USER2, j, new UJSAuthorizationException(
				String.format("User %s cannot write to workspace 1", USER2)));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "a", USER2);
		wa2.authorizeCancel(USER2, j);
		
		failCancel(wa2, USER1, j, new IllegalStateException(
				"A programming error occured: the token username and the " +
				"supplied username do not match"));
		
		// test bad auth strat
		String id2 = JS.createJob(USER1, LENIENT,
				new AuthorizationStrategy("foo"), "foo", mt);
		Job j2 = JS.getJob(USER1, id2, LENIENT);
		failCancel(wa1, USER1, j2, new UJSAuthorizationException(
				"Invalid authorization strategy: foo"));
	}
	
	@Test
	public void testDelete() throws Exception {
		WorkspaceAuthorizationFactory wafac =
				new WorkspaceAuthorizationFactory(
						new URL("http://localhost:" + WS.getServerPort()));
		UJSAuthorizer wa1 = wafac.buildAuthorizer(TOKEN1);
		UJSAuthorizer wa2 = wafac.buildAuthorizer(TOKEN2);
		
		WSC1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("foo"));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "a", USER2);
		WorkspaceUserMetadata mt = new WorkspaceUserMetadata();
		
		//test that deleting a workspace keeps the job visible to the owner
		String id = JS.createJob(USER1, wa1, strat, "1", mt);
		Job j = JS.getJob(USER1, id, wa1);
		wa2.authorizeDelete(USER2, j);
		WSC1.deleteWorkspace(new WorkspaceIdentity().withId(1L));
		failDelete(wa2, USER2, j, new UJSAuthorizationException(
				"Error contacting the workspace service to get permissions: " +
				"Workspace 1 is deleted"));
		wa1.authorizeDelete(USER1, j);
		WSC2.administer(new UObject(ImmutableMap.of("command", "undeleteWorkspace",
				"params", new WorkspaceIdentity().withId(1L))));
		wa2.authorizeDelete(USER2, j);
		
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "n", USER2);
		failDelete(wa2, USER2, j, new UJSAuthorizationException(String.format(
				"User %s does not have administration privileges for " +
				"workspace 1", USER2)));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "r", USER2);
		failDelete(wa2, USER2, j, new UJSAuthorizationException(String.format(
				"User %s does not have administration privileges for " +
				"workspace 1", USER2)));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "w", USER2);
		failDelete(wa2, USER2, j, new UJSAuthorizationException(String.format(
				"User %s does not have administration privileges for " +
				"workspace 1", USER2)));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "a", USER2);
		wa2.authorizeDelete(USER2, j);
		
		
		failDelete(wa2, USER1, j, new IllegalStateException(
				"A programming error occured: the token username and the " +
				"supplied username do not match"));
		
		// test bad auth strat
		String id2 = JS.createJob(USER1, LENIENT,
				new AuthorizationStrategy("foo"), "foo", mt);
		Job j2 = JS.getJob(USER1, id2, LENIENT);
		failDelete(wa1, USER1, j2, new UJSAuthorizationException(
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
		UJSAuthorizer wa1 = wafac.buildAuthorizer(TOKEN1);
		UJSAuthorizer wa2 = wafac.buildAuthorizer(TOKEN2);
		
		WSC1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("foo"));
		WSC1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("foo1"));
		WSC1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("foo2"));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 1, "r", USER2);
		JSONRPCLayerTestUtils.setPermissions(WSC1, 3, "r", USER2);
		
		wa1.authorizeRead(strat, USER1, Arrays.asList("1", "2", "3"));
		wa2.authorizeRead(strat, USER2, Arrays.asList("1", "3"));
		failMultipleRead(wa2, strat, USER2, Arrays.asList("1", "2", "3"),
				new UJSAuthorizationException(String.format(
						"User %s cannot read workspace 2", USER2)));
		
		WSC1.deleteWorkspace(new WorkspaceIdentity().withId(1L));
		failMultipleRead(wa2, strat, USER2, Arrays.asList("1", "3"),
				new UJSAuthorizationException("Error contacting the " +
						"workspace service to get permissions: Workspace 1 " +
						"is deleted"));
		failMultipleRead(wa2, strat, USER2, Arrays.asList("3", "4"),
				new UJSAuthorizationException("Error contacting the " +
						"workspace service to get permissions: No workspace " +
						"with id 4 exists"));
		wa2.authorizeRead(strat, USER2, Arrays.asList("3"));
		WSC2.administer(new UObject(ImmutableMap.of("command", "undeleteWorkspace",
				"params", new WorkspaceIdentity().withId(1L))));
		wa2.authorizeRead(strat, USER2, Arrays.asList("1", "3"));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 3, "n", USER2);

		//check fencepost error
		failMultipleRead(wa2, strat, USER2, Arrays.asList("1", "3"),
				new UJSAuthorizationException(String.format(
				"User %s cannot read workspace 3", USER2)));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 3, "w", USER2);
		wa2.authorizeRead(strat, USER2, Arrays.asList("1", "3"));
		JSONRPCLayerTestUtils.setPermissions(WSC1, 3, "a", USER2);
		wa2.authorizeRead(strat, USER2, Arrays.asList("1", "3"));
		
		//test globally readable workspaces
		JSONRPCLayerTestUtils.setPermissions(WSC1, 3, "n", USER2);
		failMultipleRead(wa2, strat, USER2, Arrays.asList("1", "3"),
				new UJSAuthorizationException(String.format(
				"User %s cannot read workspace 3", USER2)));
		WSC1.setGlobalPermission(new SetGlobalPermissionsParams().withId(3L)
				.withNewPermission("r"));
		wa2.authorizeRead(strat, USER2, Arrays.asList("1", "3"));
		
		
		failMultipleRead(wa1, strat, USER1, Arrays.asList("1", "2", "3", "4",
				"5", "6", "7", "8", "9", "10", "11"),
				new UJSAuthorizationException(
						"No more than 10 workspace IDs may be specified"));
		failMultipleRead(wa1, strat, USER1, Arrays.asList("1", "2", "3", "4",
				"5", "6", "7", "8", "9", "foo"),
				new UJSAuthorizationException(
						"The string foo is not a valid integer workspace ID"));
		failMultipleRead(wa1, strat, USER2, Arrays.asList("1"),
				new IllegalStateException("A programming error occured: the " +
						"token username and the supplied username do not " +
						"match"));
		
		failMultipleRead(wa1, new AuthorizationStrategy("foo"), USER1,
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
