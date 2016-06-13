package us.kbase.common.schemamanager;

public interface SchemaUpgrader {

	public void upgrade();
	
	public int getDBVersion();
	
	public int getCodebaseVersion();
	
	public String getSchemaType();
	
}
