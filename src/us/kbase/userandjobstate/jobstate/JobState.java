package us.kbase.userandjobstate.jobstate;

import static us.kbase.common.utils.StringUtils.checkString;
import static us.kbase.common.utils.StringUtils.checkMaxLen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.jongo.Jongo;
import org.jongo.MongoCollection;

import us.kbase.common.schemamanager.SchemaManager;
import us.kbase.common.schemamanager.exceptions.SchemaException;
import us.kbase.userandjobstate.authorization.AuthorizationStrategy;
import us.kbase.userandjobstate.authorization.DefaultUJSAuthorizer;
import us.kbase.userandjobstate.authorization.UJSAuthorizer;
import us.kbase.userandjobstate.authorization.exceptions.UJSAuthorizationException;
import us.kbase.userandjobstate.exceptions.CommunicationException;
import us.kbase.userandjobstate.jobstate.exceptions.NoSuchJobException;
import us.kbase.workspace.database.WorkspaceUserMetadata;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.mongodb.WriteResult;

public class JobState {

//	private final static int JOB_EXPIRES = 180 * 24 * 60 * 60; // 180 days
	
	private final static int MAX_LEN_USER = 100;
	private final static int MAX_LEN_SERVICE = 100;
	private final static int MAX_LEN_STATUS = 200;
	private final static int MAX_LEN_DESC = 1000;
	private final static int MAX_LEN_ERR = 100000;
	
	private final static String CREATED = "created";
	private final static String USER = "user";
	private final static String SERVICE = "service";
	private final static String STARTED = "started";
	private final static String UPDATED = "updated";
	private final static String EST_COMP = "estcompl";
	private final static String COMPLETE = "complete";
	private final static String ERROR = "error";
	private final static String ERROR_MSG = "errormsg";
	private final static String DESCRIPTION = "desc";
	private final static String PROG_TYPE = "progtype";
	private final static String PROG = "prog";
	private final static String MAXPROG = "maxprog";
	private final static String STATUS = "status";
	private final static String RESULT = "results";
	private final static String SHARED = "shared";
	public static final String AUTH_STRAT = "authstrat";
	public static final String AUTH_PARAM = "authparam";
	public static final String METADATA = "meta";
	
	public final static String PROG_NONE = "none";
	public final static String PROG_TASK = "task";
	public final static String PROG_PERC = "percent";
	
	private final static String MONGO_ID = "_id";
	
	public static final String META_KEY = "k";
	public static final String META_VALUE = "v";
	
	public static final String SCHEMA_TYPE = "jobstate";
	public final static int SCHEMA_VER = 2;
	
	private final DBCollection jobcol;
	private final MongoCollection jobjong;
	
	public JobState(final DBCollection jobcol, final SchemaManager sm)
			throws SchemaException {
		if (jobcol == null) {
			throw new NullPointerException("jobcol");
		}
		this.jobcol = jobcol;
		jobjong = new Jongo(jobcol.getDB()).getCollection(jobcol.getName());
		ensureIndexes();
		sm.checkSchema(SCHEMA_TYPE, SCHEMA_VER);
	}

	private void ensureIndexes() {
		ensureUserIndex(USER);
		ensureUserIndex(SHARED);
		ensureAuthIndex();
//		final DBObject ttlidx = new BasicDBObject(CREATED, 1);
//		final DBObject opts = new BasicDBObject("expireAfterSeconds",
//				JOB_EXPIRES);
//		jobcol.ensureIndex(ttlidx, opts);
	}

	private void ensureUserIndex(final String userField) {
		final DBObject idx = new BasicDBObject();
		idx.put(userField, 1);
		idx.put(SERVICE, 1);
		idx.put(COMPLETE, 1);
		jobcol.createIndex(idx);
	}
	private void ensureAuthIndex() {
		final DBObject idx = new BasicDBObject();
		idx.put(AUTH_STRAT, 1);
		idx.put(AUTH_PARAM, 1);
		jobcol.createIndex(idx);
	}
	public String createJob(final String user)
			throws CommunicationException {
		try {
			return createJob(user, new DefaultUJSAuthorizer(),
					UJSAuthorizer.DEFAULT_AUTH_STRAT,
					UJSAuthorizer.DEFAULT_AUTH_PARAM,
					new WorkspaceUserMetadata());
		} catch (UJSAuthorizationException e) {
			throw new RuntimeException(
					"This should be impossible, but there you go", e);
		}
	}
	
