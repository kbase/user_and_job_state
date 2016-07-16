package us.kbase.userandjobstate;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonServerMethod;
import us.kbase.common.service.JsonServerServlet;
import us.kbase.common.service.JsonServerSyslog;
import us.kbase.common.service.RpcContext;
import us.kbase.common.service.Tuple12;
import us.kbase.common.service.Tuple14;
import us.kbase.common.service.Tuple2;
import us.kbase.common.service.Tuple3;
import us.kbase.common.service.Tuple5;
import us.kbase.common.service.Tuple7;
import us.kbase.common.service.UObject;

//BEGIN_HEADER
import static us.kbase.common.utils.ServiceUtils.checkAddlArgs;
import static us.kbase.common.utils.StringUtils.checkMaxLen;
import static us.kbase.common.utils.StringUtils.checkString;
import static us.kbase.userandjobstate.jobstate.JobResults.MAX_LEN_ID;
import static us.kbase.userandjobstate.jobstate.JobResults.MAX_LEN_URL;

import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.slf4j.LoggerFactory;

import com.mongodb.DB;
import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import us.kbase.auth.AuthConfig;
import us.kbase.auth.AuthException;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.auth.TokenExpiredException;
import us.kbase.auth.TokenFormatException;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.mongo.exceptions.MongoAuthException;
import us.kbase.common.schemamanager.SchemaManager;
import us.kbase.common.schemamanager.exceptions.InvalidSchemaRecordException;
import us.kbase.common.schemamanager.exceptions.SchemaException;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.userandjobstate.authorization.AuthorizationStrategy;
import us.kbase.userandjobstate.authorization.DefaultUJSAuthorizer;
import us.kbase.userandjobstate.authorization.UJSAuthorizer;
import us.kbase.userandjobstate.authorization.exceptions.UJSAuthorizationException;
import us.kbase.userandjobstate.jobstate.Job;
import us.kbase.userandjobstate.jobstate.JobResult;
import us.kbase.userandjobstate.jobstate.JobResults;
import us.kbase.userandjobstate.jobstate.JobState;
import us.kbase.userandjobstate.kbase.WorkspaceAuthorizationFactory;
import us.kbase.userandjobstate.userstate.UserState;
import us.kbase.userandjobstate.userstate.UserState.KeyState;
import us.kbase.workspace.database.WorkspaceUserMetadata;
//END_HEADER

/**
 * <p>Original spec-file module name: UserAndJobState</p>
 * <pre>
 * User and Job State service (UJS)
 * Service for storing arbitrary key/object pairs on a per user per service basis
 * and storing job status so that a) long JSON RPC calls can report status and
 * UI elements can receive updates, and b) there's a centralized location for 
 * job status reporting.
 * There are two modes of operation for setting key values for a user: 
 * 1) no service authentication - an authorization token for a service is not 
 *         required, and any service with the user token can write to any other
 *         service's unauthed values for that user.
 * 2) service authentication required - the service must pass a Globus Online
 *         token that identifies the service in the argument list. Values can only be
 *         set by services with possession of a valid token. The service name 
 *         will be set to the username of the token.
 * The sets of key/value pairs for the two types of method calls are entirely
 * separate - for example, the workspace service could have a key called 'default'
 * that is writable by all other services (no auth) and the same key that was 
 * set with auth to which only the workspace service can write (or any other
 * service that has access to a workspace service account token, so keep your
 * service credentials safe).
 * Setting objects are limited to 640Kb.
 * All job writes require service authentication. No reads, either for key/value
 * pairs or jobs, require service authentication.
 * The service assumes other services are capable of simple math and does not
 * throw errors if a progress bar overflows.
 * Potential job process flows:
 * Asysnc:
 * UI calls service function which returns with job id
 * service call [spawns thread/subprocess to run job that] periodically updates
 *         the job status of the job id on the job status server
 * meanwhile, the UI periodically polls the job status server to get progress
 *         updates
 * service call finishes, completes job
 * UI pulls pointers to results from the job status server
 * Sync:
 * UI creates job, gets job id
 * UI starts thread that calls service, providing job id
 * service call runs, periodically updating the job status of the job id on the
 *         job status server
 * meanwhile, the UI periodically polls the job status server to get progress
 *         updates
 * service call finishes, completes job, returns results
 * UI thread joins
 * Authorization:
 * Currently two modes of authorization are supported:
 * DEFAULT:
 * DEFAULT authorization uses the UJS access control lists (ACLs) stored in the
 * UJS database. All methods work normally for this authorization strategy. To
 * use the default authorization strategy, simply do not specify an authorization
 * strategy when creating a job.
 * kbaseworkspace:
 * kbaseworkspace authorization (kbwsa) associates each job with an integer
 * Workspace Service (WSS) workspace ID (the authorization parameter). In order to
 * create a job with kbwsa, a user must have write access to the workspace in
 * question. That user can then read and update (but not necessarily list) the job
 * for the remainder of the job lifetime, regardless of the workspace permission.
 * Other users must have read permissions to the workspace in order to view the
 * job.
 * Share and unshare commands do not work with kbwsa.
 * </pre>
 */
public class UserAndJobStateServer extends JsonServerServlet {
    private static final long serialVersionUID = 1L;
    private static final String version = "0.0.1";
    private static final String gitUrl = "https://github.com/mrcreosote/user_and_job_state";
    private static final String gitCommitHash = "fd3aa5f9dd3f4a3644a6eabc9280f1be781f28dd";

    //BEGIN_CLASS_HEADER
	
	private static final String GIT =
			"https://github.com/kbase/user_and_job_state";
	
	private static final String VER = "0.2.0-dev";

	//required deploy parameters:
	public static final String HOST = "mongodb-host";
	public static final String DB = "mongodb-database";
	//auth params:
	public static final String USER = "mongodb-user";
	public static final String PWD = "mongodb-pwd";
	//mongo connection attempt limit
	private static final String MONGO_RECONNECT = "mongodb-retry";
	//credentials to use for user queries
	private static final String KBASE_ADMIN_USER = "kbase-admin-user";
	private static final String KBASE_ADMIN_PWD = "kbase-admin-pwd";
	
	private static final String WORKSPACE_URL = "workspace-url";
			
	private static Map<String, String> ujConfig = null;
	
	public static final String USER_COLLECTION = "userstate";
	public static final String JOB_COLLECTION = "jobstate";
	public static final String SCHEMA_VERS_COLLECTION = "schemavers";
	
	private static final int MONGO_RETRY_LOG_INTERVAL = 10;
	
	private final static int MAX_LEN_SERVTYPE = 100;
	private final static int MAX_LEN_DESC = 1000;
	
	private final static int TOKEN_REFRESH_INTERVAL_SEC = 24 * 60 * 60;
	
	private final UserState us;
	private final JobState js;
	private final ConfigurableAuthService auth;
	private final WorkspaceAuthorizationFactory authfac;
	
	private final UJSAuthorizer nows = new UJSAuthorizer() {
		
		@Override
		protected void externallyAuthorizeRead(
				final AuthorizationStrategy strat,
				final String user,
				final List<String> authParams)
				throws UJSAuthorizationException {
			checkStrat(strat);
		}

		private void checkStrat(final AuthorizationStrategy strat)
				throws UJSAuthorizationException {
			if (strat.equals(WorkspaceAuthorizationFactory.WS_AUTH)) {
				throw new UJSAuthorizationException(
						"The UJS is not configured to delegate " +
						"authorization to the workspace service");
			} else {
				throw new UJSAuthorizationException(
						"Invalid authorization strategy: " + strat.getStrat());
			}
		}
		
		@Override
		protected void externallyAuthorizeRead(final String user, final Job j)
				throws UJSAuthorizationException {
			checkStrat(j.getAuthorizationStrategy());
		}
		
		@Override
		protected void externallyAuthorizeCreate(
				final AuthorizationStrategy strat,
				final String authParam)
				throws UJSAuthorizationException {
			checkStrat(strat);
		}
	};
	
