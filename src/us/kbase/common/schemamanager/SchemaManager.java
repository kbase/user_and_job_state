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
 * match the code version. Allows storing and checking schemas for multiple
 * schema types (which in most cases will be different collections in the
 * same database or possibly multiple databases).
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
	
	/** Create a schema manager using the specified collection as its
	 * database.
	 * @param schemaCol the collection to use as the schema database.
	 * @throws InvalidSchemaRecordException if the schema database already has
	 * more than one schema record per schema type.
	 */
	public SchemaManager(final DBCollection schemaCol)
			throws InvalidSchemaRecordException {
		if (schemaCol == null) {
			throw new NullPointerException("schemaCol");
		}
		this.schemaCol = schemaCol;
		ensureIndexes(schemaCol);
	}
	
	/** Check that the database schema for a schema type matches the code
	 * version.
	 * @param schemaType the type of schema to check.
	 * @param currentVer the codebase version to compare against the db
	 * version.
	 * @throws IncompatibleSchemaException if the db and codebase versions are
	 * incompatible.
	 * @throws UpdateInProgressException if the db is marked as having an
	 * update in progress.
	 * @throws SchemaManagerCommunicationException if a communication error
	 * occurs.
	 */
	public void checkSchema(
			final String schemaType,
			final int currentVer)
			throws IncompatibleSchemaException, UpdateInProgressException,
			SchemaManagerCommunicationException {
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
			final VerUpdate vu = getVersionAndUpdateState(schemaType, true);
			if (vu.ver != currentVer) {
				throw new IncompatibleSchemaException(String.format(
						"Incompatible database schema for schema type %s. " +
						"DB is v%s, codebase is v%s",
						schemaType, vu.ver, currentVer));
			}
			if (vu.inupdate) {
				throw new UpdateInProgressException(String.format(
						"Update from version %s in progress for the %s database.",
						vu.ver, schemaType));
			}
		} catch (MongoException me) {
			throw new SchemaManagerCommunicationException(
					"There was a problem communicating with the database", me);
		}
	}

	private VerUpdate getVersionAndUpdateState(
			final String schemaType,
			final boolean exceptIfNoRecord) {
		final DBCursor cur = schemaCol.find(new BasicDBObject(
				SCHEMA_KEY, schemaType));
		if (cur.size() > 1) { //this should be impossible
			throw new IllegalStateException(
					"Multiple schema documents found in the database " +
					"for schema type " + schemaType + 
					". This should not happen, something is very wrong.");
		}
		if (cur.size() == 0) {
			if (exceptIfNoRecord) { // this should be impossible
				throw new IllegalStateException(
						"Found no record for schema type " + schemaType);
			}
			return new VerUpdate(-1, false);
		}
		final DBObject storedCfg = cur.next();
		return new VerUpdate(
				(Integer) storedCfg.get(SCHEMA_VER),
				(Boolean)storedCfg.get(UPDATE));
	}
	
	private static class VerUpdate {
		public final int ver;
		public final boolean inupdate;
		
		private VerUpdate(int ver, boolean inupdate) {
			this.ver = ver;
			this.inupdate = inupdate;
		}
	}

	private void ensureIndexes(DBCollection schemaCol)
			throws InvalidSchemaRecordException {
		final DBObject keys = new BasicDBObject(SCHEMA_KEY, 1);
		final DBObject uniq = new BasicDBObject("unique", 1);
		try {
			schemaCol.createIndex(keys, uniq);
		} catch (DuplicateKeyException dke) {
			throw new InvalidSchemaRecordException(
					"Multiple schema records exist in the database: " +
					dke.getLocalizedMessage(), dke);
		}
	}
	
	/** Only use this method on an offline database from a single application.
	 * @param schemaType the type of schema to access.
	 * @return the database version as stored in the database.
	 * @throws InvalidSchemaRecordException if the schema records are invalid.
	 */
	public synchronized int getDBVersion(final String schemaType)
			throws InvalidSchemaRecordException {
		return getVersionAndUpdateState(schemaType, false).ver;
	}
	
	/** Only use this method on an offline database from a single application.
	 * @param schemaType the type of schema to access.
	 * @return true if the database is in the process of being updated, false
	 * otherwise.
	 * @throws InvalidSchemaRecordException if the schema records are invalid.
	 */
	public synchronized boolean inUpdate(final String schemaType)
			throws InvalidSchemaRecordException {
		return getVersionAndUpdateState(schemaType, false).inupdate;
	}
	
	/** Only use this method on an offline database from a single application.
	 * @param schemaType the type of schema to access.
	 * @param the version of the database. Use 0 to represent an unversioned
	 * database.
	 * @param inupdate true to set the database to an update in progress state,
	 * false to remove the upgrade in progress state.
	 * @throws SchemaManagerCommunicationException 
	 * @throws InvalidSchemaRecordException if the schema records are invalid.
	 */
	public synchronized void setRecord(
			final String schemaType,
			final int version,
			final boolean inupdate)
			throws SchemaManagerCommunicationException {
		if (schemaType == null || schemaType.isEmpty()) {
			throw new IllegalArgumentException(
					"schemaType can't be null or empty");
		}
		if (version < 0) {
			throw new IllegalArgumentException("currentVer must be >= 0");
		}
		final DBObject cfg = new BasicDBObject(UPDATE, inupdate);
		cfg.put(SCHEMA_VER, version);
		try {
			schemaCol.update(new BasicDBObject(SCHEMA_KEY, schemaType),
					new BasicDBObject("$set", cfg), true, false);
		} catch (MongoException me) {
			throw new SchemaManagerCommunicationException(
					"There was a problem communicating with the database", me);
		}
	}
}
