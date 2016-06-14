package us.kbase.userandjobstate.test.userstate;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.schemamanager.SchemaManager;
import us.kbase.common.schemamanager.exceptions.IncompatibleSchemaException;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.userandjobstate.jobstate.UJSJobState;
import us.kbase.userandjobstate.test.UserJobStateTestCommon;
import us.kbase.userandjobstate.userstate.UserState;
import us.kbase.userandjobstate.userstate.UserState.KeyState;
import us.kbase.userandjobstate.userstate.exceptions.NoSuchKeyException;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;

public class UserStateTests {
	
	private static final String DB_NAME = "UserStateTests";

	private static MongoController mongo;
	
	private static DBCollection usercol;
	private static DBCollection schemacol;
	private static UserState us;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		mongo = new MongoController(
				UserJobStateTestCommon.getMongoExe(),
				Paths.get(UserJobStateTestCommon.getTempDir()));
		System.out.println("Using Mongo temp dir " + mongo.getTempDir());
		
		final DB db = GetMongoDB.getDB(
				"localhost:" + mongo.getServerPort(), DB_NAME, 0, 0);
		usercol = db.getCollection("userstate");
		schemacol = db.getCollection("schema");
		us = new UserState(usercol, new SchemaManager(schemacol));
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (mongo != null) {
			mongo.destroy(UserJobStateTestCommon.getDeleteTempFiles());
		}
	}
	
	private static String long101;
	static {
		for (int i = 0; i < 10; i++) {
			long101 += "aaaaaaaaaabbbbbbbbbb";
		}
		long101 += "f";
	}
	
	@Before
	public void clearDB() throws Exception {
		DB db = GetMongoDB.getDB("localhost:" + mongo.getServerPort(),
				DB_NAME);
		UserJobStateTestCommon.destroyDB(db);
	}
	
	@Test
	public void checkSchema() throws Exception {
		/* this just tests that the schema manager is doing something.
		 * The schema manager tests should handle everything else.
		 */
		new SchemaManager(schemacol).setRecord("userstate", 0, false);
		try {
			new UJSJobState(usercol, new SchemaManager(schemacol));
		} catch (IncompatibleSchemaException e) {
			assertThat("incorrect exception message", e.getLocalizedMessage(),
					is("Incompatible database schema for schema type " +
							"userstate. DB is v0, codebase is v1"));
		}
	}
	
	@Test
	public void illegalArgs() throws Exception {
		checkBadArgs(null, "foo", true, "key", "user cannot be null or the empty string");
		checkBadArgs(null, "foo", false, "key", "user cannot be null or the empty string");
		checkBadArgs("", "foo", true, "key", "user cannot be null or the empty string");
		checkBadArgs("", "foo", false, "key", "user cannot be null or the empty string");
		checkBadArgs(long101, "foo", false, "key", "user exceeds the maximum length of 100");
		checkBadArgs("u", null, true, "key", "service cannot be null or the empty string");
		checkBadArgs("u", null, false, "key", "service cannot be null or the empty string");
		checkBadArgs("u", "", true, "key", "service cannot be null or the empty string");
		checkBadArgs("u", "", false, "key", "service cannot be null or the empty string");
		checkBadArgs("u", long101, false, "key", "service exceeds the maximum length of 100");
		checkBadArgs("u", "afa)aafe", true, "key", "Illegal character in service name afa)aafe: )");
		checkBadArgs("u", "afae-afa", false, "key", "Illegal character in service name afae-afa: -");
		checkBadArgs("u", "foo", true, null, "key cannot be null or the empty string");
		checkBadArgs("u", "foo", false, null, "key cannot be null or the empty string");
		checkBadArgs("u", "foo", true, "", "key cannot be null or the empty string");
		checkBadArgs("u", "foo", false, "", "key cannot be null or the empty string");
		checkBadArgs("u", "foo", false, long101, "key exceeds the maximum length of 100");
		checkListStateBadArgs(null, "foo", true, "user cannot be null or the empty string");
		checkListStateBadArgs(null, "foo", false, "user cannot be null or the empty string");
		checkListStateBadArgs("", "foo", true, "user cannot be null or the empty string");
		checkListStateBadArgs("", "foo", false, "user cannot be null or the empty string");
		checkListStateBadArgs("u", null, true, "service cannot be null or the empty string");
		checkListStateBadArgs("u", null, false, "service cannot be null or the empty string");
		checkListStateBadArgs("u", "", true, "service cannot be null or the empty string");
		checkListStateBadArgs("u", "", false, "service cannot be null or the empty string");
		checkListStateBadArgs("u", "afa)aafe", true, "Illegal character in service name afa)aafe: )");
		checkListStateBadArgs("u", "afae-afa", false, "Illegal character in service name afae-afa: -");
		checkListServicesBadArgs(null, true, "user cannot be null or the empty string");
		checkListServicesBadArgs(null, false, "user cannot be null or the empty string");
		checkListServicesBadArgs("", true, "user cannot be null or the empty string");
		checkListServicesBadArgs("", false, "user cannot be null or the empty string");
	}
	
	private void checkListStateBadArgs(String user, String service, boolean auth,
			String exception) throws Exception {
		try {
			us.listState(user, service, auth);
			fail("list state with bad args");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is(exception));
		}
	}
	
	private void checkListServicesBadArgs(String user, boolean auth,
			String exception) throws Exception {
		try {
			us.listServices(user, auth);
			fail("list services with bad args");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is(exception));
		}
	}
	
	private void checkBadArgs(String user, String service, boolean auth,
			String key, String exception) throws Exception {
		Object data = "foo";
		try {
			us.setState(user, service, auth, key, data);
			fail("set state w/ bad args");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is(exception));
		}
		try {
			us.getState(user, service, auth, key);
			fail("get state w/ bad args");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is(exception));
		}
		try {
			us.removeState(user, service, auth, key);
			fail("remove state w/ bad args");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is(exception));
		}
	}
	
	@Test
	public void getNonExistantState() throws Exception {
		us.setState("u", "nostate", false, "thing1", "foo");
		us.setState("u", "nostate", true, "thing1", "foo");
		failGetHasState("u", "nostate", false, "thing");
		failGetHasState("u", "nostate", true, "thing");
	}
	
	private void failGetHasState(String user, String service, boolean auth, String key)
			throws Exception {
		String exp = String.format("There is no key %s for the %sauthorized service %s",
				key, auth ? "" : "un", service);
		try {
			us.getState(user, service, auth, key);
			fail("got non-existant state");
		} catch (NoSuchKeyException nske) {
			assertThat("correct exception", nske.getLocalizedMessage(),
					is(exp));
		}
		try {
			us.getState(user, service, auth, key, true);
			fail("got non-existant state");
		} catch (NoSuchKeyException nske) {
			assertThat("correct exception", nske.getLocalizedMessage(),
					is(exp));
		}
		KeyState ks = us.getState(user, service, auth, key, false);
		assertThat("key exists is false", ks.exists(), is(false));
		assertThat("value is null", ks.getValue(), is((Object) null));
		assertThat("no key exists", us.hasState(user, service, auth, key), is(false));
	}
	
	@Test
	public void removeState() throws Exception {
		us.setState("u", "remstate", false, "thing1", "foo");
		us.setState("u", "remstate", true, "thing1", "foo");
		assertThat("get works prior to remove",
				us.getState("u", "remstate", false, "thing1"),
				is((Object) "foo"));
		assertThat("get works prior to remove",
				us.getState("u", "remstate", true, "thing1"),
				is((Object) "foo"));
		us.removeState("u", "remstate", false, "thing1");
		assertThat("get works after unauthed remove",
				us.getState("u", "remstate", true, "thing1"),
				is((Object) "foo"));
		try {
			us.getState("u", "remstate", false, "thing1");
			fail("got removed state");
		} catch (NoSuchKeyException nske) {
			assertThat("correct exception", nske.getLocalizedMessage(),
					is("There is no key thing1 for the unauthorized service remstate"));
		}
		us.setState("u", "remstate", false, "thing1", "foo");
		us.removeState("u", "remstate", true, "thing1");
		assertThat("get works after authed remove",
				us.getState("u", "remstate", false, "thing1"),
				is((Object) "foo"));
		try {
			us.getState("u", "remstate", true, "thing1");
			fail("got removed state");
		} catch (NoSuchKeyException nske) {
			assertThat("correct exception", nske.getLocalizedMessage(),
					is("There is no key thing1 for the authorized service remstate"));
		}
	}

	@Test
	public void getSetListStateAndService() throws Exception {
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("bar", "baz");
		us.setState("foo", "serv1", false, "key1", data);
		succeedGetHasState("foo", "serv1", false, "key1", data);

		Map<String, Object> data2 = new HashMap<String, Object>();
		data2.put("bar", data);
		us.setState("foo", "authserv1", true, "authkey1", data2);
		succeedGetHasState("foo", "authserv1", true, "authkey1", data2);
		
		us.setState("foo", "serv1", false, "key2", data);
		us.setState("foo", "serv2", false, "key", data);
		us.setState("foo", "authserv1", true, "authkey2", data2);
		us.setState("foo", "authserv2", true, "authkey", data2);

		checkListState("foo", "serv1", false, Arrays.asList("key1", "key2"));
		checkListState("foo", "serv2", false, Arrays.asList("key"));
		checkListState("foo", "serv3", false, new ArrayList<String>());
		checkListState("foo", "authserv1", true, Arrays.asList("authkey1", "authkey2"));
		checkListState("foo", "authserv2", true, Arrays.asList("authkey"));
		checkListState("foo", "authserv3", true, new ArrayList<String>());
		
		checkListServ("foo", false, Arrays.asList("serv1", "serv2"));
		checkListServ("foo1", false, new ArrayList<String>());
		checkListServ("foo", true, Arrays.asList("authserv1", "authserv2"));
		checkListServ("foo1", true, new ArrayList<String>());
		
		us.setState("foo", "nullserv", false, "null", null);
		us.setState("foo", "nullserv", true, "null", null);
		succeedGetHasState("foo", "nullserv", true, "null", null);
		succeedGetHasState("foo", "nullserv", false, "null", null);
	}
	
	private void succeedGetHasState(String user, String service, boolean auth,
			String key, Map<String, Object> data)
			throws Exception {
		Object d = data == null ? null : (Object) new BasicDBObject(data);
		assertThat("got correct data back",
				us.getState(user, service, auth, key), is(d));
		KeyState ks = us.getState(user, service, auth, key, true);
		assertThat("key exists is true", ks.exists(), is(true));
		assertThat("value is correct", ks.getValue(), is(d));
		ks = us.getState(user, service, auth, key, false);
		assertThat("key exists is true", ks.exists(), is(true));
		assertThat("value is correct", ks.getValue(), is(d));
		assertThat("has state is correct", us.hasState(user, service, auth, key),
				is(true));
	}
	
	private void checkListServ(String user, boolean auth,
			List<String> expected) throws Exception {
		Set<String> expc = new HashSet<String>(expected);
		assertThat("get correct services", us.listServices(user, auth),
				is(expc));
	}
	
	private void checkListState(String user, String service, boolean auth,
			List<String> expected) throws Exception {
		Set<String> expc = new HashSet<String>(expected);
		assertThat("get correct keys", us.listState(user, service, auth),
				is(expc));
	}
	
	@Test
	public void setUnserializable() throws Exception {
		try {
			us.setState("foo", "unserializable", false, "a",
					new StringReader("foo"));
			fail("saved unserializable object");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Unable to serialize value"));
		}
	}
	
	private static final String BIG_STR = 
			"afbiekdiekaotkjaialeotialeicitlaielfialefotaleofleif" +
			"afbiekdiekaotkjaialeotialeicitlaielfialefotaleofleif";
	@Test
	public void failOnBigState() throws Exception {
		Map<Integer, String> data = new HashMap<Integer, String>();
		for (int i = 0; i < 10000; i++) {
			data.put(i, BIG_STR);
		}
		try {
			us.setState("foo", "servBig", false, "key1", data);
			fail("set state with way too big data");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("Value cannot be > 1000000 bytes when serialized"));
		}
	}
}