	private final static DateTimeFormatter DATE_PARSER =
			new DateTimeFormatterBuilder()
				.append(DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss"))
				.appendOptional(DateTimeFormat.forPattern(".SSS").getParser())
				.append(DateTimeFormat.forPattern("Z"))
				.toFormatter();
	
	private final static DateTimeFormatter DATE_FORMATTER =
			DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ").withZoneUTC();
	
	private DB getMongoDB(
			final String host,
			final String dbs,
			final String user,
			final String pwd,
			final int mongoReconnectRetry) {
		try {
			if (user != null) {
				return GetMongoDB.getDB(host, dbs, user, pwd,
						mongoReconnectRetry, MONGO_RETRY_LOG_INTERVAL);
			} else {
				return GetMongoDB.getDB(host, dbs, mongoReconnectRetry,
						MONGO_RETRY_LOG_INTERVAL);
			}
		} catch (UnknownHostException uhe) {
			fail("Couldn't find mongo host " + host + ": " +
					uhe.getLocalizedMessage());
		} catch (IOException | MongoTimeoutException e) {
			fail("Couldn't connect to mongo host " + host + ": " +
					e.getLocalizedMessage());
		} catch (MongoAuthException ae) {
			fail("Not authorized: " + ae.getLocalizedMessage());
		} catch (MongoException e) {
			fail("There was an error connecting to the mongo database: " +
					e.getLocalizedMessage());
		} catch (InvalidHostException ihe) {
			fail(host + " is an invalid database host: "  +
					ihe.getLocalizedMessage());
		} catch (InterruptedException ie) {
			fail("Connection to MongoDB was interrupted. This should never " +
					"happen and indicates a programming problem. Error: " +
					ie.getLocalizedMessage());
		}
		return null;
	}
	
	private SchemaManager getSchemaManager(final DB db, final String host) {
		if (db == null) {
			return null;
		}
		try {
			 return new SchemaManager(
						db.getCollection(SCHEMA_VERS_COLLECTION));
		} catch (MongoTimeoutException e) {
			fail("Couldn't connect to mongo host " + host + ": " +
					e.getLocalizedMessage());
		} catch (InvalidSchemaRecordException e) {
			fail("The schema version records are corrupt: " +
					e.getLocalizedMessage());
		}
		return null;
	}
	
	private UserState getUserState(final DB db, final SchemaManager sm,
			final String host) {
		try {
			return new UserState(db.getCollection(USER_COLLECTION), sm);
		} catch (MongoTimeoutException e) {
			fail("Couldn't connect to mongo host " + host + ": " +
					e.getLocalizedMessage());
		} catch (SchemaException e) {
			fail("An error occured while checking the database schema: " +
					e.getLocalizedMessage());
		}
		return null;
	}
	
	private JobState getJobState(final DB db, final SchemaManager sm,
			final String host) {
		try {
			return new JobState(db.getCollection(JOB_COLLECTION), sm);
		} catch (MongoTimeoutException e) {
			fail("Couldn't connect to mongo host " + host + ": " +
					e.getLocalizedMessage());
		} catch (SchemaException e) {
			fail("An error occured while checking the database schema: " +
					e.getLocalizedMessage());
		}
		return null;
	}
	//TODO ZLATER write manual
	//TODO ZZLATER admin methods
	
	private void fail(final String error) {
		logErr(error);
		System.err.println(error);
		startupFailed();
	}
	
	private String getServiceUserName(String serviceToken)
			throws TokenFormatException, TokenExpiredException, IOException {
		if (serviceToken == null || serviceToken.isEmpty()) {
			throw new IllegalArgumentException(
					"Service token cannot be null or the empty string");
		}
		final AuthToken t = new AuthToken(serviceToken);
		if (!auth.validateToken(t)) {
			throw new IllegalArgumentException("Service token is invalid");
		}
		return t.getUserName();
	}
	
	private Tuple14<String, String, String, String, String, String, Long,
			Long, String, String, Long, Long, String, Results>
			jobToJobInfo(final Job j) {
		return new Tuple14<String, String, String, String, String, String,
				Long, Long, String, String, Long, Long, String,
				Results>()
			.withE1(j.getID())
			.withE2(j.getService())
			.withE3(j.getStage())
			.withE4(formatDate(j.getStarted()))
			.withE5(j.getStatus())
			.withE6(formatDate(j.getLastUpdated()))
			.withE7(j.getProgress() == null ? null :
				new Long(j.getProgress()))
			.withE8(j.getMaxProgress() == null ? null :
				new Long(j.getMaxProgress()))
			.withE9(j.getProgType())
			.withE10(formatDate(j.getEstimatedCompletion()))
			.withE11(boolToLong(j.isComplete()))
			.withE12(boolToLong(j.hasError()))
			.withE13(j.getDescription())
			.withE14(makeResults(j.getResults()));
	}

	private Tuple12<String, String, String, String,
			Tuple3<String, String, String>, Tuple3<Long, Long, String>, Long,
			Long, Tuple2<String, String>, Map<String, String>, String, Results>
			jobToJobInfo2(final Job j) {
		return new Tuple12<String, String, String, String,
				Tuple3<String, String, String>, Tuple3<Long, Long, String>,
				Long, Long, Tuple2<String, String>, Map<String, String>,
				String, Results>()
			.withE1(j.getID())
			.withE2(j.getService())
			.withE3(j.getStage())
			.withE4(j.getStatus())
			.withE5(new Tuple3<String, String, String>()
					.withE1(formatDate(j.getStarted()))
					.withE2(formatDate(j.getLastUpdated()))
					.withE3(formatDate(j.getEstimatedCompletion()))
			)
			.withE6(new Tuple3<Long, Long, String>()
					.withE1(j.getProgress() == null ? null :
						new Long(j.getProgress()))
					.withE2(j.getMaxProgress() == null ? null :
						new Long(j.getMaxProgress()))
					.withE3(j.getProgType())
			)
			.withE7(boolToLong(j.isComplete()))
			.withE8(boolToLong(j.hasError()))
			.withE9(new Tuple2<String, String>()
					.withE1(j.getAuthorizationStrategy().getStrat())
					.withE2(j.getAuthorizationParameter())
			)
			.withE10(j.getMetadata())
			.withE11(j.getDescription())
			.withE12(makeResults(j.getResults()));
	}
	
	private static Long boolToLong(final Boolean b) {
		if (b == null) {
			return null;
		}
		return b ? 1L : 0L;
	}
	
	private static Results makeResults(final JobResults res) {
		if (res == null) {
			return null;
		}
		final List<Result> r;
		if (res.getResults() != null) {
			r = new LinkedList<Result>();
			for (final JobResult jr: res.getResults()) {
				r.add(new Result()
				.withServerType(jr.getServtype())
				.withUrl(jr.getUrl())
				.withId(jr.getId())
				.withDescription(jr.getDesc()));
			}
		} else {
			r = null;
		}
		return new Results()
				.withShocknodes((List<String>) res.getShocknodes())
				.withShockurl((String)res.getShockurl())
				.withWorkspaceids((List<String>) res.getWorkspaceids())
				.withWorkspaceurl((String) res.getWorkspaceurl())
				.withResults(r);
	}
	
	private static JobResults unmakeResults(Results res) {
		if (res == null) {
			return null;
		}
		checkAddlArgs(res.getAdditionalProperties(), Results.class);
		final List<JobResult> jrs;
		if (res.getResults() != null) {
			jrs = new LinkedList<JobResult>();
			for (final Result r: res.getResults()) {
				checkAddlArgs(r.getAdditionalProperties(), Result.class);
				checkString(r.getServerType(), "servertype", MAX_LEN_SERVTYPE);
				checkString(r.getUrl(), "url", MAX_LEN_URL);
				checkString(r.getId(), "id", MAX_LEN_ID);
				checkMaxLen(r.getDescription(), "description", MAX_LEN_DESC);
				jrs.add(new JobResult(r.getServerType(), r.getUrl(), r.getId(),
						r.getDescription()));
			}
		} else {
			jrs = null;
		}
		return new JobResults(jrs,
				res.getWorkspaceurl(),
				res.getWorkspaceids(),
				res.getShockurl(),
				res.getShocknodes());
	}
		
	private Date parseDate(final String date) {
		if (date == null) {
			return null;
		}
		try {
			return DATE_PARSER.parseDateTime(date).toDate();
		} catch (IllegalArgumentException iae) {
			throw new IllegalArgumentException("Unparseable date: " +
					iae.getMessage());
		}
	}
	
	private boolean[] parseFilter(final String filter) {
		final boolean[] ret = new boolean[4];
		if (filter != null) {
			if (filter.indexOf("R") > -1) {
				ret[0] = true;
			}
			if (filter.indexOf("C") > -1) {
				ret[1] = true;
			}
			if (filter.indexOf("E") > -1) {
				ret[2] = true;
			}
			if (filter.indexOf("S") > -1) {
				ret[3] = true;
			}
		}
		return ret;
	}
	
	private String formatDate(final Date date) {
		return date == null ? null : DATE_FORMATTER.print(new DateTime(date));
	}
	
	private void checkUsers(final List<String> users, AuthToken token)
			throws IOException, AuthException {
		//token is guaranteed to not be null since all calls require
		//authentication
		if (users == null || users.isEmpty()) {
			throw new IllegalArgumentException(
					"The user list may not be null or empty");
		}
		for (final String u: users) {
			if (u == null || u.isEmpty()) {
				throw new IllegalArgumentException(
						"A user name cannot be null or the empty string");
			}
		}
		final Map<String, Boolean> userok = auth.isValidUserName(users);
		for (String u: userok.keySet()) {
			if (!userok.get(u)) {
				throw new IllegalArgumentException(String.format(
						"User %s is not a valid user", u));
			}
		}
	}
	
	public void setUpLogger() {
		Logger l = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		l.setLevel(Level.OFF);
		l.detachAndStopAllAppenders();
	}
	
	private int getReconnectCount() {
		final String rec = ujConfig.get(MONGO_RECONNECT);
		Integer recint = null;
		try {
			recint = Integer.parseInt(rec); 
		} catch (NumberFormatException nfe) {
			//do nothing
		}
		if (recint == null) {
			logInfo("Couldn't parse MongoDB reconnect value to an integer: " +
					rec + ", using 0");
			recint = 0;
		} else if (recint < 0) {
			logInfo("MongoDB reconnect value is < 0 (" + recint + "), using 0");
			recint = 0;
		} else {
			logInfo("MongoDB reconnect value is " + recint);
		}
		return recint;
	}
	

	private ConfigurableAuthService setUpAuthClient(
			final String kbaseAdminUser,
			final String kbaseAdminPwd) {
		AuthConfig c = new AuthConfig();
		ConfigurableAuthService auth;
		try {
			auth = new ConfigurableAuthService(c);
			c.withRefreshingToken(auth.getRefreshingToken(
					kbaseAdminUser, kbaseAdminPwd,
					TOKEN_REFRESH_INTERVAL_SEC));
			return auth;
		} catch (AuthException e) {
			fail("Couldn't log in the KBase administrative user " +
					kbaseAdminUser + " : " + e.getLocalizedMessage());
		} catch (IOException e) {
			fail("Couldn't connect to authorization service at " +
					c.getAuthServerURL() + " : " + e.getLocalizedMessage());
		}
		return null;
	}
	

	private WorkspaceAuthorizationFactory setUpWorkspaceAuth() {
		WorkspaceAuthorizationFactory authfac;
		final String wsStr = ujConfig.get(WORKSPACE_URL);
		if (wsStr != null && !wsStr.isEmpty()) {
			final URL wsURL;
			try {
				wsURL = new URL(wsStr);
				authfac = new WorkspaceAuthorizationFactory(wsURL);
			} catch (JsonClientException | IOException e) {
				authfac = null;
				fail("Error attempting to set up Workspace service " +
						"based authorization with URL " + wsStr + ": " +
						e.getLocalizedMessage());
			} 
		} else {
			LoggerFactory.getLogger(getClass()).info(
					"No workspace url detected in the configuration. " +
					"Any calls requiring workspace authorization will fail.");
			authfac = null;
		}
		return authfac;
	}
	
	private UJSAuthorizer getAuthorizer(final AuthToken token)
			throws UnauthorizedException, IOException {
		if (authfac == null) {
			return nows;
		} else {
			return authfac.buildAuthorizer(token);
		}
	}
	
	public static void clearConfigForTests() {
		ujConfig = null;
	}
    //END_CLASS_HEADER

    public UserAndJobStateServer() throws Exception {
        super("UserAndJobState");
        //BEGIN_CONSTRUCTOR
		//assign config once per jvm, otherwise you could wind up with
		//different threads talking to different mongo instances
		//E.g. first thread's config applies to all threads.
		if (ujConfig == null) {
			ujConfig = new HashMap<String, String>();
			ujConfig.putAll(super.config);
		}
		setUpLogger();
		boolean failed = false;
		if (!ujConfig.containsKey(HOST)) {
			fail("Must provide param " + HOST + " in config file");
			failed = true;
		}
		final String host = ujConfig.get(HOST);
		if (!ujConfig.containsKey(DB)) {
			fail("Must provide param " + DB + " in config file");
			failed = true;
		}
		final String dbs = ujConfig.get(DB);
		if (ujConfig.containsKey(USER) ^ ujConfig.containsKey(PWD)) {
			fail(String.format("Must provide both %s and %s ",
					USER, PWD) + "params in config file if authentication " + 
					"is to be used");
			failed = true;
		}
		
		if (!ujConfig.containsKey(KBASE_ADMIN_USER)) {
			fail("Must provide param " + KBASE_ADMIN_USER + " in config file");
			failed = true;
		}
		final String adminUser = ujConfig.get(KBASE_ADMIN_USER);
		if (!ujConfig.containsKey(KBASE_ADMIN_PWD)) {
			fail("Must provide param " + KBASE_ADMIN_PWD + " in config file");
			failed = true;
		}
		final String adminPwd = ujConfig.get(KBASE_ADMIN_PWD);
		
		if (failed) {
			fail("Server startup failed - all calls will error out.");
			us = null;
			js = null;
			auth = null;
			authfac = null;
		} else {
			final String user = ujConfig.get(USER);
			final String pwd = ujConfig.get(PWD);
			String params = "";
			for (String s: Arrays.asList(HOST, DB, USER)) {
				if (ujConfig.containsKey(s)) {
					params += s + "=" + ujConfig.get(s) + "\n";
				}
			}
			if (pwd != null) {
				params += PWD + "=[redacted for your safety and comfort]\n";
			}
			System.out.println("Starting server using connection parameters:\n"
					+ params);
			logInfo("Starting server using connection parameters:\n" + params);
			final int mongoConnectRetry = getReconnectCount();
			final DB ujsDB = getMongoDB(host, dbs, user, pwd, mongoConnectRetry);
			final SchemaManager sm = getSchemaManager(ujsDB, host);
			if (ujsDB == null || sm == null) {
				us = null;
				js = null;
				auth = null;
				authfac = null;
			} else {
				//TODO ZZLATER TEST add server startup tests.
				us = getUserState(ujsDB, sm, host);
				js = getJobState(ujsDB, sm, host);
				authfac = setUpWorkspaceAuth();
				auth = setUpAuthClient(adminUser, adminPwd);
			}
		}
        //END_CONSTRUCTOR
    }

    /**
     * <p>Original spec-file function name: ver</p>
     * <pre>
     * Returns the version of the userandjobstate service.
     * </pre>
     * @return   parameter "ver" of String
     */
    @JsonServerMethod(rpc = "UserAndJobState.ver", async=true)
    public String ver(RpcContext jsonRpcContext) throws Exception {
        String returnVal = null;
        //BEGIN ver
		returnVal = VER;
        //END ver
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: set_state</p>
     * <pre>
     * Set the state of a key for a service without service authentication.
     * </pre>
     * @param   service   instance of original type "service_name" (A service name. Alphanumerics and the underscore are allowed.)
     * @param   key   instance of String
     * @param   value   instance of unspecified object
     */
    @JsonServerMethod(rpc = "UserAndJobState.set_state", async=true)
    public void setState(String service, String key, UObject value, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN set_state
		us.setState(authPart.getUserName(), service, false, key,
				value == null ? null : value.asClassInstance(Object.class));
        //END set_state
    }

    /**
     * <p>Original spec-file function name: set_state_auth</p>
     * <pre>
     * Set the state of a key for a service with service authentication.
     * </pre>
     * @param   token   instance of original type "service_token" (A globus ID token that validates that the service really is said service.)
     * @param   key   instance of String
     * @param   value   instance of unspecified object
     */
    @JsonServerMethod(rpc = "UserAndJobState.set_state_auth", async=true)
    public void setStateAuth(String token, String key, UObject value, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN set_state_auth
		us.setState(authPart.getUserName(), getServiceUserName(token), true, key,
				value == null ? null : value.asClassInstance(Object.class));
        //END set_state_auth
    }

    /**
     * <p>Original spec-file function name: get_state</p>
     * <pre>
     * Get the state of a key for a service.
     * </pre>
     * @param   service   instance of original type "service_name" (A service name. Alphanumerics and the underscore are allowed.)
     * @param   key   instance of String
     * @param   auth   instance of original type "authed" (Specifies whether results returned should be from key/value pairs set with service authentication (true) or without (false).) &rarr; original type "boolean" (A boolean. 0 = false, other = true.)
     * @return   parameter "value" of unspecified object
     */
    @JsonServerMethod(rpc = "UserAndJobState.get_state", async=true)
    public UObject getState(String service, String key, Long auth, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        UObject returnVal = null;
        //BEGIN get_state
		returnVal = new UObject(us.getState(authPart.getUserName(), service,
				auth != 0, key));
        //END get_state
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: has_state</p>
     * <pre>
     * Determine if a key exists for a service.
     * </pre>
     * @param   service   instance of original type "service_name" (A service name. Alphanumerics and the underscore are allowed.)
     * @param   key   instance of String
     * @param   auth   instance of original type "authed" (Specifies whether results returned should be from key/value pairs set with service authentication (true) or without (false).) &rarr; original type "boolean" (A boolean. 0 = false, other = true.)
     * @return   parameter "has_key" of original type "boolean" (A boolean. 0 = false, other = true.)
     */
    @JsonServerMethod(rpc = "UserAndJobState.has_state", async=true)
    public Long hasState(String service, String key, Long auth, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Long returnVal = null;
        //BEGIN has_state
		returnVal = boolToLong(us.hasState(authPart.getUserName(), service,
				auth != 0, key));
        //END has_state
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_has_state</p>
     * <pre>
     * Get the state of a key for a service, and do not throw an error if the
     * key doesn't exist. If the key doesn't exist, has_key will be false
     * and the key value will be null.
     * </pre>
     * @param   service   instance of original type "service_name" (A service name. Alphanumerics and the underscore are allowed.)
     * @param   key   instance of String
     * @param   auth   instance of original type "authed" (Specifies whether results returned should be from key/value pairs set with service authentication (true) or without (false).) &rarr; original type "boolean" (A boolean. 0 = false, other = true.)
     * @return   multiple set: (1) parameter "has_key" of original type "boolean" (A boolean. 0 = false, other = true.), (2) parameter "value" of unspecified object
     */
    @JsonServerMethod(rpc = "UserAndJobState.get_has_state", tuple = true, async=true)
    public Tuple2<Long, UObject> getHasState(String service, String key, Long auth, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Long return1 = null;
        UObject return2 = null;
        //BEGIN get_has_state
		final KeyState ks = us.getState(authPart.getUserName(), service,
				auth != 0, key, false);
		return1 = boolToLong(ks.exists());
		return2 = new UObject(ks.getValue());
        //END get_has_state
        Tuple2<Long, UObject> returnVal = new Tuple2<Long, UObject>();
        returnVal.setE1(return1);
        returnVal.setE2(return2);
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: remove_state</p>
     * <pre>
     * Remove a key value pair without service authentication.
     * </pre>
     * @param   service   instance of original type "service_name" (A service name. Alphanumerics and the underscore are allowed.)
     * @param   key   instance of String
     */
    @JsonServerMethod(rpc = "UserAndJobState.remove_state", async=true)
    public void removeState(String service, String key, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN remove_state
		us.removeState(authPart.getUserName(), service, false, key);
        //END remove_state
    }

    /**
     * <p>Original spec-file function name: remove_state_auth</p>
     * <pre>
     * Remove a key value pair with service authentication.
     * </pre>
     * @param   token   instance of original type "service_token" (A globus ID token that validates that the service really is said service.)
     * @param   key   instance of String
     */
    @JsonServerMethod(rpc = "UserAndJobState.remove_state_auth", async=true)
    public void removeStateAuth(String token, String key, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN remove_state_auth
		us.removeState(authPart.getUserName(), getServiceUserName(token), true,
				key);	
        //END remove_state_auth
    }

    /**
     * <p>Original spec-file function name: list_state</p>
     * <pre>
     * List all keys.
     * </pre>
     * @param   service   instance of original type "service_name" (A service name. Alphanumerics and the underscore are allowed.)
     * @param   auth   instance of original type "authed" (Specifies whether results returned should be from key/value pairs set with service authentication (true) or without (false).) &rarr; original type "boolean" (A boolean. 0 = false, other = true.)
     * @return   parameter "keys" of list of String
     */
    @JsonServerMethod(rpc = "UserAndJobState.list_state", async=true)
    public List<String> listState(String service, Long auth, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<String> returnVal = null;
        //BEGIN list_state
		returnVal = new LinkedList<String>(us.listState(authPart.getUserName(),
				service, auth != 0));
        //END list_state
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: list_state_services</p>
     * <pre>
     * List all state services.
     * </pre>
     * @param   auth   instance of original type "authed" (Specifies whether results returned should be from key/value pairs set with service authentication (true) or without (false).) &rarr; original type "boolean" (A boolean. 0 = false, other = true.)
     * @return   parameter "services" of list of original type "service_name" (A service name. Alphanumerics and the underscore are allowed.)
     */
    @JsonServerMethod(rpc = "UserAndJobState.list_state_services", async=true)
    public List<String> listStateServices(Long auth, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<String> returnVal = null;
        //BEGIN list_state_services
		returnVal = new LinkedList<String>(us.listServices(
				authPart.getUserName(), auth != 0));
        //END list_state_services
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: create_job2</p>
     * <pre>
     * Create a new job status report.
     * </pre>
     * @param   params   instance of type {@link us.kbase.userandjobstate.CreateJobParams CreateJobParams}
     * @return   parameter "job" of original type "job_id" (A job id.)
     */
    @JsonServerMethod(rpc = "UserAndJobState.create_job2", async=true)
    public String createJob2(CreateJobParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        String returnVal = null;
        //BEGIN create_job2
		final WorkspaceUserMetadata meta =
				new WorkspaceUserMetadata(params.getMeta());
		final String user = authPart.getUserName();
		final String as = params.getAuthstrat();
		if (as == null || as.isEmpty() ||
				as.equals(UJSAuthorizer.DEFAULT_AUTH_STRAT.getStrat())) {
			returnVal = js.createJob(user, new DefaultUJSAuthorizer(),
					UJSAuthorizer.DEFAULT_AUTH_STRAT,
					UJSAuthorizer.DEFAULT_AUTH_PARAM, meta);
		} else {
			returnVal = js.createJob(user, getAuthorizer(authPart),
					new AuthorizationStrategy(params.getAuthstrat()),
					params.getAuthparam(), meta);
		}
        //END create_job2
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: create_job</p>
     * <pre>
     * Create a new job status report.
     * @deprecated create_job2
     * </pre>
     * @return   parameter "job" of original type "job_id" (A job id.)
     */
    @JsonServerMethod(rpc = "UserAndJobState.create_job", async=true)
    public String createJob(AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        String returnVal = null;
        //BEGIN create_job
		returnVal = js.createJob(authPart.getUserName());
        //END create_job
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: start_job</p>
     * <pre>
     * Start a job and specify the job parameters.
     * </pre>
     * @param   job   instance of original type "job_id" (A job id.)
     * @param   token   instance of original type "service_token" (A globus ID token that validates that the service really is said service.)
     * @param   status   instance of original type "job_status" (A job status string supplied by the reporting service. No more than 200 characters.)
     * @param   desc   instance of original type "job_description" (A job description string supplied by the reporting service. No more than 1000 characters.)
     * @param   progress   instance of type {@link us.kbase.userandjobstate.InitProgress InitProgress}
     * @param   estComplete   instance of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time))
     */
    @JsonServerMethod(rpc = "UserAndJobState.start_job", async=true)
    public void startJob(String job, String token, String status, String desc, InitProgress progress, String estComplete, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN start_job
		if (progress == null) {
			throw new IllegalArgumentException("InitProgress cannot be null");
		}
		checkAddlArgs(progress.getAdditionalProperties(), InitProgress.class);
		if (progress.getPtype() == null) {
			throw new IllegalArgumentException("Progress type cannot be null");
		}
		if (progress.getPtype().equals(JobState.PROG_NONE)) {
			js.startJob(authPart.getUserName(), job,
					getServiceUserName(token), status, desc,
					parseDate(estComplete));
		} else if (progress.getPtype().equals(JobState.PROG_PERC)) {
			js.startJobWithPercentProg(
					authPart.getUserName(), job, getServiceUserName(token), status,
					desc, parseDate(estComplete));
		} else if (progress.getPtype().equals(JobState.PROG_TASK)) {
			if (progress.getMax() == null) {
				throw new IllegalArgumentException(
						"Max progress cannot be null for task based progress");
			}
			if (progress.getMax().longValue() > Integer.MAX_VALUE) {
				throw new IllegalArgumentException(
						"Max progress can be no greater than "
						+ Integer.MAX_VALUE);
			}
			js.startJob(authPart.getUserName(), job,
					getServiceUserName(token), status, desc,
					(int) progress.getMax().longValue(),
					parseDate(estComplete));
		} else {
			throw new IllegalArgumentException("No such progress type: " +
					progress.getPtype());
		}
        //END start_job
    }

    /**
     * <p>Original spec-file function name: create_and_start_job</p>
     * <pre>
     * Create and start a job.
     * </pre>
     * @param   token   instance of original type "service_token" (A globus ID token that validates that the service really is said service.)
     * @param   status   instance of original type "job_status" (A job status string supplied by the reporting service. No more than 200 characters.)
     * @param   desc   instance of original type "job_description" (A job description string supplied by the reporting service. No more than 1000 characters.)
     * @param   progress   instance of type {@link us.kbase.userandjobstate.InitProgress InitProgress}
     * @param   estComplete   instance of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time))
     * @return   parameter "job" of original type "job_id" (A job id.)
     */
    @JsonServerMethod(rpc = "UserAndJobState.create_and_start_job", async=true)
    public String createAndStartJob(String token, String status, String desc, InitProgress progress, String estComplete, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        String returnVal = null;
        //BEGIN create_and_start_job
		//could combine with above, but it'd be a huge mess
		if (progress == null) {
			throw new IllegalArgumentException("InitProgress cannot be null");
		}
		checkAddlArgs(progress.getAdditionalProperties(), InitProgress.class);
		if (progress.getPtype() == null) {
			throw new IllegalArgumentException("Progress type cannot be null");
		}
		if (progress.getPtype().equals(JobState.PROG_NONE)) {
			returnVal = js.createAndStartJob(authPart.getUserName(),
					getServiceUserName(token), status, desc, parseDate(estComplete));
		} else if (progress.getPtype().equals(JobState.PROG_PERC)) {
			returnVal = js.createAndStartJobWithPercentProg(
					authPart.getUserName(), getServiceUserName(token), status, desc,
					parseDate(estComplete));
		} else if (progress.getPtype().equals(JobState.PROG_TASK)) {
			if (progress.getMax() == null) {
				throw new IllegalArgumentException(
						"Max progress cannot be null for task based progress");
			}
			if (progress.getMax().longValue() > Integer.MAX_VALUE) {
				throw new IllegalArgumentException(
						"Max progress can be no greater than "
						+ Integer.MAX_VALUE);
			}
			returnVal = js.createAndStartJob(authPart.getUserName(),
					getServiceUserName(token), status, desc,
					(int) progress.getMax().longValue(),
					parseDate(estComplete));
		} else {
			throw new IllegalArgumentException("No such progress type: " +
					progress.getPtype());
		}
        //END create_and_start_job
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: update_job_progress</p>
     * <pre>
     * Update the status and progress for a job.
     * </pre>
     * @param   job   instance of original type "job_id" (A job id.)
     * @param   token   instance of original type "service_token" (A globus ID token that validates that the service really is said service.)
     * @param   status   instance of original type "job_status" (A job status string supplied by the reporting service. No more than 200 characters.)
     * @param   prog   instance of original type "progress" (The amount of progress the job has made since the last update. This will be summed to the total progress so far.)
     * @param   estComplete   instance of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time))
     */
    @JsonServerMethod(rpc = "UserAndJobState.update_job_progress", async=true)
    public void updateJobProgress(String job, String token, String status, Long prog, String estComplete, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN update_job_progress
		Integer progval = null;
		if (prog != null) {
			if (prog.longValue() > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(
					"Max progress can be no greater than "
					+ Integer.MAX_VALUE);
			}
			progval = (int) prog.longValue();
		}
		js.updateJob(authPart.getUserName(), job,
				getServiceUserName(token), status, progval,
				parseDate(estComplete));
        //END update_job_progress
    }

    /**
     * <p>Original spec-file function name: update_job</p>
     * <pre>
     * Update the status for a job.
     * </pre>
     * @param   job   instance of original type "job_id" (A job id.)
     * @param   token   instance of original type "service_token" (A globus ID token that validates that the service really is said service.)
     * @param   status   instance of original type "job_status" (A job status string supplied by the reporting service. No more than 200 characters.)
     * @param   estComplete   instance of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time))
     */
    @JsonServerMethod(rpc = "UserAndJobState.update_job", async=true)
    public void updateJob(String job, String token, String status, String estComplete, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN update_job
		js.updateJob(authPart.getUserName(), job,
				getServiceUserName(token), status, null, parseDate(estComplete));
        //END update_job
    }

    /**
     * <p>Original spec-file function name: get_job_description</p>
     * <pre>
     * Get the description of a job.
     * </pre>
     * @param   job   instance of original type "job_id" (A job id.)
     * @return   multiple set: (1) parameter "service" of original type "service_name" (A service name. Alphanumerics and the underscore are allowed.), (2) parameter "ptype" of original type "progress_type" (The type of progress that is being tracked. One of: 'none' - no numerical progress tracking 'task' - Task based tracking, e.g. 3/24 'percent' - percentage based tracking, e.g. 5/100%), (3) parameter "max" of original type "max_progress" (The maximum possible progress of a job.), (4) parameter "desc" of original type "job_description" (A job description string supplied by the reporting service. No more than 1000 characters.), (5) parameter "started" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time))
     */
    @JsonServerMethod(rpc = "UserAndJobState.get_job_description", tuple = true, async=true)
    public Tuple5<String, String, Long, String, String> getJobDescription(String job, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        String return1 = null;
        String return2 = null;
        Long return3 = null;
        String return4 = null;
        String return5 = null;
        //BEGIN get_job_description
		final Job j = js.getJob(authPart.getUserName(), job,
				getAuthorizer(authPart));
		return1 = j.getService();
		return2 = j.getProgType();
		return3 = j.getMaxProgress() == null ? null :
			new Long(j.getMaxProgress());
		return4 = j.getDescription();
		return5 = formatDate(j.getStarted());
        //END get_job_description
        Tuple5<String, String, Long, String, String> returnVal = new Tuple5<String, String, Long, String, String>();
        returnVal.setE1(return1);
        returnVal.setE2(return2);
        returnVal.setE3(return3);
        returnVal.setE4(return4);
        returnVal.setE5(return5);
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_job_status</p>
     * <pre>
     * Get the status of a job.
     * </pre>
     * @param   job   instance of original type "job_id" (A job id.)
     * @return   multiple set: (1) parameter "last_update" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), (2) parameter "stage" of original type "job_stage" (A string that describes the stage of processing of the job. One of 'created', 'started', 'completed', or 'error'.), (3) parameter "status" of original type "job_status" (A job status string supplied by the reporting service. No more than 200 characters.), (4) parameter "progress" of original type "total_progress" (The total progress of a job.), (5) parameter "est_complete" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), (6) parameter "complete" of original type "boolean" (A boolean. 0 = false, other = true.), (7) parameter "error" of original type "boolean" (A boolean. 0 = false, other = true.)
     */
    @JsonServerMethod(rpc = "UserAndJobState.get_job_status", tuple = true, async=true)
    public Tuple7<String, String, String, Long, String, Long, Long> getJobStatus(String job, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        String return1 = null;
        String return2 = null;
        String return3 = null;
        Long return4 = null;
        String return5 = null;
        Long return6 = null;
        Long return7 = null;
        //BEGIN get_job_status
		final Job j = js.getJob(authPart.getUserName(), job,
				getAuthorizer(authPart));
		return1 = formatDate(j.getLastUpdated());
		return2 = j.getStage();
		return3 = j.getStatus();
		return4 = j.getProgress() == null ? null :new Long(j.getProgress());
		return5 = formatDate(j.getEstimatedCompletion());
		return6 = boolToLong(j.isComplete());
		return7 = boolToLong(j.hasError());
        //END get_job_status
        Tuple7<String, String, String, Long, String, Long, Long> returnVal = new Tuple7<String, String, String, Long, String, Long, Long>();
        returnVal.setE1(return1);
        returnVal.setE2(return2);
        returnVal.setE3(return3);
        returnVal.setE4(return4);
        returnVal.setE5(return5);
        returnVal.setE6(return6);
        returnVal.setE7(return7);
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: complete_job</p>
     * <pre>
     * Complete the job. After the job is completed, total_progress always
     * equals max_progress. If detailed_err is anything other than null,
     * the job is considered to have errored out.
     * </pre>
     * @param   job   instance of original type "job_id" (A job id.)
     * @param   token   instance of original type "service_token" (A globus ID token that validates that the service really is said service.)
     * @param   status   instance of original type "job_status" (A job status string supplied by the reporting service. No more than 200 characters.)
     * @param   error   instance of original type "detailed_err" (Detailed information about a job error, such as a stacktrace, that will not fit in the job_status. No more than 100K characters.)
     * @param   res   instance of type {@link us.kbase.userandjobstate.Results Results}
     */
    @JsonServerMethod(rpc = "UserAndJobState.complete_job", async=true)
    public void completeJob(String job, String token, String status, String error, Results res, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN complete_job
		js.completeJob(authPart.getUserName(), job,
				getServiceUserName(token), status, error, unmakeResults(res));
        //END complete_job
    }

    /**
     * <p>Original spec-file function name: get_results</p>
     * <pre>
     * Get the job results.
     * </pre>
     * @param   job   instance of original type "job_id" (A job id.)
     * @return   parameter "res" of type {@link us.kbase.userandjobstate.Results Results}
     */
    @JsonServerMethod(rpc = "UserAndJobState.get_results", async=true)
    public Results getResults(String job, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Results returnVal = null;
        //BEGIN get_results
		returnVal = makeResults(js.getJob(authPart.getUserName(), job,
				getAuthorizer(authPart)).getResults());
        //END get_results
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_detailed_error</p>
     * <pre>
     * Get the detailed error message, if any
     * </pre>
     * @param   job   instance of original type "job_id" (A job id.)
     * @return   parameter "error" of original type "detailed_err" (Detailed information about a job error, such as a stacktrace, that will not fit in the job_status. No more than 100K characters.)
     */
    @JsonServerMethod(rpc = "UserAndJobState.get_detailed_error", async=true)
    public String getDetailedError(String job, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        String returnVal = null;
        //BEGIN get_detailed_error
		returnVal =  js.getJob(authPart.getUserName(), job,
				getAuthorizer(authPart)).getErrorMsg();
        //END get_detailed_error
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_job_info2</p>
     * <pre>
     * Get information about a job.
     * </pre>
     * @param   job   instance of original type "job_id" (A job id.)
     * @return   parameter "info" of original type "job_info2" (Information about a job.) &rarr; tuple of size 12: parameter "job" of original type "job_id" (A job id.), parameter "service" of original type "service_name" (A service name. Alphanumerics and the underscore are allowed.), parameter "stage" of original type "job_stage" (A string that describes the stage of processing of the job. One of 'created', 'started', 'completed', or 'error'.), parameter "status" of original type "job_status" (A job status string supplied by the reporting service. No more than 200 characters.), parameter "times" of original type "time_info" (Job timing information.) &rarr; tuple of size 3: parameter "started" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "last_update" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "est_complete" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "progress" of original type "progress_info" (Job progress information.) &rarr; tuple of size 3: parameter "prog" of original type "total_progress" (The total progress of a job.), parameter "max" of original type "max_progress" (The maximum possible progress of a job.), parameter "ptype" of original type "progress_type" (The type of progress that is being tracked. One of: 'none' - no numerical progress tracking 'task' - Task based tracking, e.g. 3/24 'percent' - percentage based tracking, e.g. 5/100%), parameter "complete" of original type "boolean" (A boolean. 0 = false, other = true.), parameter "error" of original type "boolean" (A boolean. 0 = false, other = true.), parameter "auth" of original type "auth_info" (Job authorization strategy information.) &rarr; tuple of size 2: parameter "strat" of original type "auth_strategy" (An authorization strategy to use for jobs. Other than the DEFAULT strategy (ACLs local to the UJS and managed by the UJS sharing functions), currently the only other strategy is the 'kbaseworkspace' strategy, which consults the workspace service for authorization information.), parameter "param" of original type "auth_param" (An authorization parameter. The contents of this parameter differ by auth_strategy, but for the workspace strategy it is the workspace id (an integer) as a string.), parameter "meta" of original type "usermeta" (User provided metadata about a job. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String, parameter "desc" of original type "job_description" (A job description string supplied by the reporting service. No more than 1000 characters.), parameter "res" of type {@link us.kbase.userandjobstate.Results Results}
     */
    @JsonServerMethod(rpc = "UserAndJobState.get_job_info2", async=true)
    public Tuple12<String, String, String, String, Tuple3<String, String, String>, Tuple3<Long, Long, String>, Long, Long, Tuple2<String, String>, Map<String,String>, String, Results> getJobInfo2(String job, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Tuple12<String, String, String, String, Tuple3<String, String, String>, Tuple3<Long, Long, String>, Long, Long, Tuple2<String, String>, Map<String,String>, String, Results> returnVal = null;
        //BEGIN get_job_info2
		returnVal = jobToJobInfo2(js.getJob(authPart.getUserName(), job,
				getAuthorizer(authPart)));
        //END get_job_info2
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_job_info</p>
     * <pre>
     * Get information about a job.
     * @deprecated get_job_info2
     * </pre>
     * @param   job   instance of original type "job_id" (A job id.)
     * @return   parameter "info" of original type "job_info" (Information about a job. @deprecated job_info2) &rarr; tuple of size 14: parameter "job" of original type "job_id" (A job id.), parameter "service" of original type "service_name" (A service name. Alphanumerics and the underscore are allowed.), parameter "stage" of original type "job_stage" (A string that describes the stage of processing of the job. One of 'created', 'started', 'completed', or 'error'.), parameter "started" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "status" of original type "job_status" (A job status string supplied by the reporting service. No more than 200 characters.), parameter "last_update" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "prog" of original type "total_progress" (The total progress of a job.), parameter "max" of original type "max_progress" (The maximum possible progress of a job.), parameter "ptype" of original type "progress_type" (The type of progress that is being tracked. One of: 'none' - no numerical progress tracking 'task' - Task based tracking, e.g. 3/24 'percent' - percentage based tracking, e.g. 5/100%), parameter "est_complete" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "complete" of original type "boolean" (A boolean. 0 = false, other = true.), parameter "error" of original type "boolean" (A boolean. 0 = false, other = true.), parameter "desc" of original type "job_description" (A job description string supplied by the reporting service. No more than 1000 characters.), parameter "res" of type {@link us.kbase.userandjobstate.Results Results}
     */
    @JsonServerMethod(rpc = "UserAndJobState.get_job_info", async=true)
    public Tuple14<String, String, String, String, String, String, Long, Long, String, String, Long, Long, String, Results> getJobInfo(String job, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Tuple14<String, String, String, String, String, String, Long, Long, String, String, Long, Long, String, Results> returnVal = null;
        //BEGIN get_job_info
		returnVal = jobToJobInfo(js.getJob(authPart.getUserName(), job,
				getAuthorizer(authPart)));
        //END get_job_info
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: list_jobs2</p>
     * <pre>
     * List jobs.
     * </pre>
     * @param   params   instance of type {@link us.kbase.userandjobstate.ListJobsParams ListJobsParams}
     * @return   parameter "jobs" of list of original type "job_info2" (Information about a job.) &rarr; tuple of size 12: parameter "job" of original type "job_id" (A job id.), parameter "service" of original type "service_name" (A service name. Alphanumerics and the underscore are allowed.), parameter "stage" of original type "job_stage" (A string that describes the stage of processing of the job. One of 'created', 'started', 'completed', or 'error'.), parameter "status" of original type "job_status" (A job status string supplied by the reporting service. No more than 200 characters.), parameter "times" of original type "time_info" (Job timing information.) &rarr; tuple of size 3: parameter "started" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "last_update" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "est_complete" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "progress" of original type "progress_info" (Job progress information.) &rarr; tuple of size 3: parameter "prog" of original type "total_progress" (The total progress of a job.), parameter "max" of original type "max_progress" (The maximum possible progress of a job.), parameter "ptype" of original type "progress_type" (The type of progress that is being tracked. One of: 'none' - no numerical progress tracking 'task' - Task based tracking, e.g. 3/24 'percent' - percentage based tracking, e.g. 5/100%), parameter "complete" of original type "boolean" (A boolean. 0 = false, other = true.), parameter "error" of original type "boolean" (A boolean. 0 = false, other = true.), parameter "auth" of original type "auth_info" (Job authorization strategy information.) &rarr; tuple of size 2: parameter "strat" of original type "auth_strategy" (An authorization strategy to use for jobs. Other than the DEFAULT strategy (ACLs local to the UJS and managed by the UJS sharing functions), currently the only other strategy is the 'kbaseworkspace' strategy, which consults the workspace service for authorization information.), parameter "param" of original type "auth_param" (An authorization parameter. The contents of this parameter differ by auth_strategy, but for the workspace strategy it is the workspace id (an integer) as a string.), parameter "meta" of original type "usermeta" (User provided metadata about a job. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String, parameter "desc" of original type "job_description" (A job description string supplied by the reporting service. No more than 1000 characters.), parameter "res" of type {@link us.kbase.userandjobstate.Results Results}
     */
    @JsonServerMethod(rpc = "UserAndJobState.list_jobs2", async=true)
    public List<Tuple12<String, String, String, String, Tuple3<String, String, String>, Tuple3<Long, Long, String>, Long, Long, Tuple2<String, String>, Map<String,String>, String, Results>> listJobs2(ListJobsParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<Tuple12<String, String, String, String, Tuple3<String, String, String>, Tuple3<Long, Long, String>, Long, Long, Tuple2<String, String>, Map<String,String>, String, Results>> returnVal = null;
        //BEGIN list_jobs2
		final boolean[] rces = parseFilter(params.getFilter());
		final List<String> services = params.getServices();
		final List<Job> jobs;
		final String as = params.getAuthstrat();
		if (as == null || as.isEmpty() ||
				as.equals(UJSAuthorizer.DEFAULT_AUTH_STRAT.getStrat())) {
			jobs = js.listJobs(authPart.getUserName(), services,
				rces[0], rces[1], rces[2], rces[3]);
		} else {
			jobs = js.listJobs(authPart.getUserName(), services,
					rces[0], rces[1], rces[2], rces[3],
					getAuthorizer(authPart),
					new AuthorizationStrategy(params.getAuthstrat()),
					params.getAuthparams());
		}
		returnVal = new LinkedList<Tuple12<String, String, String, String,
				Tuple3<String, String, String>, Tuple3<Long, Long, String>,
				Long, Long, Tuple2<String, String>, Map<String, String>,
				String, Results>>();
		for (final Job j: jobs) {
			returnVal.add(jobToJobInfo2(j));
		}
        //END list_jobs2
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: list_jobs</p>
     * <pre>
     * List jobs. Leave 'services' empty or null to list jobs from all
     * services.
     * @deprecated list_jobs2
     * </pre>
     * @param   services   instance of list of original type "service_name" (A service name. Alphanumerics and the underscore are allowed.)
     * @param   filter   instance of original type "job_filter" (A string-based filter for listing jobs. If the string contains: 'R' - running jobs are returned. 'C' - completed jobs are returned. 'E' - jobs that errored out are returned. 'S' - shared jobs are returned. The string can contain any combination of these codes in any order. If the string contains none of the codes or is null, all self-owned jobs are returned. If only the S filter is present, all jobs are returned. The S filter is ignored for jobs not using the default authorization strategy.)
     * @return   parameter "jobs" of list of original type "job_info" (Information about a job. @deprecated job_info2) &rarr; tuple of size 14: parameter "job" of original type "job_id" (A job id.), parameter "service" of original type "service_name" (A service name. Alphanumerics and the underscore are allowed.), parameter "stage" of original type "job_stage" (A string that describes the stage of processing of the job. One of 'created', 'started', 'completed', or 'error'.), parameter "started" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "status" of original type "job_status" (A job status string supplied by the reporting service. No more than 200 characters.), parameter "last_update" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "prog" of original type "total_progress" (The total progress of a job.), parameter "max" of original type "max_progress" (The maximum possible progress of a job.), parameter "ptype" of original type "progress_type" (The type of progress that is being tracked. One of: 'none' - no numerical progress tracking 'task' - Task based tracking, e.g. 3/24 'percent' - percentage based tracking, e.g. 5/100%), parameter "est_complete" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "complete" of original type "boolean" (A boolean. 0 = false, other = true.), parameter "error" of original type "boolean" (A boolean. 0 = false, other = true.), parameter "desc" of original type "job_description" (A job description string supplied by the reporting service. No more than 1000 characters.), parameter "res" of type {@link us.kbase.userandjobstate.Results Results}
     */
    @JsonServerMethod(rpc = "UserAndJobState.list_jobs", async=true)
    public List<Tuple14<String, String, String, String, String, String, Long, Long, String, String, Long, Long, String, Results>> listJobs(List<String> services, String filter, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<Tuple14<String, String, String, String, String, String, Long, Long, String, String, Long, Long, String, Results>> returnVal = null;
        //BEGIN list_jobs
		final boolean[] rces = parseFilter(filter);
		returnVal = new LinkedList<Tuple14<String, String, String, String,
				String, String, Long, Long, String, String, Long,
				Long, String, Results>>();
		for (final Job j: js.listJobs(authPart.getUserName(), services,
				rces[0], rces[1], rces[2], rces[3])) {
			returnVal.add(jobToJobInfo(j));
		}
        //END list_jobs
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: list_job_services</p>
     * <pre>
     * List all job services. Note that only services with jobs owned by the
     * user or shared with the user via the default auth strategy will be
     * listed.
     * </pre>
     * @return   parameter "services" of list of original type "service_name" (A service name. Alphanumerics and the underscore are allowed.)
     */
    @JsonServerMethod(rpc = "UserAndJobState.list_job_services", async=true)
    public List<String> listJobServices(AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<String> returnVal = null;
        //BEGIN list_job_services
		returnVal = new ArrayList<String>(js.listServices(
				authPart.getUserName()));
        //END list_job_services
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: share_job</p>
     * <pre>
     * Share a job. Sharing a job to the same user twice or with the job owner
     * has no effect. Attempting to share a job not using the default auth
     * strategy will fail.
     * </pre>
     * @param   job   instance of original type "job_id" (A job id.)
     * @param   users   instance of list of original type "username" (Login name of a KBase user account.)
     */
    @JsonServerMethod(rpc = "UserAndJobState.share_job", async=true)
    public void shareJob(String job, List<String> users, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN share_job
		checkUsers(users, authPart);
		js.shareJob(
				authPart.getUserName(), job, users);
        //END share_job
    }

    /**
     * <p>Original spec-file function name: unshare_job</p>
     * <pre>
     * Stop sharing a job. Removing sharing from a user that the job is not
     * shared with or the job owner has no effect. Attemping to unshare a job
     * not using the default auth strategy will fail.
     * </pre>
     * @param   job   instance of original type "job_id" (A job id.)
     * @param   users   instance of list of original type "username" (Login name of a KBase user account.)
     */
    @JsonServerMethod(rpc = "UserAndJobState.unshare_job", async=true)
    public void unshareJob(String job, List<String> users, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN unshare_job
		checkUsers(users, authPart);
		js.unshareJob(
				authPart.getUserName(), job, users);
        //END unshare_job
    }

    /**
     * <p>Original spec-file function name: get_job_owner</p>
     * <pre>
     * Get the owner of a job.
     * </pre>
     * @param   job   instance of original type "job_id" (A job id.)
     * @return   parameter "owner" of original type "username" (Login name of a KBase user account.)
     */
    @JsonServerMethod(rpc = "UserAndJobState.get_job_owner", async=true)
    public String getJobOwner(String job, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        String returnVal = null;
        //BEGIN get_job_owner
		returnVal = js.getJob(authPart.getUserName(), job,
				getAuthorizer(authPart)).getUser();
        //END get_job_owner
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_job_shared</p>
     * <pre>
     * Get the list of users with which a job is shared. Only the job owner
     * may access this method. Returns an empty list for jobs not using the
     * default auth strategy.
     * </pre>
     * @param   job   instance of original type "job_id" (A job id.)
     * @return   parameter "users" of list of original type "username" (Login name of a KBase user account.)
     */
    @JsonServerMethod(rpc = "UserAndJobState.get_job_shared", async=true)
    public List<String> getJobShared(String job, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<String> returnVal = null;
        //BEGIN get_job_shared
		final Job j = js.getJob(authPart.getUserName(), job,
				getAuthorizer(authPart));
		if (!j.getUser().equals(authPart.getUserName())) {
			throw new IllegalArgumentException(String.format(
					"User %s may not access the sharing list of job %s",
					authPart.getUserName(), job));
		}
		returnVal = j.getShared();
        //END get_job_shared
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: delete_job</p>
     * <pre>
     * Delete a job. Will fail if the job is not complete.
     * </pre>
     * @param   job   instance of original type "job_id" (A job id.)
     */
    @JsonServerMethod(rpc = "UserAndJobState.delete_job", async=true)
    public void deleteJob(String job, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN delete_job
		js.deleteJob(authPart.getUserName(), job);
        //END delete_job
    }

    /**
     * <p>Original spec-file function name: force_delete_job</p>
     * <pre>
     * Force delete a job - will succeed unless the job has not been started.
     * In that case, the service must start the job and then delete it, since
     * a job is not "owned" by any service until it is started.
     * </pre>
     * @param   token   instance of original type "service_token" (A globus ID token that validates that the service really is said service.)
     * @param   job   instance of original type "job_id" (A job id.)
     */
    @JsonServerMethod(rpc = "UserAndJobState.force_delete_job", async=true)
    public void forceDeleteJob(String token, String job, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN force_delete_job
		js.deleteJob(authPart.getUserName(), job, getServiceUserName(token));
        //END force_delete_job
    }
    @JsonServerMethod(rpc = "UserAndJobState.status")
    public Map<String, Object> status() {
        Map<String, Object> returnVal = null;
        //BEGIN_STATUS
        //TODO ZZLATER check mongo & memory
		returnVal = new LinkedHashMap<String, Object>();
		returnVal.put("state", "OK");
		returnVal.put("message", "");
		returnVal.put("version", VER);
		returnVal.put("git_url", GIT);
		@SuppressWarnings("unused")
		String v = version;
		@SuppressWarnings("unused")
		String h = gitCommitHash;
		@SuppressWarnings("unused")
		String u = gitUrl;
        //END_STATUS
        return returnVal;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            new UserAndJobStateServer().startupServer(Integer.parseInt(args[0]));
        } else if (args.length == 3) {
            JsonServerSyslog.setStaticUseSyslog(false);
            JsonServerSyslog.setStaticMlogFile(args[1] + ".log");
            new UserAndJobStateServer().processRpcCall(new File(args[0]), new File(args[1]), args[2]);
        } else {
            System.out.println("Usage: <program> <server_port>");
            System.out.println("   or: <program> <context_json_file> <output_json_file> <token>");
            return;
        }
    }
}
