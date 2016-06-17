package us.kbase.userandjobstate.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.net.URL;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.DB;

import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.Tuple2;
import us.kbase.common.service.UObject;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.userandjobstate.CreateJobParams;
import us.kbase.userandjobstate.InitProgress;
import us.kbase.userandjobstate.Result;
import us.kbase.userandjobstate.Results;
import us.kbase.userandjobstate.UserAndJobStateClient;
import us.kbase.userandjobstate.UserAndJobStateServer;
import us.kbase.userandjobstate.jobstate.JobResults;
import us.kbase.userandjobstate.test.FakeJob;

/*
 * These tests are specifically for testing the JSON-RPC communications between
 * the client, up to the invocation of the {@link us.kbase.userandjobstate.userstate.UserState}
 * and {@link us.kbase.userandjobstate.jobstate.JobState}
 * methods. As such they do not test the full functionality of the methods;
 * {@link us.kbase.userandjobstate.userstate.test.UserStateTests} and
 * {@link us.kbase.userandjobstate.jobstate.test.JobStateTests} handles that.
 */
public class JSONRPCLayerTest extends JSONRPCLayerTestUtils {
	
	//TODO NOW implement and test 3 new methods
	
	private static UserAndJobStateServer SERVER = null;
	private static UserAndJobStateClient CLIENT1 = null;
	private static String USER1 = null;
	private static UserAndJobStateClient CLIENT2 = null;
	private static String USER2 = null;
	private static String TOKEN1;
	private static String TOKEN2;
	
	private static String DEF = "DEFAULT";
	private static Map<String, String> MTMAP = new HashMap<String, String>();
	
	private final static String DB_NAME = "JSONRPCLayerTest_DB";
	
	private static MongoController mongo;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		USER1 = System.getProperty("test.user1");
		USER2 = System.getProperty("test.user2");
		String p1 = System.getProperty("test.pwd1");
		String p2 = System.getProperty("test.pwd2");

