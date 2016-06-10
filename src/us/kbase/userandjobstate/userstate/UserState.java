package us.kbase.userandjobstate.userstate;

import static us.kbase.common.utils.StringUtils.checkString;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.mongo.exceptions.MongoAuthException;
import us.kbase.userandjobstate.exceptions.CommunicationException;
import us.kbase.userandjobstate.userstate.exceptions.NoSuchKeyException;

public class UserState {
	
	private final static int MAX_LEN_VALUE = 1000000;
	private final static int MAX_LEN_USER = 100;
	private final static int MAX_LEN_SERVICE = 100;
	private final static int MAX_LEN_KEY = 100;
	
	private final static String SERVICE = "service";
	private final static String USER = "user";
	private final static String KEY = "key";
	private final static String AUTH = "auth";
	private final static String VALUE = "value";
	
	private final static String IDX_UNIQ = "unique";
	
	private final DBCollection uscol;
	
	private final static ObjectMapper MAPPER = new ObjectMapper();
	private final static Pattern INVALID_SERV_NAMES = 
			Pattern.compile("[^\\w]");
	
	public UserState(final String host, final String database,
			final String collection, final int mongoReconnectRetry)
			throws UnknownHostException, IOException, InvalidHostException,
			InterruptedException {
		final DB m = GetMongoDB.getDB(host, database, mongoReconnectRetry, 10);
		uscol = m.getCollection(collection);
		ensureIndexes();
	}

	public UserState(final String host, final String database,
			final String collection, final String user, final String password,
			final int mongoReconnectRetry)
			throws UnknownHostException, IOException, InvalidHostException,
			MongoAuthException, InterruptedException {
		final DB m = GetMongoDB.getDB(host, database, user, password,
				mongoReconnectRetry, 10);
		uscol = m.getCollection(collection);
		ensureIndexes();
	}

	private void ensureIndexes() {
		final DBObject idx = new BasicDBObject();
		idx.put(USER, 1);
		idx.put(SERVICE, 1);
		idx.put(AUTH, 1);
		idx.put(KEY, 1);
		final DBObject unique = new BasicDBObject();
		unique.put(IDX_UNIQ, 1);
		uscol.createIndex(idx, unique);
	}

	private static final String VAL_ERR = String.format(
			"Value cannot be > %s bytes when serialized", MAX_LEN_VALUE);
	
	public void setState(final String user, final String service,
			final boolean auth, final String key, final Object value)
			throws CommunicationException {
		if (value != null) {
			final String valueStr;
			try {
				valueStr = MAPPER.writeValueAsString(value);
			} catch (JsonProcessingException jpe) {
				throw new IllegalArgumentException(
						"Unable to serialize value", jpe);
			}
			//strictly speaking, should convert to bytes as UTF-8, but hardly worth the trouble
			if (valueStr.length() > MAX_LEN_VALUE) {
				throw new IllegalArgumentException(VAL_ERR);
			}
		}
		final DBObject query = generateQuery(user, service, auth, key);
		final DBObject set = new BasicDBObject();
		final DBObject val = new BasicDBObject();
		val.put(VALUE, value);
		set.put("$set", val);
		try {
			uscol.update(query, set, true, false);
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
	}

	private DBObject generateQuery(final String user, final String service,
			final boolean auth, final String key) {
		checkString(user, "user", MAX_LEN_USER);
		checkServiceName(service);
		checkString(key, "key", MAX_LEN_KEY);
		final DBObject query = new BasicDBObject();
		query.put(USER, user);
		query.put(SERVICE, service);
		query.put(AUTH, auth);
		query.put(KEY, key);
		return query;
	}
	
	private static void checkServiceName(final String name) {
		checkString(name, "service", MAX_LEN_SERVICE);
		final Matcher m = INVALID_SERV_NAMES.matcher(name);
		if (m.find()) {
			throw new IllegalArgumentException(String.format(
					"Illegal character in service name %s: %s", name, m.group()));
		}
	}

	public Object getState(final String user, final String service, 
			final boolean auth, final String key)
			throws CommunicationException, NoSuchKeyException {
		return getState(user, service, auth, key, true).getValue();
	}
	
	public KeyState getState(final String user, final String service, 
			final boolean auth, final String key, final boolean exceptOnNoKey)
			throws CommunicationException, NoSuchKeyException {
		final DBObject query = generateQuery(user, service, auth, key);
		final DBObject projection = new BasicDBObject(VALUE, 1);
		final DBObject mret;
		try {
			mret = uscol.findOne(query, projection);
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
		if (mret == null) {
			if (exceptOnNoKey) {
				throw new NoSuchKeyException(String.format(
						"There is no key %s for the %sauthorized service %s",
						key, auth ? "" : "un", service));
			} else {
				return new KeyState(false, null);
			}
		}
		//might make sense to run through this and switch all DBObjects to 
		//Maps, but doesn't really matter for the application, so pass
		return new KeyState(true, mret.get(VALUE));
	}
	
	public static class KeyState {
		private final Object value;
		private final boolean exists;
		
		private KeyState(final boolean exists, final Object value) {
			super();
			this.exists = exists;
			this.value = value;
		}

		public Object getValue() {
			return value;
		}

		public boolean exists() {
			return exists;
		}
	}
	
	public boolean hasState(final String user, final String service, 
			final boolean auth, final String key)
			throws CommunicationException, NoSuchKeyException {
		final DBObject query = generateQuery(user, service, auth, key);
		final long count;
		try {
			count = uscol.count(query);
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
		return count != 0;
	}

	public void removeState(final String user, final String service, 
			final boolean auth, final String key)
			throws CommunicationException {
		final DBObject query = generateQuery(user, service, auth, key);
		try {
			uscol.remove(query);
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
	}

	public Set<String> listState(final String user, final String service,
			final boolean auth)
			throws CommunicationException, NoSuchKeyException {
		checkString(user, "user");
		checkServiceName(service);
		final DBObject query = new BasicDBObject();
		query.put(USER, user);
		query.put(SERVICE, service);
		query.put(AUTH, auth);
		final DBObject projection = new BasicDBObject();
		projection.put(KEY, 1);
		final Set<String> keys = new HashSet<String>();
		try {
			final DBCursor mret = uscol.find(query, projection);
			for (DBObject o: mret) {
				keys.add((String) o.get(KEY));
			}
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
		return keys;
	}
	
	public Set<String> listServices(final String user, final boolean auth)
			throws CommunicationException {
		checkString(user, "user");
		final DBObject mfields = new BasicDBObject(USER, user);
		mfields.put(AUTH, auth);
		final Set<String> services = new HashSet<String>();
		try {
			@SuppressWarnings("unchecked")
			final List<String> servs = uscol.distinct(SERVICE, mfields);
			services.addAll(servs);
		} catch (MongoException me) {
			throw new CommunicationException(
					"There was a problem communicating with the database", me);
		}
		return services;
	}
}
