package us.kbase.userandjobstate.common;

public interface SchemaUpgrader {

	public void upgrade();
	
	public int getDBVersion();
	
	public int getCodebaseVersion();
	
	public String getSchemaType();
	
}