		mongo = new MongoController(
				TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()));
		System.out.println("Using Mongo temp dir " + mongo.getTempDir());
		
		SERVER = startUpUJSServer("localhost:" + mongo.getServerPort(),
				null, DB_NAME, USER1, p1);
		int port = SERVER.getServerPort();
		System.out.println("Started test server on port " + port);
		System.out.println("Starting tests");
		CLIENT1 = new UserAndJobStateClient(new URL("http://localhost:" + port), USER1, p1);
		CLIENT2 = new UserAndJobStateClient(new URL("http://localhost:" + port), USER2, p2);
		CLIENT1.setIsInsecureHttpConnectionAllowed(true);
		CLIENT2.setIsInsecureHttpConnectionAllowed(true);
		TOKEN1 = CLIENT1.getToken().toString();
		TOKEN2 = CLIENT2.getToken().toString();
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		if (SERVER != null) {
			System.out.print("Killing server... ");
			SERVER.stopServer();
			System.out.println("Done");
		}
		if (mongo != null) {
			mongo.destroy(TestCommon.getDeleteTempFiles());
		}
	}
	
	@Before
	public void clearDB() throws Exception {
		DB db = GetMongoDB.getDB("localhost:" + mongo.getServerPort(),
				DB_NAME);
		TestCommon.destroyDB(db);
	}
	
	@Test
	public void ver() throws Exception {
		assertThat("got correct version", CLIENT1.ver(), is("0.2.0-dev"));
	}
	
	
	@Test
	public void unicode() throws Exception {
		int[] char1 = {11614};
		String uni = new String(char1, 0, 1);
		CLIENT1.setState("uni", "key", new UObject(uni));
		assertThat("get unicode back",
				CLIENT1.getState("uni", "key", 0L)
				.asClassInstance(Object.class),
				is((Object) uni));
		CLIENT1.removeState("uni", "key");
	}
	
	@Test
	public void getSetListStateService() throws Exception {
		List<Integer> data = Arrays.asList(1, 2, 3, 5, 8, 13);
		List<Integer> data2 = Arrays.asList(42);
		CLIENT1.setState("serv1", "key1", new UObject(data));
		CLIENT2.setState("serv1", "2key1", new UObject(data2));
		CLIENT1.setStateAuth(TOKEN2, "akey1", new UObject(data));
		CLIENT2.setStateAuth(TOKEN1, "2akey1", new UObject(data2));
		
		succeedGetHasState(CLIENT1, "serv1", "key1", 0L, data);
		succeedGetHasState(CLIENT2, "serv1", "2key1", 0L, data2);
		succeedGetHasState(CLIENT1, USER2, "akey1", 1L, data);
		succeedGetHasState(CLIENT2, USER1, "2akey1", 1L, data2);

		CLIENT1.setState("serv1", "key2", new UObject(data));
		CLIENT1.setState("serv2", "key", new UObject(data));
		CLIENT1.setStateAuth(TOKEN2, "akey2", new UObject(data));
		CLIENT1.setStateAuth(TOKEN1, "akey", new UObject(data));
		assertThat("get correct keys",
				new HashSet<String>(CLIENT1.listState("serv1", 0L)),
				is(new HashSet<String>(Arrays.asList("key1", "key2"))));
		assertThat("get correct keys",
				new HashSet<String>(CLIENT1.listState("serv2", 0L)),
				is(new HashSet<String>(Arrays.asList("key"))));
		assertThat("get correct keys",
				new HashSet<String>(CLIENT1.listState(USER2, 1L)),
				is(new HashSet<String>(Arrays.asList("akey1", "akey2"))));
		assertThat("get correct keys",
				new HashSet<String>(CLIENT1.listState(USER1, 1L)),
				is(new HashSet<String>(Arrays.asList("akey"))));
		assertThat("get correct services",
				new HashSet<String>(CLIENT1.listStateServices(0L)),
				is(new HashSet<String>(Arrays.asList("serv1", "serv2"))));
		assertThat("get correct services",
				new HashSet<String>(CLIENT1.listStateServices(1L)),
				is(new HashSet<String>(Arrays.asList(USER1, USER2))));
		
		CLIENT1.removeState("serv1", "key1");
		CLIENT1.removeStateAuth(TOKEN2, "akey1");
		failGetHasState(CLIENT1, "serv1", "key1", 0L);
		failGetHasState(CLIENT1, USER2, "akey1", 1L);
	}
	
	private void failGetHasState(UserAndJobStateClient client, String service,
			String key, long auth) throws Exception {
		String exp = String.format("There is no key %s for the %sauthorized service %s",
				key, auth == 1 ? "" : "un", service);
		try {
			client.getState(service, key, auth);
			fail("got deleted state");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exp));
		}
		Tuple2<Long, UObject> t2 = client.getHasState(service, key, auth);
		assertThat("key does not exist", t2.getE1(), is(0L));
		assertThat("null value", t2.getE2(),
				is((Object) null));
		assertThat("has key returns false", client.hasState(service, key, auth),
				is(0L));
	}
	
	private void succeedGetHasState(UserAndJobStateClient client, String service,
			String key, long auth, Object data) throws Exception {
		assertThat("get correct data back",
				client.getState(service, key, auth).asClassInstance(Object.class),
				is(data));
		Tuple2<Long, UObject> t2 = client.getHasState(service, key, auth);
		assertThat("key exists", t2.getE1(), is(1L));
		assertThat("correct value", t2.getE2().asClassInstance(Object.class),
				is(data));
		assertThat("has key returns true", client.hasState(service, key, auth),
				is(1L));
	}
	
	@Test
	public void badToken() throws Exception {
		try {
			CLIENT1.setStateAuth(null, "key", new UObject("foo"));
			fail("set state w/ bad token");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Service token cannot be null or the empty string"));
		}
		try {
			CLIENT1.setStateAuth("", "key", new UObject("foo"));
			fail("set state w/ bad token");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Service token cannot be null or the empty string"));
		}
		try {
			CLIENT1.setStateAuth("boogabooga", "key", new UObject("foo"));
			fail("set state w/ bad token");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Auth token is in the incorrect format, near 'boogabooga'"));
		}
		try {
			CLIENT1.setStateAuth(TOKEN2 + "a", "key", new UObject("foo"));
			fail("set state w/ bad token");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Service token is invalid"));
		}
		try {
			CLIENT1.removeStateAuth(null, "key");
			fail("set state w/ bad token");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Service token cannot be null or the empty string"));
		}
		try {
			CLIENT1.removeStateAuth("", "key");
			fail("set state w/ bad token");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Service token cannot be null or the empty string"));
		}
		try {
			CLIENT1.removeStateAuth("boogabooga", "key");
			fail("set state w/ bad token");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Auth token is in the incorrect format, near 'boogabooga'"));
		}
		try {
			CLIENT1.removeStateAuth(TOKEN2 + "a", "key");
			fail("set state w/ bad token");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is("Service token is invalid"));
		}
	}
	
	private String[] getNearbyTimes() {
		SimpleDateFormat dateform =
				new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
		SimpleDateFormat dateformutc =
				new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		dateformutc.setTimeZone(TimeZone.getTimeZone("UTC"));
		Date nf = new Date(new Date().getTime() + 10000);
		Date np =  new Date(new Date().getTime() - 10);
		String[] nearfuture = new String[3];
		nearfuture[0] = dateform.format(nf);
		nearfuture[1] = dateformutc.format(nf);
		nearfuture[2] = dateform.format(np);
		return nearfuture;
	}
	
	@Test
	public void createAndStartJob() throws Exception {
		String[] nearfuture = getNearbyTimes();
		
		@SuppressWarnings("deprecation")
		String jobid = CLIENT1.createJob();
		checkJob(CLIENT1, jobid, "created",null, null, null, null,
				null, null, null, null, null, null, null, DEF, DEF, MTMAP);
		CLIENT1.startJob(jobid, TOKEN2, "new stat", "ne desc",
				new InitProgress().withPtype("none"), null);
		checkJob(CLIENT1, jobid, "started", "new stat", USER2, "ne desc",
				"none", null, null, null, 0L, 0L, null, null, DEF, DEF, MTMAP);
		
		jobid = CLIENT1.createJob2(new CreateJobParams().withAuthstrat(null));
		CLIENT1.startJob(jobid, TOKEN2, "new stat2", "ne desc2",
				new InitProgress().withPtype("percent"), nearfuture[0]);
		checkJob(CLIENT1, jobid, "started", "new stat2", USER2,
				"ne desc2", "percent", 0L, 100L, nearfuture[1], 0L, 0L, null,
				null, DEF, DEF, MTMAP);
		
		jobid = CLIENT1.createJob2(new CreateJobParams().withAuthstrat(""));
		CLIENT1.startJob(jobid, TOKEN2, "new stat3", "ne desc3",
				new InitProgress().withPtype("task").withMax(5L), null);
		checkJob(CLIENT1, jobid, "started", "new stat3", USER2, "ne desc3",
				"task", 0L, 5L, null, 0L, 0L, null, null, DEF, DEF, MTMAP);
		
		startJobBadArgs(null, TOKEN2, "s", "d", new InitProgress().withPtype("none"),
				null, "id cannot be null or the empty string", true);
		startJobBadArgs("", TOKEN2, "s", "d", new InitProgress().withPtype("none"),
				null, "id cannot be null or the empty string", true);
		startJobBadArgs("aaaaaaaaaaaaaaaaaaaa", TOKEN2, "s", "d",
				new InitProgress().withPtype("none"),
				null, "Job ID aaaaaaaaaaaaaaaaaaaa is not a legal ID", true);
		
		jobid = CLIENT1.createJob2(new CreateJobParams()
				.withAuthstrat("DEFAULT"));
		startJobBadArgs(jobid, null, "s", "d", new InitProgress().withPtype("none"),
				null, "Service token cannot be null or the empty string");
		startJobBadArgs(jobid, "foo", "s", "d", new InitProgress().withPtype("none"),
				null, "Auth token is in the incorrect format, near 'foo'");
		startJobBadArgs(jobid, TOKEN2 + "a", "s", "d", new InitProgress().withPtype("none"),
				null, "Service token is invalid");
		
		startJobBadArgs(jobid, TOKEN2, "s", "d",
				new InitProgress().withPtype("none"),
				nearfuture[2], "The estimated completion date must be in the future", true);
		startJobBadArgs(jobid, TOKEN2, "s", "d",
				new InitProgress().withPtype("none"),
				"2200-12-30T23:30:54-8000",
				"Unparseable date: Invalid format: \"2200-12-30T23:30:54-8000\" is malformed at \"8000\"", true);
		startJobBadArgs(jobid, TOKEN2, "s", "d",
				new InitProgress().withPtype("none"),
				"2200-12-30T123:30:54-0800",
				"Unparseable date: Invalid format: \"2200-12-30T123:30:54-0800\" is malformed at \"3:30:54-0800\"", true);
		
		startJobBadArgs(jobid, TOKEN2, "s", "d", null,
				null, "InitProgress cannot be null");
		InitProgress ip = new InitProgress().withPtype("none");
		ip.setAdditionalProperties("foo", "bar");
		startJobBadArgs(jobid, TOKEN2, "s", "d", ip,
				null, "Unexpected arguments in InitProgress: foo");
		startJobBadArgs(jobid, TOKEN2, "s", "d", new InitProgress().withPtype(null),
				null, "Progress type cannot be null");
		startJobBadArgs(jobid, TOKEN2, "s", "d", new InitProgress().withPtype("foo"),
				null, "No such progress type: foo");
		startJobBadArgs(jobid, TOKEN2, "s", "d", new InitProgress().withPtype("task")
				.withMax(null),
				null, "Max progress cannot be null for task based progress");
		startJobBadArgs(jobid, TOKEN2, "s", "d", new InitProgress().withPtype("task")
				.withMax(-1L),
				null, "The maximum progress for the job must be > 0");
		startJobBadArgs(jobid, TOKEN2, "s", "d", new InitProgress().withPtype("task")
				.withMax(((long) Integer.MAX_VALUE) + 1),
				null, "Max progress can be no greater than " + Integer.MAX_VALUE);
		
		
		jobid = CLIENT1.createAndStartJob(TOKEN2, "cs stat", "cs desc",
				new InitProgress().withPtype("none"), nearfuture[0]);
		checkJob(CLIENT1, jobid, "started", "cs stat", USER2,
				"cs desc", "none", null, null, nearfuture[1], 0L, 0L, null,
				null, DEF, DEF, MTMAP);
		
		jobid = CLIENT1.createAndStartJob(TOKEN2, "cs stat2", "cs desc2",
				new InitProgress().withPtype("percent"), null);
		checkJob(CLIENT1, jobid, "started", "cs stat2", USER2, "cs desc2",
				"percent", 0L, 100L, null, 0L, 0L, null, null, DEF, DEF,
				MTMAP);
		
		jobid = CLIENT1.createAndStartJob(TOKEN2, "cs stat3", "cs desc3",
				new InitProgress().withPtype("task").withMax(5L), null);
		checkJob(CLIENT1, jobid, "started", "cs stat3", USER2, "cs desc3",
				"task", 0L, 5L, null, 0L, 0L, null, null, DEF, DEF, MTMAP);
		
		failCreateJob("kbaseworkspace", "1", "The UJS is not configured to " +
				"delegate authorization to the workspace service");
		failCreateJob("foo", "1", "Invalid authorization strategy: foo");
	}
	
	@Test
	public void metadata() throws Exception {
		Map<String, String> m = new HashMap<String, String>();
		InitProgress noprog = new InitProgress().withPtype("none");
		m.put("bar", "baz");
		m.put("whoo", "wee");
		String id1 = CLIENT1.createJob2(new CreateJobParams().withMeta(null));
		String id2 = CLIENT1.createJob2(new CreateJobParams().withMeta(
				new HashMap<String, String>()));
		String id3 = CLIENT1.createJob2(new CreateJobParams().withMeta(m));
		
		checkJob(CLIENT1, id1, "created", null, null, null, null, null, null,
				null, null, null, null, null, DEF, DEF, MTMAP);
		checkJob(CLIENT1, id2, "created", null, null, null, null, null, null,
				null, null, null, null, null, DEF, DEF, MTMAP);
		checkJob(CLIENT1, id3, "created", null, null, null, null, null, null,
				null, null, null, null, null, DEF, DEF, m);
		
		CLIENT1.startJob(id1, TOKEN2, "stat1", "desc1", noprog, null);
		CLIENT1.startJob(id3, TOKEN2, "stat3", "desc2", noprog, null);
		checkJob(CLIENT1, id1, "started", "stat1", USER2, "desc1", "none",
				null, null, null, 0L, 0L, null, null, DEF, DEF, MTMAP);
		checkJob(CLIENT1, id3, "started", "stat3", USER2, "desc2", "none",
				null, null, null, 0L, 0L, null, null, DEF, DEF, m);
		
		CLIENT1.updateJob(id1, TOKEN2, "stat1-2", null);
		CLIENT1.updateJob(id3, TOKEN2, "stat3-2", null);
		checkJob(CLIENT1, id1, "started", "stat1-2", USER2, "desc1", "none",
				null, null, null, 0L, 0L, null, null, DEF, DEF, MTMAP);
		checkJob(CLIENT1, id3, "started", "stat3-2", USER2, "desc2", "none",
				null, null, null, 0L, 0L, null, null, DEF, DEF, m);

		CLIENT1.completeJob(id1, TOKEN2, "stat1-3", null, null);
		CLIENT1.completeJob(id3, TOKEN2, "stat3-3", null, null);
		checkJob(CLIENT1, id1, "complete", "stat1-3", USER2, "desc1", "none",
				null, null, null, 1L, 0L, null, null, DEF, DEF, MTMAP);
		checkJob(CLIENT1, id3, "complete", "stat3-3", USER2, "desc2", "none",
				null, null, null, 1L, 0L, null, null, DEF, DEF, m);
	}
	
	private void failCreateJob(String authstrat, String param, String exp)
			throws Exception {
		try {
			CLIENT1.createJob2(new CreateJobParams().withAuthstrat(authstrat)
					.withAuthparam(param));
			fail("created job with bad authstrat");
		} catch (ServerException e) {
			assertThat("incorrect exception message. Server trace: " +
					e.getData(), e.getLocalizedMessage(), is(exp));
		}
	}
	
	private void startJobBadArgs(String jobid, String token, String stat, String desc,
			InitProgress prog, String estCompl, String exception) throws Exception {
		startJobBadArgs(jobid, token, stat, desc, prog, estCompl, exception, false);
	}
	
	private void startJobBadArgs(String jobid, String token, String stat, String desc,
			InitProgress prog, String estCompl, String exception, boolean badid)
			throws Exception {
		try {
			CLIENT1.startJob(jobid, token, stat, desc, prog, estCompl);
			fail("started job w/ bad args");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
		if (badid) {
			return;
		}
		try {
			CLIENT1.createAndStartJob(token, stat, desc, prog, estCompl);
			fail("started job w/ bad args");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
	}
	
	@Test
	public void getJobInfoBadArgs() throws Exception {
		failGetJob(CLIENT1, null, "id cannot be null or the empty string");
		failGetJob(CLIENT1, "", "id cannot be null or the empty string");
		failGetJob(CLIENT1, "foo", "Job ID foo is not a legal ID");
		
		String jobid = CLIENT1.createJob2(new CreateJobParams());
		if (jobid.charAt(0) == 'a') {
			jobid = "b" + jobid.substring(1);
		} else {
			jobid = "a" + jobid.substring(1);
		}
		failGetJob(CLIENT1, jobid, String.format(
				"There is no job %s viewable by user %s", jobid, USER1));
	}
	
	@Test
	public void updateJob() throws Exception {
		String[] nearfuture = getNearbyTimes();
		String jobid = CLIENT1.createAndStartJob(TOKEN2, "up stat", "up desc",
				new InitProgress().withPtype("none"), null);
		CLIENT1.updateJob(jobid, TOKEN2, "up stat2", null);
		checkJob(CLIENT1, jobid, "started", "up stat2", USER2, "up desc",
				"none", null, null, null, 0L, 0L, null, null, DEF, DEF, MTMAP);
		CLIENT1.updateJobProgress(jobid, TOKEN2, "up stat3", 40L, null);
		checkJob(CLIENT1, jobid, "started", "up stat3", USER2, "up desc",
				"none", null, null, null, 0L, 0L, null, null, DEF, DEF, MTMAP);
		CLIENT1.updateJobProgress(jobid, TOKEN2, "up stat3", null, nearfuture[0]);
		checkJob(CLIENT1, jobid, "started", "up stat3", USER2,
				"up desc", "none", null, null, nearfuture[1], 0L, 0L, null,
				null, DEF, DEF, MTMAP);
		
		jobid = CLIENT1.createAndStartJob(TOKEN2, "up2 stat", "up2 desc",
				new InitProgress().withPtype("percent"), null);
		CLIENT1.updateJobProgress(jobid, TOKEN2, "up2 stat2", 40L, null);
		checkJob(CLIENT1, jobid, "started", "up2 stat2", USER2, "up2 desc",
				"percent", 40L, 100L, null, 0L, 0L, null, null, DEF, DEF,
				MTMAP);
		CLIENT1.updateJob(jobid, TOKEN2, "up2 stat3", nearfuture[0]);
		checkJob(CLIENT1, jobid, "started", "up2 stat3", USER2, "up2 desc",
				"percent", 40L, 100L, nearfuture[1], 0L, 0L, null,
				null, DEF, DEF, MTMAP);
		CLIENT1.updateJobProgress(jobid, TOKEN2, "up2 stat4", 70L, null);
		checkJob(CLIENT1, jobid, "started", "up2 stat4", USER2,
				"up2 desc", "percent", 100L, 100L, nearfuture[1], 0L, 0L, null,
				null, DEF, DEF, MTMAP);
		
		jobid = CLIENT1.createAndStartJob(TOKEN2, "up3 stat", "up3 desc",
				new InitProgress().withPtype("task").withMax(42L), null);
		CLIENT1.updateJobProgress(jobid, TOKEN2, "up3 stat2", 30L, nearfuture[0]);
		checkJob(CLIENT1, jobid, "started", "up3 stat2", USER2, "up3 desc",
				"task", 30L, 42L, nearfuture[1], 0L, 0L, null, null, DEF, DEF,
				MTMAP);
		CLIENT1.updateJob(jobid, TOKEN2, "up3 stat3", null);
		checkJob(CLIENT1, jobid, "started", "up3 stat3", USER2,
				"up3 desc", "task", 30L, 42L, nearfuture[1], 0L, 0L, null,
				null, DEF, DEF, MTMAP);
		CLIENT1.updateJobProgress(jobid, TOKEN2, "up3 stat4", 15L, null);
		checkJob(CLIENT1, jobid, "started", "up3 stat4", USER2,
				"up3 desc", "task", 42L, 42L, nearfuture[1], 0L, 0L, null,
				null, DEF, DEF, MTMAP);
		
		jobid = CLIENT1.createAndStartJob(TOKEN2, "up4 stat", "up4 desc",
				new InitProgress().withPtype("none"), null);
		updateJobBadArgs(jobid, TOKEN2, "up4 stat2", -1L, null, "progress cannot be negative");
		updateJobBadArgs(jobid, TOKEN2, "up4 stat2",
				(long) Integer.MAX_VALUE + 1, null,
				"Max progress can be no greater than " + Integer.MAX_VALUE);
		
		updateJobBadArgs(null, TOKEN2, "s", null,
				"id cannot be null or the empty string");
		updateJobBadArgs("", TOKEN2, "s", null,
				"id cannot be null or the empty string");
		updateJobBadArgs("aaaaaaaaaaaaaaaaaaaa", TOKEN2, "s", null,
				"Job ID aaaaaaaaaaaaaaaaaaaa is not a legal ID");
		
		//test a few bad times
		updateJobBadArgs(jobid, TOKEN2, "s", nearfuture[2],
				"The estimated completion date must be in the future");
		updateJobBadArgs(jobid, TOKEN2, "s", "2200-12-30T123:30:54-0800",
				"Unparseable date: Invalid format: \"2200-12-30T123:30:54-0800\" is malformed at \"3:30:54-0800\"");
		updateJobBadArgs(jobid, TOKEN2, "s", "2013-04-26T25:52:06-0800",
				"Unparseable date: Cannot parse \"2013-04-26T25:52:06-0800\": Value 25 for hourOfDay must be in the range [0,23]");
		updateJobBadArgs(jobid, TOKEN2, "s", "2013-04-26T23:52:06-8000",
				"Unparseable date: Invalid format: \"2013-04-26T23:52:06-8000\" is malformed at \"8000\"");
		updateJobBadArgs(jobid, TOKEN2, "s", "2013-04-35T23:52:06-0800",
				"Unparseable date: Cannot parse \"2013-04-35T23:52:06-0800\": Value 35 for dayOfMonth must be in the range [1,30]");
		updateJobBadArgs(jobid, TOKEN2, "s", "2013-13-26T23:52:06-0800",
				"Unparseable date: Cannot parse \"2013-13-26T23:52:06-0800\": Value 13 for monthOfYear must be in the range [1,12]");
		updateJobBadArgs(jobid, TOKEN2, "s", "2013-13-26T23:52:06.1111-0800",
				"Unparseable date: Invalid format: \"2013-13-26T23:52:06.1111-0800\" is malformed at \"1-0800\"");
		updateJobBadArgs(jobid, TOKEN2, "s", "2013-13-26T23:52:06.-0800",
				"Unparseable date: Invalid format: \"2013-13-26T23:52:06.-0800\" is malformed at \".-0800\"");
		updateJobBadArgs(jobid, TOKEN2, "s", "2013-12-26T23:52:06.55",
				"Unparseable date: Invalid format: \"2013-12-26T23:52:06.55\" is too short");
		updateJobBadArgs(jobid, TOKEN2, "s", "2013-12-26T23:52-0800",
				"Unparseable date: Invalid format: \"2013-12-26T23:52-0800\" is malformed at \"-0800\"");
		
		updateJobBadArgs(jobid, null, "s", null,
				"Service token cannot be null or the empty string");
		updateJobBadArgs(jobid, "foo", "s", null,
				"Auth token is in the incorrect format, near 'foo'");
		updateJobBadArgs(jobid, TOKEN1, "s", null, String.format(
				"There is no uncompleted job %s for user %s started by service %s",
				jobid, USER1, USER1));
		updateJobBadArgs(jobid, TOKEN2 + "a", "s", null,
				"Service token is invalid");
	}
	
	private void updateJobBadArgs(String jobid, String token, String status,
			Long prog, String estCompl, String exception) throws Exception {
		try {
			CLIENT1.updateJobProgress(jobid, token, status, prog, estCompl);
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
	}
	
	private void updateJobBadArgs(String jobid, String token, String status,
			String estCompl, String exception) throws Exception {
		try {
			CLIENT1.updateJob(jobid, token, status, estCompl);
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
		try {
			CLIENT1.updateJobProgress(jobid, token, status, 1L, estCompl);
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
	}
	
	@Test
	public void completeJob() throws Exception {
		String[] nearfuture = getNearbyTimes();
		String jobid = CLIENT1.createAndStartJob(TOKEN2, "c stat", "c desc",
				new InitProgress().withPtype("none"), null);
		CLIENT1.completeJob(jobid, TOKEN2, "c stat2", null, null);
		checkJob(CLIENT1, jobid, "complete", "c stat2", USER2, "c desc",
				"none", null, null, null, 1L, 0L, null, null, DEF, DEF, MTMAP);
		
		jobid = CLIENT1.createAndStartJob(TOKEN2, "c2 stat", "c2 desc",
				new InitProgress().withPtype("percent"), null);
		CLIENT1.updateJobProgress(jobid, TOKEN2, "c2 stat2", 40L, null);
		CLIENT1.completeJob(jobid, TOKEN2, "c2 stat3", "omg err",
				new Results());
		checkJob(CLIENT1, jobid, "error", "c2 stat3", USER2, "c2 desc",
				"percent", 100L, 100L, null, 1L, 1L, "omg err", new Results(),
				DEF, DEF, MTMAP);
		
		jobid = CLIENT1.createAndStartJob(TOKEN2, "c3 stat", "c3 desc",
				new InitProgress().withPtype("task").withMax(37L), null);
		CLIENT1.updateJobProgress(jobid, TOKEN2, "c3 stat2", 15L, nearfuture[0]);
		List<Result> r = new LinkedList<Result>();
		r.add(new Result().withUrl("some url").withServerType("server")
				.withId("an id").withDescription("desc"));
		Results res = new Results()
						.withShocknodes(Arrays.asList("node1", "node3"))
						.withShockurl("surl")
						.withWorkspaceids(Arrays.asList("ws1", "ws3"))
						.withWorkspaceurl("wurl")
						.withResults(r);
		CLIENT1.completeJob(jobid, TOKEN2, "c3 stat3", null, res);
		checkJob(CLIENT1, jobid, "complete", "c3 stat3", USER2, "c3 desc",
				"task", 37L, 37L, nearfuture[1], 1L, 0L, null, res, DEF, DEF,
				MTMAP);
		
		failCompleteJob(null, TOKEN2, "s", null, null,
				"id cannot be null or the empty string");
		failCompleteJob("", TOKEN2, "s", null, null,
				"id cannot be null or the empty string");
		failCompleteJob("aaaaaaaaaaaaaaaaaaaa", TOKEN2, "s", null, null,
				"Job ID aaaaaaaaaaaaaaaaaaaa is not a legal ID");
		
		failCompleteJob(jobid, null, "s", null, null,
				"Service token cannot be null or the empty string");
		failCompleteJob(jobid, "foo", "s", null, null,
				"Auth token is in the incorrect format, near 'foo'");
		failCompleteJob(jobid, TOKEN2 + "w", "s", null, null,
				"Service token is invalid");
		failCompleteJob(jobid, TOKEN1, "s", null, null, String.format(
				"There is no uncompleted job %s for user %s started by service %s",
				jobid, USER1, USER1));
		Results badres = new Results();
		badres.setAdditionalProperties("foo", "bar");
		failCompleteJob(jobid, TOKEN1, "s", null, badres,
				"Unexpected arguments in Results: foo");
		
		jobid = CLIENT1.createAndStartJob(TOKEN2, "bad res stat",
				"bad res desc", new InitProgress().withPtype("none"), null);
		Results res2 = new Results().withShockurl("");
		failCompleteJob(jobid, TOKEN2, "foo", "bar", res2, "shockurl cannot be the empty string");
		failCompleteJob(jobid, TOKEN2, "foo", "bar", res.withShockurl(null).withWorkspaceurl(""),
				"workspaceurl cannot be the empty string");
		failCompleteJob(jobid, TOKEN2, "foo", "bar", res.withWorkspaceurl(null)
				.withShocknodes(Arrays.asList("")),
				"shocknode cannot be null or the empty string");
		failCompleteJob(jobid, TOKEN2, "foo", "bar", res.withShocknodes(null)
				.withWorkspaceids(Arrays.asList("")),
				"workspaceid cannot be null or the empty string");
		
		Result r2 = new Result();
		r2.setAdditionalProperties("foo", "bar");
		List<Result> rl = new LinkedList<Result>();
		rl.add(r2);
		failCompleteJob(jobid, TOKEN2, "foo", "bar", res.withWorkspaceids(null)
				.withResults(rl), "Unexpected arguments in Result: foo");
		
		r2 = new Result().withServerType(null).withId("id").withUrl("url");
		rl.clear();
		rl.add(r2);
		failCompleteJob(jobid, TOKEN2, "foo", "bar", res,
				"servertype cannot be null or the empty string");
		r2.withServerType("");
		failCompleteJob(jobid, TOKEN2, "foo", "bar", res,
				"servertype cannot be null or the empty string");
		r2.withServerType(CHAR101);
		failCompleteJob(jobid, TOKEN2, "foo", "bar", res,
				"servertype exceeds the maximum length of 100");
		r2.withServerType("serv");
		
		r2.withId(null);
		failCompleteJob(jobid, TOKEN2, "foo", "bar", res,
				"id cannot be null or the empty string");
		r2.withId("");
		failCompleteJob(jobid, TOKEN2, "foo", "bar", res,
				"id cannot be null or the empty string");
		r2.withId(CHAR1001);
		failCompleteJob(jobid, TOKEN2, "foo", "bar", res,
				"id exceeds the maximum length of 1000");
		r2.withId("id");
		
		r2.withUrl(null);
		failCompleteJob(jobid, TOKEN2, "foo", "bar", res,
				"url cannot be null or the empty string");
		r2.withUrl("");
		failCompleteJob(jobid, TOKEN2, "foo", "bar", res,
				"url cannot be null or the empty string");
		r2.withUrl(CHAR1001);
		failCompleteJob(jobid, TOKEN2, "foo", "bar", res,
				"url exceeds the maximum length of 1000");
		r2.withUrl("url");
		
		r2.withDescription(CHAR1001);
		failCompleteJob(jobid, TOKEN2, "foo", "bar", res,
				"description exceeds the maximum length of 1000");
		
				
	}
	
	private void failCompleteJob(String jobid, String token, String status,
			String error, Results res, String exception) throws Exception {
		try {
			CLIENT1.completeJob(jobid, token, status, error, res);
			fail("Completed job with bad input");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
	}
	
	@Test
	public void deleteJob() throws Exception {
		String nojob = "There is no job %s viewable by user %s";
		
		InitProgress noprog = new InitProgress().withPtype("none");
		String jobid = CLIENT1.createAndStartJob(TOKEN2, "d stat", "d desc", noprog, null);
		CLIENT1.completeJob(jobid, TOKEN2, "d stat2", null, null);
		CLIENT1.deleteJob(jobid);
		failGetJob(CLIENT1, jobid, String.format(nojob, jobid, USER1));
		
		jobid = CLIENT1.createAndStartJob(TOKEN2, "e stat", "e desc", noprog, null);
		CLIENT1.completeJob(jobid, TOKEN2, "e stat2", "err", null);
		CLIENT1.deleteJob(jobid);
		failGetJob(CLIENT1, jobid, String.format(nojob, jobid, USER1));
		
		jobid = CLIENT1.createAndStartJob(TOKEN2, "d2 stat", "d2 desc", noprog, null);
		CLIENT1.forceDeleteJob(TOKEN2, jobid);
		failGetJob(CLIENT1, jobid, String.format(nojob, jobid, USER1));
		
		jobid = CLIENT1.createAndStartJob(TOKEN2, "d3 stat", "d3 desc", noprog, null);
		CLIENT1.updateJobProgress(jobid, TOKEN2, "d3 stat2", 3L, null);
		CLIENT1.forceDeleteJob(TOKEN2, jobid);
		failGetJob(CLIENT1, jobid, String.format(nojob, jobid, USER1));
		
		jobid = CLIENT1.createAndStartJob(TOKEN2, "d4 stat", "d4 desc", noprog, null);
		CLIENT1.completeJob(jobid, TOKEN2, "d4 stat2", null, null);
		CLIENT1.forceDeleteJob(TOKEN2, jobid);
		failGetJob(CLIENT1, jobid, String.format(nojob, jobid, USER1));
		
		jobid = CLIENT1.createAndStartJob(TOKEN2, "d5 stat", "d5 desc", noprog, null);
		CLIENT1.completeJob(jobid, TOKEN2, "d5 stat2", "err", null);
		CLIENT1.forceDeleteJob(TOKEN2, jobid);
		failGetJob(CLIENT1, jobid, String.format(nojob, jobid, USER1));
		
		jobid = CLIENT1.createJob2(new CreateJobParams());
		failToDeleteJob(jobid, String.format(
				"There is no completed job %s for user %s", jobid, USER1));
		failToDeleteJob(jobid, TOKEN2, String.format(
				"There is no job %s for user %s and service %s",
				jobid, USER1, USER2));
		CLIENT1.startJob(jobid, TOKEN2, "d6 stat", "d6 desc", noprog, null);
		failToDeleteJob(jobid, String.format(
				"There is no completed job %s for user %s", jobid, USER1));
		CLIENT1.updateJobProgress(jobid, TOKEN2, "d6 stat2", 3L, null);
		failToDeleteJob(jobid, String.format(
				"There is no completed job %s for user %s", jobid, USER1));
		
		failToDeleteJob(null, "id cannot be null or the empty string");
		failToDeleteJob("", "id cannot be null or the empty string");
		failToDeleteJob("aaaaaaaaaaaaaaaaaaaa",
				"Job ID aaaaaaaaaaaaaaaaaaaa is not a legal ID");
		
		failToDeleteJob(null, TOKEN2,
				"id cannot be null or the empty string");
		failToDeleteJob("", TOKEN2,
				"id cannot be null or the empty string");
		failToDeleteJob("aaaaaaaaaaaaaaaaaaaa", TOKEN2,
				"Job ID aaaaaaaaaaaaaaaaaaaa is not a legal ID");
		
		failToDeleteJob(jobid, null,
				"Service token cannot be null or the empty string", true);
		failToDeleteJob(jobid, "foo",
				"Auth token is in the incorrect format, near 'foo'");
		failToDeleteJob(jobid, TOKEN2 + 'w',
				"Service token is invalid");
		failToDeleteJob(jobid, TOKEN1, String.format(
				"There is no job %s for user %s and service %s",
				jobid, USER1, USER1));
	}
	
	private void failToDeleteJob(String jobid, String exception)
			throws Exception {
		failToDeleteJob(jobid, null, exception, false);
	}
	
	private void failToDeleteJob(String jobid, String token, String exception)
			throws Exception {
		failToDeleteJob(jobid, token, exception, false);
	}
	
	private void failToDeleteJob(String jobid, String token, String exception,
			boolean usenulltoken)
			throws Exception {
		try {
			if (!usenulltoken && token == null) {
				CLIENT1.deleteJob(jobid);
			} else {
				CLIENT1.forceDeleteJob(token, jobid);
			}
			fail("deleted job with bad args");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
	}
	
	@Test
	public void listServices() throws Exception {
		checkListServices(CLIENT2, new HashSet<String>());
		InitProgress noprog = new InitProgress().withPtype("none");
		String jobid1 = CLIENT1.createAndStartJob(TOKEN2, "ls stat", "ls desc",
				noprog, null);
		checkListServices(CLIENT1, new HashSet<String>(Arrays.asList(USER2)));
		String jobid2 = CLIENT1.createAndStartJob(TOKEN1, "ls2 stat",
				"ls2 desc", noprog, null);
		checkListServices(CLIENT1, new HashSet<String>(Arrays.asList(USER1, USER2)));
		checkListServices(CLIENT2, new HashSet<String>());
		CLIENT1.shareJob(jobid2, Arrays.asList(USER2));
		checkListServices(CLIENT2, new HashSet<String>(Arrays.asList(USER1)));
		CLIENT1.shareJob(jobid1, Arrays.asList(USER2));
		checkListServices(CLIENT2, new HashSet<String>(Arrays.asList(USER1, USER2)));
		CLIENT1.unshareJob(jobid2, Arrays.asList(USER2));
		checkListServices(CLIENT2, new HashSet<String>(Arrays.asList(USER2)));
		
		CLIENT1.forceDeleteJob(TOKEN1, jobid2);
		CLIENT1.forceDeleteJob(TOKEN2, jobid1);
	}
	
	private void checkListServices(UserAndJobStateClient client,
			Set<String> service) throws Exception {
		assertThat("got correct services", new HashSet<String>(client.listJobServices()),
				is(service));
	}
	
	@Test
	public void listJobs() throws Exception {
		InitProgress noprog = new InitProgress().withPtype("none");
		Set<FakeJob> empty = new HashSet<FakeJob>();
		
		checkListJobs(CLIENT2, USER1, null, empty);
		checkListJobs(CLIENT2, USER1, "", empty);
		checkListJobs(CLIENT2, USER1, "S", empty);
		checkListJobs(CLIENT2, USER1, "R", empty);
		checkListJobs(CLIENT2, USER1, "RS", empty);
		checkListJobs(CLIENT2, USER1, "C", empty);
		checkListJobs(CLIENT2, USER1, "CS", empty);
		checkListJobs(CLIENT2, USER1, "E", empty);
		checkListJobs(CLIENT2, USER1, "ES", empty);
		checkListJobs(CLIENT2, USER1, "RC", empty);
		checkListJobs(CLIENT2, USER1, "RCS", empty);
		checkListJobs(CLIENT2, USER1, "CE", empty);
		checkListJobs(CLIENT2, USER1, "CES", empty);
		checkListJobs(CLIENT2, USER1, "RE", empty);
		checkListJobs(CLIENT2, USER1, "RES", empty);
		checkListJobs(CLIENT2, USER1, "RCE", empty);
		checkListJobs(CLIENT2, USER1, "RCES", empty);
		checkListJobs(CLIENT2, USER1, "RCEX", empty);
		checkListJobs(CLIENT2, USER1, "RCEXS", empty);
		
		String jobid = CLIENT1.createAndStartJob(TOKEN1, "ljs stat", "ljs desc", noprog, null);
		FakeJob shared = new FakeJob(jobid, null, USER1, "started", null, "ljs desc",
				"none", null, null, "ljs stat", false, false, null, null);
		CLIENT1.shareJob(jobid, Arrays.asList(USER2));
		Set<FakeJob> setsharedonly = new HashSet<FakeJob>(Arrays.asList(shared)); 
		
		checkListJobs(CLIENT2, USER1, null, empty);
		checkListJobs(CLIENT2, USER1, "", empty);
		checkListJobs(CLIENT2, USER1, "S", setsharedonly);
		checkListJobs(CLIENT2, USER1, "R", empty);
		checkListJobs(CLIENT2, USER1, "RS", setsharedonly);
		checkListJobs(CLIENT2, USER1, "C", empty);
		checkListJobs(CLIENT2, USER1, "CS", empty);
		checkListJobs(CLIENT2, USER1, "E", empty);
		checkListJobs(CLIENT2, USER1, "ES", empty);
		checkListJobs(CLIENT2, USER1, "RC", empty);
		checkListJobs(CLIENT2, USER1, "RCS", setsharedonly);
		checkListJobs(CLIENT2, USER1, "CE", empty);
		checkListJobs(CLIENT2, USER1, "CES", empty);
		checkListJobs(CLIENT2, USER1, "RE", empty);
		checkListJobs(CLIENT2, USER1, "RES", setsharedonly);
		checkListJobs(CLIENT2, USER1, "RCE", empty);
		checkListJobs(CLIENT2, USER1, "RCES", setsharedonly);
		checkListJobs(CLIENT2, USER1, "RCEX", empty);
		checkListJobs(CLIENT2, USER1, "RCEXS", setsharedonly);
		
		jobid = CLIENT2.createJob2(new CreateJobParams());
		checkListJobs(CLIENT2, USER1, null, empty);
		checkListJobs(CLIENT2, USER1, "", empty);
		checkListJobs(CLIENT2, USER1, "R", empty);
		checkListJobs(CLIENT2, USER1, "C", empty);
		checkListJobs(CLIENT2, USER1, "E", empty);
		checkListJobs(CLIENT2, USER1, "RC", empty);
		checkListJobs(CLIENT2, USER1, "CE", empty);
		checkListJobs(CLIENT2, USER1, "RE", empty);
		checkListJobs(CLIENT2, USER1, "RCE", empty);
		checkListJobs(CLIENT2, USER1, "RXCE", empty);
		
		CLIENT2.startJob(jobid, TOKEN1, "lj stat", "lj desc", noprog, null);
		FakeJob started = new FakeJob(jobid, null, USER1, "started", null, "lj desc",
				"none", null, null, "lj stat", false, false, null, null);
		Set<FakeJob> setstarted = new HashSet<FakeJob>(Arrays.asList(started));
		Set<FakeJob> setstartshare = new HashSet<FakeJob>(setstarted);
		setstartshare.add(shared);
		checkListJobs(CLIENT2, USER1, null, setstarted);
		checkListJobs(CLIENT2, USER1, "", setstarted);
		checkListJobs(CLIENT2, USER1, "S", setstartshare);
		checkListJobs(CLIENT2, USER1, "R", setstarted);
		checkListJobs(CLIENT2, USER1, "RS", setstartshare);
		checkListJobs(CLIENT2, USER1, "C", empty);
		checkListJobs(CLIENT2, USER1, "CS", empty);
		checkListJobs(CLIENT2, USER1, "E", empty);
		checkListJobs(CLIENT2, USER1, "ES", empty);
		checkListJobs(CLIENT2, USER1, "RC", setstarted);
		checkListJobs(CLIENT2, USER1, "RCS", setstartshare);
		checkListJobs(CLIENT2, USER1, "CE", empty);
		checkListJobs(CLIENT2, USER1, "CES", empty);
		checkListJobs(CLIENT2, USER1, "RE", setstarted);
		checkListJobs(CLIENT2, USER1, "RES", setstartshare);
		checkListJobs(CLIENT2, USER1, "RCE", setstarted);
		checkListJobs(CLIENT2, USER1, "RCES", setstartshare);
		checkListJobs(CLIENT2, USER1, "!RCE", setstarted);
		checkListJobs(CLIENT2, USER1, "!RCES", setstartshare);
		
		jobid = CLIENT2.createAndStartJob(TOKEN1, "lj2 stat", "lj2 desc",
				new InitProgress().withPtype("percent"), null);
		CLIENT2.updateJobProgress(jobid, TOKEN1, "lj2 stat2", 42L, null);
		FakeJob started2 = new FakeJob(jobid, null, USER1, "started", null,
				"lj2 desc", "percent", 42, 100, "lj2 stat2", false, false, null,
				null);
		setstarted.add(started2);
		checkListJobs(CLIENT2, USER1, null, setstarted);
		checkListJobs(CLIENT2, USER1, "", setstarted);
		checkListJobs(CLIENT2, USER1, "R", setstarted);
		checkListJobs(CLIENT2, USER1, "C", empty);
		checkListJobs(CLIENT2, USER1, "E", empty);
		checkListJobs(CLIENT2, USER1, "RC", setstarted);
		checkListJobs(CLIENT2, USER1, "CE", empty);
		checkListJobs(CLIENT2, USER1, "RE", setstarted);
		checkListJobs(CLIENT2, USER1, "RCE", setstarted);
		checkListJobs(CLIENT2, USER1, "RCwE", setstarted);
		
		CLIENT2.completeJob(jobid, TOKEN1, "lj2 stat3", null, 
				new Results().withShocknodes(Arrays.asList("node1", "node2")));
		setstarted.remove(started2);
		started2 = null;
		JobResults res = new JobResults(null, null, null, null,
				Arrays.asList("node1", "node2"));
		FakeJob complete = new FakeJob(jobid, null, USER1, "complete", null,
				"lj2 desc", "percent", 100, 100, "lj2 stat3", true, false,
				null, res);
		Set<FakeJob> setcomplete = new HashSet<FakeJob>(Arrays.asList(complete));
		Set<FakeJob> setstartcomp = new HashSet<FakeJob>();
		setstartcomp.addAll(setstarted);
		setstartcomp.addAll(setcomplete);
		Set<FakeJob> setstartcompshare = new HashSet<FakeJob>(setstartcomp);
		setstartcompshare.add(shared);
		checkListJobs(CLIENT2, USER1, null, setstartcomp);
		checkListJobs(CLIENT2, USER1, "", setstartcomp);
		checkListJobs(CLIENT2, USER1, "S", setstartcompshare);
		checkListJobs(CLIENT2, USER1, "R", setstarted);
		checkListJobs(CLIENT2, USER1, "RS", setstartshare);
		checkListJobs(CLIENT2, USER1, "C", setcomplete);
		checkListJobs(CLIENT2, USER1, "CS", setcomplete);
		checkListJobs(CLIENT2, USER1, "C0", setcomplete);
		checkListJobs(CLIENT2, USER1, "C0S", setcomplete);
		checkListJobs(CLIENT2, USER1, "E", empty);
		checkListJobs(CLIENT2, USER1, "ES", empty);
		checkListJobs(CLIENT2, USER1, "RC", setstartcomp);
		checkListJobs(CLIENT2, USER1, "RCS", setstartcompshare);
		checkListJobs(CLIENT2, USER1, "CE", setcomplete);
		checkListJobs(CLIENT2, USER1, "CES", setcomplete);
		checkListJobs(CLIENT2, USER1, "RE", setstarted);
		checkListJobs(CLIENT2, USER1, "RES", setstartshare);
		checkListJobs(CLIENT2, USER1, "RCE", setstartcomp);
		checkListJobs(CLIENT2, USER1, "RCES", setstartcompshare);
		
		jobid = CLIENT2.createAndStartJob(TOKEN1, "lj3 stat", "lj3 desc",
				new InitProgress().withPtype("task").withMax(55L), null);
		CLIENT2.updateJobProgress(jobid, TOKEN1, "lj3 stat2", 40L, null);
		started2 = new FakeJob(jobid, null, USER1, "started", null,
				"lj3 desc", "task", 40, 55, "lj3 stat2", false, false, null,
				null);
		setstarted.add(started2);
		setstartcomp.add(started2);
		checkListJobs(CLIENT2, USER1, null, setstartcomp);
		checkListJobs(CLIENT2, USER1, "", setstartcomp);
		checkListJobs(CLIENT2, USER1, "R", setstarted);
		checkListJobs(CLIENT2, USER1, "C", setcomplete);
		checkListJobs(CLIENT2, USER1, "E", empty);
		checkListJobs(CLIENT2, USER1, "RC", setstartcomp);
		checkListJobs(CLIENT2, USER1, "CE", setcomplete);
		checkListJobs(CLIENT2, USER1, "C#E", setcomplete);
		checkListJobs(CLIENT2, USER1, "RE", setstarted);
		checkListJobs(CLIENT2, USER1, "RCE", setstartcomp);
		
		CLIENT2.completeJob(jobid, TOKEN1, "lj3 stat3", "omg err", 
				new Results().withWorkspaceids(Arrays.asList("wss1", "wss2")));
		setstarted.remove(started2);
		setstartcomp.remove(started2);
		started2 = null;
		JobResults res2 = new JobResults(null, null,
				Arrays.asList("wss1", "wss2"), null, null);
		FakeJob error = new FakeJob(jobid, null, USER1, "error", null,
				"lj3 desc", "task", 55, 55, "lj3 stat3", true, true, null,
				res2);
		Set<FakeJob> seterr = new HashSet<FakeJob>(Arrays.asList(error));
		Set<FakeJob> all = new HashSet<FakeJob>(
				Arrays.asList(started, complete, error));
		checkListJobs(CLIENT2, USER1, null, all);
		checkListJobs(CLIENT2, USER1, "", all);
		checkListJobs(CLIENT2, USER1, "x", all);
		checkListJobs(CLIENT2, USER1, "R", setstarted);
		checkListJobs(CLIENT2, USER1, "C", setcomplete);
		checkListJobs(CLIENT2, USER1, "E", seterr);
		checkListJobs(CLIENT2, USER1, "RC", setstartcomp);
		checkListJobs(CLIENT2, USER1, "CE", new HashSet<FakeJob>(
				Arrays.asList(complete, error)));
		checkListJobs(CLIENT2, USER1, "RE", new HashSet<FakeJob>(
				Arrays.asList(started, error)));
		checkListJobs(CLIENT2, USER1, "RCE", all);
		
		CLIENT2.forceDeleteJob(TOKEN1, started.getID());
		all.remove(started);
		checkListJobs(CLIENT2, USER1, null, all);
		checkListJobs(CLIENT2, USER1, "", all);
		checkListJobs(CLIENT2, USER1, "goodness this is odd input", all);
		checkListJobs(CLIENT2, USER1, "R", empty);
		checkListJobs(CLIENT2, USER1, "C", setcomplete);
		checkListJobs(CLIENT2, USER1, "E", seterr);
		checkListJobs(CLIENT2, USER1, "cE", seterr);
		checkListJobs(CLIENT2, USER1, "RC", setcomplete);
		checkListJobs(CLIENT2, USER1, "CE", new HashSet<FakeJob>(
				Arrays.asList(complete, error)));
		checkListJobs(CLIENT2, USER1, "RE", seterr);
		checkListJobs(CLIENT2, USER1, "RCE", all);
		
		CLIENT2.deleteJob(complete.getID());
		checkListJobs(CLIENT2, USER1, null, seterr);
		checkListJobs(CLIENT2, USER1, "", seterr);
		checkListJobs(CLIENT2, USER1, "R", empty);
		checkListJobs(CLIENT2, USER1, "C", empty);
		checkListJobs(CLIENT2, USER1, "E", seterr);
		checkListJobs(CLIENT2, USER1, "e", seterr);
		checkListJobs(CLIENT2, USER1, "RC", empty);
		checkListJobs(CLIENT2, USER1, "CE", seterr);
		checkListJobs(CLIENT2, USER1, "RE", seterr);
		checkListJobs(CLIENT2, USER1, "RCE", seterr);
		
		CLIENT2.deleteJob(error.getID());
		checkListJobs(CLIENT2, USER1, null, empty);
		checkListJobs(CLIENT2, USER1, "", empty);
		checkListJobs(CLIENT2, USER1, "R", empty);
		checkListJobs(CLIENT2, USER1, "C", empty);
		checkListJobs(CLIENT2, USER1, "E", empty);
		checkListJobs(CLIENT2, USER1, "RC", empty);
		checkListJobs(CLIENT2, USER1, "CE", empty);
		checkListJobs(CLIENT2, USER1, "RE", empty);
		checkListJobs(CLIENT2, USER1, "RCE", empty);
		
		failListJobs(CLIENT2, null,
				"service cannot be null or the empty string");
		failListJobs(CLIENT2, "",
				"service cannot be null or the empty string");
		failListJobs(CLIENT2, "abcdefghijklmnopqrst" + "abcdefghijklmnopqrst"
				+ "abcdefghijklmnopqrst" + "abcdefghijklmnopqrst" +
				"abcdefghijklmnopqrst" + "a",
				"service exceeds the maximum length of 100");
		failListJobs2(CLIENT2, USER1, "kbaseworkspace",
				"The UJS is not configured to delegate authorization to the " +
				"workspace service");
		
		failListJobs2(CLIENT2, USER1, "foo",
				"Invalid authorization strategy: foo");
	}
	
	@Test
	public void sharing() throws Exception {
		InitProgress noprog = new InitProgress().withPtype("none");
		String jobid = CLIENT2.createAndStartJob(TOKEN1, "sh stat", "sh desc", noprog, null);
		CLIENT2.shareJob(jobid, Arrays.asList(USER1));
		//next line ensures that all job read functions are accessible to client 1
		checkJob(CLIENT1, jobid, "started", "sh stat", USER1, "sh desc",
				"none", null, null, null, 0L, 0L, null, null, DEF, DEF, MTMAP);
		failShareJob(CLIENT1, jobid, Arrays.asList(USER2), String.format(
				"There is no job %s with default authorization owned by user %s",
				jobid, USER1));
		assertThat("shared list ok", CLIENT2.getJobShared(jobid), is(Arrays.asList(USER1)));
		failGetJobShared(jobid, String.format("User %s may not access the sharing list of job %s", USER1, jobid));
		assertThat("owner ok", CLIENT1.getJobOwner(jobid), is(USER2));
		assertThat("owner ok", CLIENT2.getJobOwner(jobid), is(USER2));
		CLIENT2.unshareJob(jobid, Arrays.asList(USER1));
		failGetJob(CLIENT1, jobid, String.format("There is no job %s viewable by user %s", jobid, USER1));
		failGetJobOwner(jobid, String.format("There is no job %s viewable by user %s", jobid, USER1));
		failGetJobShared(jobid, String.format("There is no job %s viewable by user %s", jobid, USER1));
		
		CLIENT2.shareJob(jobid, Arrays.asList(USER1));
		failUnshareJob(CLIENT1, jobid, Arrays.asList(USER2), String.format(
				"User %s may only stop sharing job %s for themselves", USER1, jobid));
		CLIENT1.unshareJob(jobid, Arrays.asList(USER1));
		failGetJob(CLIENT1, jobid, String.format("There is no job %s viewable by user %s", jobid, USER1));
		
		String jobid2 = CLIENT1.createAndStartJob(TOKEN1, "sh stat2", "sh desc2", noprog, null);
		failShareUnshareJob(CLIENT1, jobid2, Arrays.asList("thishadbetterbeafakeuserorthistestwillfail"),
				"User thishadbetterbeafakeuserorthistestwillfail is not a valid user");
		failShareUnshareJob(CLIENT1, jobid2, null, "The user list may not be null or empty");
		failShareUnshareJob(CLIENT1, jobid2, new ArrayList<String>(), "The user list may not be null or empty");
		failShareUnshareJob(CLIENT1, jobid2, Arrays.asList(USER2, null),
				"A user name cannot be null or the empty string");
		failShareUnshareJob(CLIENT1, jobid2, Arrays.asList(USER2, ""),
				"A user name cannot be null or the empty string");
	}
	
	private void failGetJobOwner(String id, String exception) throws Exception {
		try {
			CLIENT1.getJobOwner(id);
			fail("got job owner w/ bad args");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
	}
	
	private void failGetJobShared(String id, String exception) throws Exception {
		try {
			CLIENT1.getJobShared(id);
			fail("got job shared list w/ bad args");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
	}
}
