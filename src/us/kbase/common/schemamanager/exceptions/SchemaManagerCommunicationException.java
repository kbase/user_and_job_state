package us.kbase.common.schemamanager.exceptions;

/** 
 * Thrown when communication to the mongo database fails.
 * @author gaprice@lbl.gov
 *
 */
public class SchemaManagerCommunicationException extends SchemaException {

	private static final long serialVersionUID = 1L;
	
	public SchemaManagerCommunicationException() { super(); }
	public SchemaManagerCommunicationException(String message) { super(message); }
	public SchemaManagerCommunicationException(String message, Throwable cause) { super(message, cause); }
	public SchemaManagerCommunicationException(Throwable cause) { super(cause); }
}
