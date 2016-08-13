package us.kbase.userandjobstate;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import us.kbase.common.mongo.GetMongoDB;

public class AppEventListener implements ServletContextListener {
	
	@Override
	public void contextInitialized(ServletContextEvent arg0) {
		// do nothing
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		GetMongoDB.closeAllConnections();
	}
}