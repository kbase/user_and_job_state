package us.kbase.userandjobstate.jobstate;

public class JobResult {
	
	private String servtype = null;
	private String url = null;
	private String id = null;
	private String desc = null;
	
	@SuppressWarnings("unused")
	private JobResult() {} //for jongo
	
	public JobResult(
			final String servtype,
			final String url,
			final String id,
			final String desc) {
		super();
		if (servtype == null || url == null || id == null) {
			throw new NullPointerException(
					"The servtype, url, and id fields are required for a job result");
		}
		//this class may be used to represent external job runner results
		// so no string checking here
		this.servtype = servtype;
		this.url = url;
		this.id = id;
		this.desc = desc;
	}

	public String getServtype() {
		return servtype;
	}

	public String getUrl() {
		return url;
	}

	public String getId() {
		return id;
	}

	public String getDesc() {
		return desc;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((desc == null) ? 0 : desc.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result
				+ ((servtype == null) ? 0 : servtype.hashCode());
		result = prime * result + ((url == null) ? 0 : url.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		JobResult other = (JobResult) obj;
		if (desc == null) {
			if (other.desc != null)
				return false;
		} else if (!desc.equals(other.desc))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (servtype == null) {
			if (other.servtype != null)
				return false;
		} else if (!servtype.equals(other.servtype))
			return false;
		if (url == null) {
			if (other.url != null)
				return false;
		} else if (!url.equals(other.url))
			return false;
		return true;
	}
}
