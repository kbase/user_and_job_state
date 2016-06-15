package us.kbase.userandjobstate.updater;

import static us.kbase.userandjobstate.UserAndJobStateServer.HOST;
import static us.kbase.userandjobstate.UserAndJobStateServer.DB;
import static us.kbase.userandjobstate.UserAndJobStateServer.USER;
import static us.kbase.userandjobstate.UserAndJobStateServer.PWD;
import static us.kbase.userandjobstate.UserAndJobStateServer.USER_COLLECTION;
import static us.kbase.userandjobstate.UserAndJobStateServer.JOB_COLLECTION;
import static us.kbase.userandjobstate.UserAndJobStateServer.SCHEMA_VERS_COLLECTION;



import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.Map;

import org.ini4j.Ini;

import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.mongo.exceptions.MongoAuthException;
import us.kbase.common.schemamanager.SchemaManager;
import us.kbase.common.schemamanager.exceptions.InvalidSchemaRecordException;
import us.kbase.common.schemamanager.exceptions.SchemaManagerCommunicationException;
import us.kbase.userandjobstate.jobstate.UJSJobState;
import us.kbase.userandjobstate.userstate.UserState;


import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;

public class UjsUpdater {

	//TODO LATER tests - when this gets a bit more complicated.
	//TODO LATER modular update mechanism
	//TODO LATER add a logger instead of prints?
	
	private static final String UJS = "UserAndJobState";
	
	private final UpdateArgs ua;
	
	public static void main(String[] args) {
		new UjsUpdater(args);
	}
	
	public UjsUpdater(final String[] args) {
		ua = new UpdateArgs();
		final JCommander jc = new JCommander(ua);
		jc.setProgramName("db_update");
		try {
			jc.parse(args);
		} catch (ParameterException e) {
			System.out.println("Error: " + e.getLocalizedMessage());
			jc.usage();
			System.exit(1);
		}
		
		final Map<String, String> config;
		try {
			config = getConfig();
		} catch(Exception e) {
			throw showError(e);
		}
		
		final DB db;
		try {
			db = getDatabase(config);
		} catch (Exception e) {
			throw showError(e);
		}
		
		final SchemaManager sm;
		try {
			sm = new SchemaManager(db.getCollection(SCHEMA_VERS_COLLECTION));
		} catch (InvalidSchemaRecordException e) {
			throw showError(e);
		}
		System.out.println("Updating user state database");
		try {
			updateUserStateDB(db.getCollection(USER_COLLECTION), sm);
		} catch (SchemaManagerCommunicationException e) {
			throw showError(e);
			
		}
		updateJobStateDB(db.getCollection(JOB_COLLECTION), sm);
	}
	
	private void updateJobStateDB(final DBCollection jobs,
			final SchemaManager sm) {
		final int ver = sm.getDBVersion(UJSJobState.SCHEMA_TYPE);
		// TODO Auto-generated method stub
		
	}

	private void updateUserStateDB(final DBCollection user,
			final SchemaManager sm)
			throws SchemaManagerCommunicationException {
		final int ver = sm.getDBVersion(UserState.SCHEMA_TYPE);
		
		if (ver == -1) {
			sm.setRecord(UserState.SCHEMA_TYPE, UserState.SCHEMA_VER, false);
		} else if (ver != UserState.SCHEMA_VER) {
			throw new IllegalStateException(String.format(
					"There is no upgrade path from DB version %s to %s",
					ver, UserState.SCHEMA_VER));
		}
	}

	private DB getDatabase(Map<String, String> config) {
		if (!config.containsKey(HOST)) {
			throw new IllegalStateException(
					"Must provide param " + HOST + " in config file");
		}
		final String host = config.get(HOST);
		if (!config.containsKey(DB)) {
			throw new IllegalStateException(
					"Must provide param " + DB + " in config file");
		}
		final String dbs = config.get(DB);
		if (config.containsKey(USER) ^ config.containsKey(PWD)) {
			throw new IllegalStateException(String.format(
					"Must provide both %s and %s ",
					USER, PWD) + "params in config file if authentication " + 
					"is to be used");
		}
		final String user = config.get(USER);
		final String pwd = config.get(PWD);
		return getMongoDB(host, dbs, user, pwd);
	}

	//TODO LATER generalize this with the method in the server class
	private DB getMongoDB(
			final String host,
			final String dbs,
			final String user,
			final String pwd) {
		try {
			if (user != null) {
				return GetMongoDB.getDB(host, dbs, user, pwd, 0, 0);
			} else {
				return GetMongoDB.getDB(host, dbs, 0, 0);
			}
		} catch (UnknownHostException uhe) {
			throw new IllegalStateException(
					"Couldn't find mongo host " + host + ": " +
					uhe.getLocalizedMessage(), uhe);
		} catch (IOException | MongoTimeoutException e) {
			throw new IllegalStateException(
					"Couldn't connect to mongo host " + host + ": " +
					e.getLocalizedMessage(), e);
		} catch (MongoAuthException ae) {
			throw new IllegalStateException(
					"Not authorized: " + ae.getLocalizedMessage(), ae);
		} catch (MongoException e) {
			throw new IllegalStateException(
					"There was an error connecting to the mongo database: " +
					e.getLocalizedMessage(), e);
		} catch (InvalidHostException ihe) {
			throw new IllegalStateException(
					host + " is an invalid database host: "  +
					ihe.getLocalizedMessage(), ihe);
		} catch (InterruptedException ie) {
			throw new IllegalStateException(
					"Connection to MongoDB was interrupted. This should never " +
					"happen and indicates a programming problem. Error: " +
					ie.getLocalizedMessage(), ie);
		}
	}
	
	
	public Map<String, String> getConfig() throws IOException {
		final Ini ini;
		try {
			ini = new Ini(Paths.get(ua.deploy).toFile());
		} catch (IOException ioe) {
			throw new IOException(
					"There was an error opening the configuration file: " +
							ioe.getLocalizedMessage(), ioe);
		}
		Map<String, String> config = ini.get(UJS);
		if (config == null) {
			throw new IllegalStateException(String.format(
					"Config file %s missing section %s", ua.deploy, UJS));
					
		}
		return config;
	}

	private AssertionError showError(final Exception e) {
		if (ua.verbose) {
			e.printStackTrace();
		} else {
			System.out.println(e.getLocalizedMessage());
		}
		System.exit(1);
		return new AssertionError("Well, this shouldn't have happened.");
	}

	@Parameters(commandDescription =
			"Update the UJS database to a version matching this codebase.")
	private static class UpdateArgs {
		@Parameter(names={"-d","--deploy"}, required = true,
				description="Path to the deploy.cfg file")
		String deploy;
		
		@Parameter(names={"-c","--commit"}, description="Make changes to " +
				"the database. If this parameter is not set, a dry run will " +
				"be performed.")
		boolean commit = false;
		
		@Parameter(names={"-v","--verbose"},
				description="Print error stacktraces.")
		boolean verbose = false;
	}
}
