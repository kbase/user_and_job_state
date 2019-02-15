package us.kbase.common.test.schemamanager;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import static us.kbase.common.test.TestCommon.assertExceptionCorrect;

import java.nio.file.Paths;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.common.schemamanager.SchemaManager;
import us.kbase.common.schemamanager.exceptions.IncompatibleSchemaException;
import us.kbase.common.schemamanager.exceptions.InvalidSchemaRecordException;
import us.kbase.common.schemamanager.exceptions.UpdateInProgressException;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

public class SchemaManagerTest {

	//TODO ZZLATER TEST add shutdown /startup capability to mongo controller and test against shutdown mongo
	
	private static final String DB_NAME = "SchemaManagerTests";
	
	private static DB db;

	private static MongoController mongo;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		TestCommon.stfuLoggers();
		mongo = new MongoController(
				TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()));
		System.out.println("Using Mongo temp dir " + mongo.getTempDir());
		db = new MongoClient("localhost:" + mongo.getServerPort()).getDB(DB_NAME);
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (mongo != null) {
			mongo.destroy(TestCommon.getDeleteTempFiles());
		}
	}
	
	@Before
	public void clearDB() throws Exception {
		TestCommon.destroyDB(db);
	}
	
	@Test
	public void testNullCollection() throws Exception {
		try {
			new SchemaManager(null);
			fail("expected npe");
		} catch (NullPointerException npe) {
			assertThat("incorrect exception message", npe.getLocalizedMessage(),
					is("schemaCol"));
		}
	}
	@Test
	public void testEmptyDB() throws Exception {
		final String sc = "myschema";
		SchemaManager sm = new SchemaManager(db.getCollection("foo"));
		assertThat("empty db has version", sm.getDBVersion(sc), is(-1));
		assertThat("empty db in update", sm.inUpdate(sc), is(false));
		
	}
	
	@Test
	public void testGetSet() throws Exception {
		final String sc = "myschema";
		SchemaManager sm = new SchemaManager(db.getCollection("foo"));
		sm.setRecord(sc, 1, false);
		assertThat("incorrect version", sm.getDBVersion(sc), is(1));
		assertThat("incorrect in update", sm.inUpdate(sc), is(false));
		
		sm.setRecord(sc, 2, true);
		assertThat("incorrect version", sm.getDBVersion(sc), is(2));
		assertThat("incorrect in update", sm.inUpdate(sc), is(true));
		
		sm.setRecord(sc, -1, false);
		assertThat("incorrect version", sm.getDBVersion(sc), is(2));
		assertThat("incorrect in update", sm.inUpdate(sc), is(false));
		
		failSetRecord(sm, sc, 0, true,
				new IllegalArgumentException("currentVer must be -1 or > 0"));
		failSetRecord(sm, sc, -2, true,
				new IllegalArgumentException("currentVer must be -1 or > 0"));
		failSetRecord(sm, null, 1, true,
				new IllegalArgumentException("schemaType can't be null or empty"));
		failSetRecord(sm, "", 1, true,
				new IllegalArgumentException("schemaType can't be null or empty"));
	}
	
	@Test
	public void testTwoSchemaRecords() throws Exception {
		final String dbn = "noindexes";
		DBCollection dbc = db.getCollection(dbn);
		DBObject dbo1 = new BasicDBObject("config", "myschema");
		dbo1.put("inupdate", false);
		dbo1.put("schemaver", 1);
		dbc.insert(dbo1);
		DBObject dbo2 = new BasicDBObject("config", "myschema");
		dbo2.put("inupdate", true);
		dbo2.put("schemaver", 3);
		dbc.insert(dbo2);
		try {
			new SchemaManager(dbc);
			fail("created manager with bad schema docs");
		} catch (InvalidSchemaRecordException e) {
			final String msg = e.getMessage();
			assertThat("incorrect exception message", msg, containsString(
					"Multiple schema records exist in the database: "));
			assertThat("incorrect exception message", msg, containsString(
					"E11000 duplicate key error index: SchemaManagerTests.noindexes.$config_1"));
		}
	}
	
	@Test
	public void testCheckSchema() throws Exception {
		final String sc = "myschema";
		SchemaManager sm = new SchemaManager(db.getCollection("foo"));
		assertThat("empty db has version", sm.getDBVersion(sc), is(-1));
		assertThat("empty db in update", sm.inUpdate(sc), is(false));
		
		sm.checkSchema(sc, 1);
		assertThat("incorrect version", sm.getDBVersion(sc), is(1));
		assertThat("incorrect in update", sm.inUpdate(sc), is(false));
		
		failCheckSchema(sm, sc, 2,
				new IncompatibleSchemaException(
						"Incompatible database schema for schema type " +
						"myschema. DB is v1, codebase is v2"));
		
		sm.setRecord(sc, 1, true);
		
		failCheckSchema(sm, sc, 1,
				new UpdateInProgressException(
						"Update from version 1 in progress for the myschema database."));
		
		// check that the upgrade exception takes precedence over the bad
		// version exception
		failCheckSchema(sm, sc, 2,
				new UpdateInProgressException(
						"Update from version 1 in progress for the myschema database."));
		
		sm.setRecord(sc, 1, false);
		
		sm.checkSchema(sc, 1); //should work
		
		failCheckSchema(sm, sc, 0,
				new IllegalArgumentException("currentVer must be > 0"));
		failCheckSchema(sm, null, 1,
				new IllegalArgumentException("schemaType can't be null or empty"));
		failCheckSchema(sm, "", 1,
				new IllegalArgumentException("schemaType can't be null or empty"));
	}

	private void failSetRecord(SchemaManager sm, String sc, int ver,
			boolean inupdate, Exception exp) {
		try {
			sm.setRecord(sc, ver, inupdate);
			fail("set bad record");
		} catch (Exception e) {
			assertExceptionCorrect(e, exp);
		}
	}
	
	private void failCheckSchema(SchemaManager sm, String sc, int currentver,
			Exception exp) {
		try {
			sm.checkSchema(sc, currentver);
			fail("passed bad schema");
		} catch (Exception e) {
			assertExceptionCorrect(e, exp);
		}
	}
}
