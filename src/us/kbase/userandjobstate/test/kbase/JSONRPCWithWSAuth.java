package us.kbase.userandjobstate.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthUser;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.TestException;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.userandjobstate.CreateJobParams;
import us.kbase.userandjobstate.InitProgress;
import us.kbase.userandjobstate.UserAndJobStateClient;
import us.kbase.userandjobstate.UserAndJobStateServer;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.WorkspaceServer;

import com.mongodb.DB;

public class JSONRPCWithWSAuth extends JSONRPCLayerTestUtils {
	
	public static final Map<String, String> MTMAP =
			new HashMap<String, String>();
	
	public static final String KBWS = "kbaseworkspace";
	
	public static MongoController MONGO;
	
	public static WorkspaceServer WS;
	public static WorkspaceClient WSC1;
	public static WorkspaceClient WSC2;
	
	public static UserAndJobStateServer UJS;
	public static UserAndJobStateClient UJSC1;
	public static UserAndJobStateClient UJSC2;
	
	public static AuthUser U1;
	public static AuthUser U2;
	public static String TOKEN1;
	public static String TOKEN2;
	
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
		
		WS = startupWorkspaceServer(mongohost, WS_DB_NAME, "ws_types",
				user1, user2, p1);
		final int wsport = WS.getServerPort();
		try {
			WSC1 = new WorkspaceClient(new URL("http://localhost:" + wsport),
					user1, p1);
		} catch (UnauthorizedException ue) {
			throw new TestException("Unable to login with test.user1: " + user1 +
					"\nPlease check the credentials in the test configuration.", ue);
		}
		try {
			WSC2 = new WorkspaceClient(new URL("http://localhost:" + wsport),
					user2, p2);
		} catch (UnauthorizedException ue) {
			throw new TestException("Unable to login with test.user2: " + user2 +
					"\nPlease check the credentials in the test configuration.", ue);
		}
		WSC1.setIsInsecureHttpConnectionAllowed(true);
		WSC2.setIsInsecureHttpConnectionAllowed(true);
		
		UJS = startUpUJSServer("localhost:" + MONGO.getServerPort(),
				"http://localhost:" + WS.getServerPort(),
				JOB_DB_NAME, user1, p1);
		int ujsport = UJS.getServerPort();
		System.out.println("Started UJS test server on port " + ujsport);
		System.out.println("Starting tests");
		UJSC1 = new UserAndJobStateClient(new URL("http://localhost:" + ujsport),
				user1, p1);
		UJSC2 = new UserAndJobStateClient(new URL("http://localhost:" + ujsport),
				user2, p2);
		UJSC1.setIsInsecureHttpConnectionAllowed(true);
		UJSC2.setIsInsecureHttpConnectionAllowed(true);
		TOKEN1 = UJSC1.getToken().toString();
		TOKEN2 = UJSC2.getToken().toString();
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
	
	@Test
	public void testCreateJob() throws Exception {
		WSC1.createWorkspace(new CreateWorkspaceParams().withWorkspace("foo"));
		String id = UJSC1.createJob2(new CreateJobParams().withAuthstrat(KBWS)
				.withAuthparam("1"));
		checkJob(UJSC1, id, "created", null, null, null, null, null, null,
				null, null, null, null, null, KBWS, "1", MTMAP);
		
		failCreateJob(UJSC1, "foo", "1",
				"Invalid authorization strategy: foo");
		failCreateJob(UJSC1, KBWS, "foo",
				"The string foo is not a valid integer workspace ID");
		failCreateJob(UJSC1, KBWS, "2",
				"Error contacting the workspace service to get " +
				"permissions: No workspace with id 2 exists");
		
		WSC1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("foo1"));
		String id2 = UJSC1.createJob2(new CreateJobParams().withAuthstrat(KBWS)
				.withAuthparam("2"));
		checkJob(UJSC1, id2, "created", null, null, null, null, null, null,
				null, null, null, null, null, KBWS, "2", MTMAP);
		
		failCreateJob(UJSC2, KBWS, "1", String.format(
				"User %s cannot write to workspace 1", U2.getUserId()));
		
