package us.kbase.common.schemamanager;

import us.kbase.common.schemamanager.exceptions.InvalidSchemaRecordException;
import us.kbase.common.schemamanager.exceptions.IncompatibleSchemaException;
import us.kbase.common.schemamanager.exceptions.SchemaManagerCommunicationException;
import us.kbase.common.schemamanager.exceptions.UpdateInProgressException;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.DuplicateKeyException;
import com.mongodb.MongoException;

/** Checks schema versions and throws errors if the schema version doesn't
 * match the code version. 
 * 
 * This class deliberately does not handle automatic upgrades, as it is too
 * difficult to automatically upgrade a database if multiple servers are
 * running. For example, if a v2 server is running and a v3 server is started,
 * the v3 server will upgrade the database to v3, which could cause v2 to
 * error, or worse, corrupt the database. Even locking the database while the
 * upgrade is in progress can't fix this - the v2 server would have to 
 * detect the upgrade and stop.
 * @author gaprice@lbl.gov
 *
 */
public class SchemaManager {
	
	//these were deliberately set to be compatible with the workspace 
	//schema collection, will port over there eventually
	private final static String SCHEMA_KEY = "config";
	private final static String UPDATE = "inupdate";
	private final static String SCHEMA_VER = "schemaver";
	
	private final DBCollection schemaCol;
	
	public SchemaManager(final DBCollection schemaCol) {
		if (schemaCol == null) {
			throw new NullPointerException("schemaCol");
		}
		this.schemaCol = schemaCol;
		ensureIndexes(schemaCol);
	}
	
	//TODO NOW tests
	//TODO NOW check changes so far and list tests necessary
	public void checkSchema(
			final String schemaType,
			final int currentVer)
			throws InvalidSchemaRecordException, IncompatibleSchemaException,
			UpdateInProgressException, SchemaManagerCommunicationException {
		if (schemaType == null || schemaType.isEmpty()) {
			throw new IllegalArgumentException(
					"schemaType can't be null or empty");
		}
		if (currentVer < 1) {
			throw new IllegalArgumentException("currentVer must be > 0");
		}
		final DBObject cfg = new BasicDBObject(SCHEMA_KEY, schemaType);
		cfg.put(UPDATE, false);
		cfg.put(SCHEMA_VER, currentVer);
		try {
			schemaCol.insert(cfg);
		} catch (DuplicateKeyException dk) {
			//ok, the version doc is already there, this isn't the first
			//startup
			final DBCursor cur = schemaCol.find(new BasicDBObject(
					SCHEMA_KEY, schemaType));
			if (cur.size() != 1) {
				throw new InvalidSchemaRecordException(
						"Multiple schema documents found in the database " +
						"for schema type " + schemaType + 
						". This should not happen, something is very wrong.");
			}
			final DBObject storedCfg = cur.next();
			final int dbver = (Integer) storedCfg.get(SCHEMA_VER);
			if (dbver != currentVer) {
				throw new IncompatibleSchemaException(String.format(
						"Incompatible database schema. Server is v%s, DB is v%s",
						dbver, currentVer));
			}
			if ((Boolean)storedCfg.get(UPDATE)) {
				throw new UpdateInProgressException(String.format(
						"The database is in the middle of an update from " +
						"version %s of the schema.", dbver));
			}
		} catch (MongoException me) {
			throw new SchemaManagerCommunicationException(
					"There was a problem communicating with the database", me);
		}
	}

	private void ensureIndexes(DBCollection schemaCol) {
		final DBObject keys = new BasicDBObject(SCHEMA_KEY, 1);
		final DBObject uniq = new BasicDBObject("unique", 1);
		schemaCol.createIndex(keys, uniq);
	}
}
