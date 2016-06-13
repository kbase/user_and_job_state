package us.kbase.userandjobstate.jobstate;

import java.util.Date;
import java.util.List;

public interface Job {

	public static final String CREATED = "created";
	public static final String STARTED = "started";
	public static final String COMPLETE = "complete";
	public static final String ERROR = "error";
	public static final String DELETED = "deleted";
	
	public static final String DEFAULT_AUTH_STRAT = "DEFAULT";
	public static final String DEFAULT_AUTH_PARAM = "DEFAULT";
	
	public String getID();

	public String getStage();

	public String getUser();

	public String getService();

	public String getDescription();

	public String getProgType();

	public Integer getProgress();

	public Integer getMaxProgress();

	public String getStatus();

	public Date getStarted();

	public Date getEstimatedCompletion();

	public Date getLastUpdated();

	public Boolean isComplete();

	public Boolean hasError();

	public String getErrorMsg();

	public JobResults getResults();

	public List<String> getShared();
	
	public String getAuthorizationStrategy();
	
	public String getAuthorizationParameter();

	@Override
	public String toString();

}