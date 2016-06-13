package us.kbase.common.schemamanager.exceptions;

/** 
 * Parent class of all schema exceptions.
 * @author gaprice@lbl.gov
 *
 */
public class SchemaException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public SchemaException() { super(); }
	public SchemaException(String message) { super(message); }
	public SchemaException(String message, Throwable cause) { super(message, cause); }
	public SchemaException(Throwable cause) { super(cause); }
}
