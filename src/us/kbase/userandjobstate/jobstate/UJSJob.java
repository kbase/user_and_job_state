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

public class UJSJob implements Job {
	
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
	private String errormsg;
	private JobResults results;
	private List<String> shared;
	private String authstrat;
	private String authparam;
	private List<Map<String, String>> meta;
	
	private UJSJob() {}

	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#getID()
	 */
	@Override
	public String getID() {
		return _id.toString();
	}
	
	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#getStage()
	 */
	@Override
	public String getStage() {
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
	
	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#getUser()
	 */
	@Override
	public String getUser() {
		return user;
	}

	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#getService()
	 */
	@Override
	public String getService() {
		return service;
	}

	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#getDescription()
	 */
	@Override
	public String getDescription() {
		return desc;
	}

	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#getProgType()
	 */
	@Override
	public String getProgType() {
		return progtype;
	}

	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#getProgress()
	 */
	@Override
	public Integer getProgress() {
		if (getProgType() == null || getProgType().equals(PROG_NONE)) {
			return null;
		}
		if (isComplete() || getMaxProgress() < prog) {
			return getMaxProgress();
		}
		return prog;
	}

	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#getMaxProgress()
	 */
	@Override
	public Integer getMaxProgress() {
		if (getProgType() == null || getProgType().equals(PROG_NONE)) {
			return null;
		}
		return maxprog;
	}

	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#getStatus()
	 */
	@Override
	public String getStatus() {
		return status;
	}

	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#getStarted()
	 */
	@Override
	public Date getStarted() {
		return started;
	}
	
	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#getEstimatedCompletion()
	 */
	@Override
	public Date getEstimatedCompletion() {
		return estcompl;
	}
	
	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#getLastUpdated()
	 */
	@Override
	public Date getLastUpdated() {
		return updated;
	}

	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#isComplete()
	 */
	@Override
	public Boolean isComplete() {
		return complete;
	}

	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#hasError()
	 */
	@Override
	public Boolean hasError() {
		return error;
	}
	
	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#getErrorMsg()
	 */
	@Override
	public String getErrorMsg() {
		return errormsg;
	}

	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#getResults()
	 */
	@Override
	public JobResults getResults() {
		return results;
	}
	
	/* (non-Javadoc)
	 * @see us.kbase.userandjobstate.jobstate.Job#getShared()
	 */
	@Override
	public List<String> getShared() {
		if (shared == null) {
			return new LinkedList<String>();
		}
		return new LinkedList<String>(shared);
	}

	@Override
	public String getAuthorizationStrategy() {
		return authstrat;
	}

	@Override
	public String getAuthorizationParameter() {
		return authparam;
	}
	
	@Override
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