	public String createJob(
			final String user,
			final UJSAuthorizer auth,
			final AuthorizationStrategy strat,
			final String authParam,
			//TODO ZZLATER this class should be renamed
			final WorkspaceUserMetadata meta)
			throws CommunicationException, UJSAuthorizationException {
		checkString(user, "user", MAX_LEN_USER);
		auth.authorizeCreate(strat, authParam);
		if (meta == null) {
			throw new NullPointerException("meta");
		}
		final DBObject job = new BasicDBObject(USER, user);
		final Date date = new Date();
		job.put(AUTH_STRAT, strat.getStrat());
		job.put(AUTH_PARAM, authParam);
		job.put(METADATA, metaToMongoArray(meta));
		job.put(CREATED, date);
		job.put(UPDATED, date);
		job.put(EST_COMP, null);
		job.put(SERVICE, null);
		try {
			jobcol.insert(job);
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
		return ((ObjectId) job.get(MONGO_ID)).toString();
	}
	
	private static List<Map<String, String>> metaToMongoArray(
			final WorkspaceUserMetadata wum) {
		final List<Map<String, String>> meta = 
				new ArrayList<Map<String, String>>();
		for (String key: wum.getMetadata().keySet()) {
			Map<String, String> m = new LinkedHashMap<String, String>(2);
			m.put(META_KEY, key);
			m.put(META_VALUE, wum.getMetadata().get(key));
			meta.add(m);
		}
		return meta;
	}
	
	private static ObjectId checkJobID(final String id) {
		checkString(id, "id");
		final ObjectId oi;
		try {
			oi = new ObjectId(id);
		} catch (IllegalArgumentException iae) {
			throw new IllegalArgumentException(String.format(
					"Job ID %s is not a legal ID", id));
		}
		return oi;
	}
	
	private final static String QRY_FIND_JOB_BY_OWNER = String.format(
			"{%s: #, %s: #}", MONGO_ID, USER);
	private final static String QRY_FIND_JOB_BY_USER = String.format(
			"{%s: #, $or: [{%s: #}, {%s: #}]}", MONGO_ID, USER, SHARED);
	private final static String QRY_FIND_JOB_NO_USER = String.format(
			"{%s: #}", MONGO_ID);
	
	public Job getJob(final String user, final String jobID)
			throws CommunicationException, NoSuchJobException {
		checkString(user, "user", MAX_LEN_USER);
		final ObjectId oi = checkJobID(jobID);
		return getJob(user, oi);
	}
	
	//TODO NOW user will have to go, auth outside of this class
	private Job getJob(final String user, final ObjectId jobID)
			throws CommunicationException, NoSuchJobException {
		final Job j;
		try {
			if (user == null) {
				j = jobjong.findOne(QRY_FIND_JOB_NO_USER, jobID).as(Job.class);
			} else {
				j = jobjong.findOne(QRY_FIND_JOB_BY_USER, jobID, user, user)
						.as(Job.class);
			}
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
		if (j == null) {
			throw new NoSuchJobException(String.format(
					"There is no job %s viewable by user %s", jobID, user));
		}
		return j;
	}
	
	public void startJob(final String user, final String jobID,
			final String service, final String status,
			final String description, final Date estComplete)
			throws CommunicationException, NoSuchJobException {
		startJob(user, jobID, service, status, description, PROG_NONE, null,
				estComplete);
	}
	
	public void startJob(final String user, final String jobID,
			final String service, final String status,
			final String description, final int maxProg,
			final Date estComplete)
			throws CommunicationException, NoSuchJobException {
		startJob(user, jobID, service, status, description, PROG_TASK, maxProg,
				estComplete);
	}
	
	public void startJobWithPercentProg(final String user, final String jobID,
			final String service, final String status,
			final String description, final Date estComplete)
			throws CommunicationException, NoSuchJobException {
		startJob(user, jobID, service, status, description, PROG_PERC, null,
				estComplete);
	}
	
	private void startJob(final String user, final String jobID,
			final String service, final String status,
			final String description, final String progType,
			final Integer maxProg, final Date estComplete)
			throws CommunicationException, NoSuchJobException {
		checkString(user, "user", MAX_LEN_USER);
		final ObjectId oi = checkJobID(jobID);
		//this is coming from an auth token so doesn't need much checking
		//although if this is every really used as a lib (unlikely) will need better QA
		checkString(service, "service", MAX_LEN_SERVICE);
		checkMaxLen(status, "status", MAX_LEN_STATUS);
		checkMaxLen(description, "description", MAX_LEN_DESC);
		checkEstComplete(estComplete);
		if (maxProg != null && maxProg < 1) {
			throw new IllegalArgumentException(
					"The maximum progress for the job must be > 0"); 
		}
		final DBObject query = new BasicDBObject(USER, user);
		query.put(MONGO_ID, oi);
		query.put(SERVICE, null);
		final DBObject update = new BasicDBObject(SERVICE, service);
		update.put(STATUS, status);
		update.put(DESCRIPTION, description);
		update.put(PROG_TYPE, progType);
		final Date now = new Date();
		update.put(STARTED, now);
		update.put(UPDATED, now);
		update.put(EST_COMP, estComplete);
		update.put(COMPLETE, false);
		update.put(ERROR, false);
		update.put(ERROR_MSG, null);
		update.put(RESULT, null);
		
		final Integer prog;
		final Integer maxprog;
		if (progType == PROG_TASK) {
			prog = 0;
			maxprog = maxProg;
		} else if (progType == PROG_PERC) {
			prog = 0;
			maxprog = 100;
		} else if (progType == PROG_NONE) {
			prog = 0;
			maxprog = null;
		} else {
			throw new IllegalArgumentException("Illegal progress type: " +
					progType);
		}
		update.put(PROG, prog);
		update.put(MAXPROG, maxprog);
		
		final WriteResult wr;
		try {
			wr = jobcol.update(query, new BasicDBObject("$set", update));
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
		if (wr.getN() != 1) {
			throw new NoSuchJobException(String.format(
					"There is no unstarted job %s for user %s", jobID, user));
		}
	}
	
	private void checkEstComplete(final Date estComplete) {
		if (estComplete == null) {
			return;
		}
		if (estComplete.compareTo(new Date()) < 1) {
			throw new IllegalArgumentException(
					"The estimated completion date must be in the future");
		}
	}
	
	public String createAndStartJob(final String user, final String service,
			final String status, final String description,
			final Date estComplete)
			throws CommunicationException {
		return createAndStartJob(user, service, status, description, PROG_NONE,
				null, estComplete);
	}
	
	public String createAndStartJob(final String user, final String service,
			final String status, final String description, final int maxProg,
			final Date estComplete)
			throws CommunicationException {
		return createAndStartJob(user, service, status, description, PROG_TASK,
				maxProg, estComplete);
	}
	
	public String createAndStartJobWithPercentProg(final String user,
			final String service, final String status,
			final String description, final Date estComplete)
			throws CommunicationException {
		return createAndStartJob(user, service, status, description, PROG_PERC,
				null, estComplete);
	}
	
	private String createAndStartJob(final String user, final String service,
			final String status, final String description,
			final String progType, final Integer maxProg,
			final Date estComplete)
			throws CommunicationException {
		final String jobid = createJob(user);
		try {
			startJob(user, jobid, service, status, description, progType,
					maxProg, estComplete);
		} catch (NoSuchJobException nsje) {
			throw new RuntimeException(
					"Just created a job and it's already deleted", nsje);
		}
		return jobid;
	}
	
	//TODO NOW note that owner can always write and read job, regardless of auth strategy
	//TODO NOW better explanation of auth strategy in header
	
	public void updateJob(final String user, final String jobID,
			final String service, final String status, final Integer progress,
			final Date estComplete)
			throws CommunicationException, NoSuchJobException {
		checkMaxLen(status, "status", MAX_LEN_STATUS);
		final DBObject query = buildStartedJobQuery(user, jobID, service);
		final DBObject set = new BasicDBObject(STATUS, status);
		set.put(UPDATED, new Date());
		if (estComplete != null) {
			checkEstComplete(estComplete);
			set.put(EST_COMP, estComplete);
		}
		final DBObject update = new BasicDBObject("$set", set);
		if (progress != null) {
			if (progress < 0) {
				throw new IllegalArgumentException(
						"progress cannot be negative");
			}
			update.put("$inc", new BasicDBObject(PROG, progress));
		}
		
		final WriteResult wr;
		try {
			wr = jobcol.update(query, update);
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
		if (wr.getN() != 1) {
			throw new NoSuchJobException(String.format(
					"There is no uncompleted job %s for user %s started by service %s",
					jobID, user, service));
		}
	}
	
	
	public void completeJob(final String user, final String jobID,
			final String service, final String status, final String error,
			final JobResults results)
			throws CommunicationException, NoSuchJobException {
		checkMaxLen(status, "status", MAX_LEN_STATUS);
		checkMaxLen(error, "error", MAX_LEN_ERR);
		final DBObject query = buildStartedJobQuery(user, jobID, service);
		final DBObject set = new BasicDBObject(UPDATED, new Date());
		set.put(COMPLETE, true);
		set.put(ERROR, error != null);
		set.put(ERROR_MSG, error);
		set.put(STATUS, status);
		//if anyone is stupid enough to store 16mb of results will need to
		//check size first, or at least catch error and report.
		set.put(RESULT, resultsToDBObject(results));
		
		final WriteResult wr;
		try {
			wr = jobcol.update(query, new BasicDBObject("$set", set));
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
		if (wr.getN() != 1) {
			throw new NoSuchJobException(String.format(
					"There is no uncompleted job %s for user %s started by service %s",
					jobID, user, service));
		}
	}
	
	/* DO NOT change this to use Jongo. This enforces the continuing use of
	 *  the same field names, which is needed for backwards compatibility.
	 */
	private static DBObject resultsToDBObject(final JobResults res) {
		if (res == null) {
			return null;
		}
		final DBObject ret = new BasicDBObject();
		ret.put("shocknodes", res.getShocknodes());
		ret.put("shockurl", res.getShockurl());
		ret.put("workspaceids", res.getWorkspaceids());
		ret.put("workspaceurl", res.getWorkspaceurl());
		if (res.getResults() != null) {
			final List<DBObject> results = new LinkedList<DBObject>();
			ret.put("results", results);
			for (final JobResult jr: res.getResults()) {
				final DBObject oneres = new BasicDBObject();
				results.add(oneres);
				oneres.put("servtype", jr.getServtype());
				oneres.put("url", jr.getUrl());
				oneres.put("id", jr.getId());
				oneres.put("desc", jr.getDesc());
			}
		}
		return ret;
	}

	private DBObject buildStartedJobQuery(final String user, final String jobID,
			final String service) {
		checkString(user, "user", MAX_LEN_USER);
		final ObjectId id = checkJobID(jobID);
		checkString(service, "service", MAX_LEN_SERVICE);
		final DBObject query = new BasicDBObject(USER, user);
		query.put(MONGO_ID, id);
		query.put(SERVICE, service);
		query.put(COMPLETE, false);
		return query;
	}
	
	public void deleteJob(final String user, final String jobID)
			throws NoSuchJobException, CommunicationException {
		deleteJob(user, jobID, null);
	}
	
	public void deleteJob(final String user, final String jobID,
			final String service)
			throws NoSuchJobException, CommunicationException {
		checkString(user, "user", MAX_LEN_USER);
		final ObjectId id = checkJobID(jobID);
		final DBObject query = new BasicDBObject(USER, user);
		query.put(MONGO_ID, id);
		if (service == null) {
			query.put(COMPLETE, true);
		} else {
			query.put(SERVICE, service);
		}
		final WriteResult wr;
		try {
			wr = jobcol.remove(query);
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
		if (wr.getN() != 1) {
			throw new NoSuchJobException(String.format(
					"There is no %sjob %s for user %s",
					service == null ? "completed " : "", jobID, user +
					(service == null ? "" : " and service " + service)));
		}
	}
	
	public Set<String> listServices(final String user)
			throws CommunicationException {
		checkString(user, "user");
		final DBObject query = new BasicDBObject("$or", Arrays.asList(
				new BasicDBObject(USER, user),
				new BasicDBObject(SHARED, user)));
		query.put(SERVICE, new BasicDBObject("$ne", null));
		final Set<String> services = new HashSet<String>();
		try {
			@SuppressWarnings("unchecked")
			final List<String> servs = jobcol.distinct(SERVICE, query);
			services.addAll(servs);
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
		return services;
	}
	
	public List<Job> listJobs(final String user, final List<String> services,
			final boolean queued, final boolean running,
			final boolean complete, final boolean error, final boolean shared)
			throws CommunicationException {
		//queued is ignored
		checkString(user, "user");
		String query;
		if (shared) {
			query = String.format("{$or: [{%s: '%s'}, {%s: '%s'}]",
					USER, user, SHARED, user);
		} else {
			query = String.format("{%s: '%s'", USER, user);
		}
		if (services != null && !services.isEmpty()) {
			for (final String s: services) {
				checkString(s, "service", MAX_LEN_SERVICE);
			}
			query += String.format(", %s: {$in: ['%s']}", SERVICE,
					StringUtils.join(services, "', '"));
		} else {
			query += String.format(", %s: {$ne: null}", SERVICE);
		}
		//this seems dumb.
		if (running && !complete && !error) {
			query += ", " + COMPLETE + ": false}";
		} else if (!running && complete && !error) {
			query += ", " + COMPLETE + ": true, " + ERROR + ": false}";
		} else if (!running && !complete && error) {
			query += ", " + ERROR + ": true}";
		} else if (running && complete && !error) {
			query += ", " + ERROR + ": false}";
		} else if (!running && complete && error) {
			query += ", " + COMPLETE + ": true}";
		} else if (running && !complete && error) {
			query += ", $or: [{" + COMPLETE + ": false}, {" + ERROR + ": true}]}";
		} else {
			query += "}";
		}
		final List<Job> jobs = new LinkedList<Job>();
		try {
			final Iterable<Job> j  = jobjong.find(query).as(Job.class);
			for (final Job job: j) {
				jobs.add(job);
			}
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
		return jobs;
	}
	
	//TODO NOW shareJob, unshareJob, getJobOwner should throw exception or do nothing for non-default auth jobs
	
	//note sharing with an already shared user or sharing with the owner has
	//no effect
	public void shareJob(final String owner, final String jobID,
			final List<String> users)
			throws CommunicationException, NoSuchJobException {
		final ObjectId id = checkShareParams(owner, jobID, users, "owner");
		final List<String> us = new LinkedList<String>();
		for (final String u: users) {
			if (u != owner) {
				us.add(u);
			}
		}
		//TODO NOW add default auth to query
		final WriteResult wr;
		try {
			wr = jobjong.update(QRY_FIND_JOB_BY_OWNER, id, owner)
					.with("{$addToSet: {" + SHARED + ": {$each: #}}}", us);
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
		if (wr.getN() != 1) {
			throw new NoSuchJobException(String.format(
					"There is no job %s owned by user %s", jobID, owner));
		}
	}

	private ObjectId checkShareParams(final String user, final String jobID,
			final List<String> users, final String userType) {
		checkString(user, userType);
		if (users == null) {
			throw new IllegalArgumentException("The users list cannot be null");
		}
		if (users.isEmpty()) {
			throw new IllegalArgumentException("The users list is empty");
		}
		for (final String u: users) {
			checkString(u, "user");
		}
		final ObjectId id = checkJobID(jobID);
		return id;
	}
	
	//removing the owner or an unshared user has no effect
	public void unshareJob(final String user, final String jobID,
			final List<String> users) throws CommunicationException,
			NoSuchJobException {
		final NoSuchJobException e = new NoSuchJobException(String.format(
				"There is no job %s visible to user %s", jobID, user));
		final ObjectId id = checkShareParams(user, jobID, users, "user");
		final Job j;
		try {
			j= getJob(null, id);
		} catch (NoSuchJobException nsje) {
			throw e;
		}
		//TODO NOW check default auth here
		if (j.getUser().equals(user)) {
			//it's the owner, can do whatever
		} else if (j.getShared().contains(user)) {
			if (!users.equals(Arrays.asList(user))) {
				throw new IllegalArgumentException(String.format(
						"User %s may only stop sharing job %s for themselves",
						user, jobID));
			}
			//shared user removing themselves, no prob
		} else {
			throw e;
		}
		try {
			jobjong.update(QRY_FIND_JOB_BY_OWNER, id, j.getUser())
					.with("{$pullAll: {" + SHARED + ": #}}", users);
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
	}
}