		setPermissions(WSC1, 1, "r", U2.getUserId());
		failCreateJob(UJSC2, KBWS, "1", String.format(
				"User %s cannot write to workspace 1", U2.getUserId()));
		
		setPermissions(WSC1, 1, "w", U2.getUserId());
		String id3 = UJSC2.createJob2(new CreateJobParams().withAuthstrat(KBWS)
				.withAuthparam("1"));
		checkJob(UJSC1, id3, "created", null, null, null, null, null, null,
				null, null, null, null, null, KBWS, "1", MTMAP);
		setPermissions(WSC1, 1, "a", U2.getUserId());
		String id4 = UJSC2.createJob2(new CreateJobParams().withAuthstrat(KBWS)
				.withAuthparam("1"));
		checkJob(UJSC1, id4, "created", null, null, null, null, null, null,
				null, null, null, null, null, KBWS, "1", MTMAP);
	}
	
	@Test
	public void testGetJob() throws Exception {
		String user1 = U1.getUserId();
		String user2 = U2.getUserId();
		InitProgress noprog = new InitProgress().withPtype("none");
		List<String> mtl = new LinkedList<String>();
		
		WSC1.createWorkspace(new CreateWorkspaceParams().withWorkspace("foo"));
		setPermissions(WSC1, 1, "w", user2);
		
		//test that deleting a workspace keeps the job visible to the owner
		String id =  UJSC1.createJob2(new CreateJobParams()
			.withAuthstrat(KBWS).withAuthparam("1"));
		checkJob(UJSC1, id, "created", null, null, null, null, null, null,
				null, null, null, null, null, KBWS, "1", MTMAP);
		assertThat("owner ok", UJSC1.getJobOwner(id), is(user1));
		assertThat("shared list ok", UJSC1.getJobShared(id), is(mtl));
		checkJob(UJSC2, id, "created", null, null, null, null, null, null,
				null, null, null, null, null, KBWS, "1", MTMAP);
		assertThat("owner ok", UJSC2.getJobOwner(id), is(user1));
		
		WSC1.deleteWorkspace(new WorkspaceIdentity().withId(1L));
		final String err = String.format(
				"There is no job %s viewable by user %s", id, user2);
		failGetJob(UJSC2, id, err);
		failGetJobOwner(UJSC2, id, err);
		failGetJobShared(UJSC2, id, err);
		
		UJSC1.startJob(id, TOKEN2, "stat1", "desc1", noprog, null);
		UJSC1.updateJob(id, TOKEN2, "up stat2", null);
		UJSC1.completeJob(id, TOKEN2, "c stat2", null, null);
		checkJob(UJSC1, id, "complete", "c stat2", user2, "desc1",
				"none", null, null, null, 1L, 0L, null, null, KBWS, "1", MTMAP);
		assertThat("owner ok", UJSC1.getJobOwner(id), is(user1));
		assertThat("shared list ok", UJSC1.getJobShared(id), is(mtl));
		
		WSC1.undeleteWorkspace(new WorkspaceIdentity().withId(1L));
		checkJob(UJSC2, id, "complete", "c stat2", user2, "desc1",
				"none", null, null, null, 1L, 0L, null, null, KBWS, "1", MTMAP);
		assertThat("owner ok", UJSC2.getJobOwner(id), is(user1));
		
		setPermissions(WSC1, 1, "n", user2);
		failGetJob(UJSC2, id, err);
		failGetJobOwner(UJSC2, id, err);
		failGetJobShared(UJSC2, id, err);
		
		setPermissions(WSC1, 1, "w", user2);
		checkJob(UJSC2, id, "complete", "c stat2", user2, "desc1",
				"none", null, null, null, 1L, 0L, null, null, KBWS, "1", MTMAP);
		assertThat("owner ok", UJSC2.getJobOwner(id), is(user1));
		
		setPermissions(WSC1, 1, "a", user2);
		checkJob(UJSC2, id, "complete", "c stat2", user2, "desc1",
				"none", null, null, null, 1L, 0L, null, null, KBWS, "1", MTMAP);
		assertThat("owner ok", UJSC2.getJobOwner(id), is(user1));
		
	}

}
