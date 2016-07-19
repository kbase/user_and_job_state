package us.kbase.userandjobstate.jobstate;

import static us.kbase.userandjobstate.jobstate.JobState.PROG_NONE;
import static us.kbase.userandjobstate.jobstate.JobState.META_KEY;
import static us.kbase.userandjobstate.jobstate.JobState.META_VALUE;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;

import us.kbase.userandjobstate.authorization.AuthorizationStrategy;

public class Job {
	
	public static final String CREATED = "created";
	public static final String STARTED = "started";
	public static final String COMPLETE = "complete";
	public static final String ERROR = "error";
	public static final String CANCELED = "canceled";
	
	private ObjectId _id;
	private String user;
	private String service;
	private String desc;
	private String progtype;
	private Integer prog;
	private Integer maxprog;
	private String status;
	private Date started;
	private Date updated;
	private Date estcompl;
	private Boolean complete;
	private Boolean error;
	private String canceledby;
	private String errormsg;
	private JobResults results;
	private List<String> shared;
	private String authstrat;
	private String authparam;
	private List<Map<String, String>> meta;
	
	private Job() {}

	public String getID() {
		return _id.toString();
	}
	
	public String getStage() {
		if (canceledby != null) {
			return CANCELED;
		}
		if (service == null) {
			return CREATED;
		}
		if (!complete) {
			return STARTED;
		}
		if (!error) {
			return COMPLETE;
		}
		return ERROR;
	}
	
	public String getUser() {
		return user;
	}

	public String getService() {
		return service;
	}

	public String getDescription() {
		return desc;
	}

	public String getProgType() {
		return progtype;
	}

	public Integer getProgress() {
		if (getProgType() == null || getProgType().equals(PROG_NONE)) {
			return null;
		}
		if (isComplete() || getMaxProgress() < prog) {
			return getMaxProgress();
		}
		return prog;
	}

	public Integer getMaxProgress() {
		if (getProgType() == null || getProgType().equals(PROG_NONE)) {
			return null;
		}
		return maxprog;
	}

	public String getStatus() {
		return status;
	}

	public Date getStarted() {
		return started;
	}
	
	public Date getEstimatedCompletion() {
		return estcompl;
	}
	
	public Date getLastUpdated() {
		return updated;
	}

	public Boolean isComplete() {
		return complete;
	}
	
	public boolean isCanceled() {
		return canceledby != null;
	}
	
	public String getCanceledBy() {
		return canceledby;
	}

	public Boolean hasError() {
		return error;
	}
	
	public String getErrorMsg() {
		return errormsg;
	}

	public JobResults getResults() {
		return results;
	}
	
	public List<String> getShared() {
		if (shared == null) {
			return new LinkedList<String>();
		}
		return new LinkedList<String>(shared);
	}

	public AuthorizationStrategy getAuthorizationStrategy() {
		return new AuthorizationStrategy(authstrat);
	}

	public String getAuthorizationParameter() {
		return authparam;
	}
	
	public Map<String, String> getMetadata() {
		final Map<String, String> ret = new HashMap<String, String>();
		for (final Map<String, String> o: meta) {
			ret.put((String) o.get(META_KEY),
					(String) o.get(META_VALUE));
		}
		return ret;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("UJSJob [_id=");
		builder.append(_id);
		builder.append(", user=");
		builder.append(user);
		builder.append(", service=");
		builder.append(service);
		builder.append(", desc=");
		builder.append(desc);
		builder.append(", progtype=");
		builder.append(progtype);
		builder.append(", prog=");
		builder.append(prog);
		builder.append(", maxprog=");
		builder.append(maxprog);
		builder.append(", status=");
		builder.append(status);
		builder.append(", started=");
		builder.append(started);
		builder.append(", updated=");
		builder.append(updated);
		builder.append(", estcompl=");
		builder.append(estcompl);
		builder.append(", complete=");
		builder.append(complete);
		builder.append(", error=");
		builder.append(error);
		builder.append(", errormsg=");
		builder.append(errormsg);
		builder.append(", results=");
		builder.append(results);
		builder.append(", shared=");
		builder.append(shared);
		builder.append(", authstrat=");
		builder.append(authstrat);
		builder.append(", authparam=");
		builder.append(authparam);
		builder.append("]");
		return builder.toString();
	}
}
