package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.DataverseUser;
import edu.harvard.iq.dataverse.DataverseUserServiceBean;
import static edu.harvard.iq.dataverse.api.JsonPrinter.json;
import static edu.harvard.iq.dataverse.api.Util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

/**
 *
 * @author michael
 */
@Path("users")
public class Users {
	private static final Logger logger = Logger.getLogger(Users.class.getName());
	
	@EJB
	DataverseUserServiceBean usersSvc;
	
	
	@GET
	public String list() {
		JsonArrayBuilder bld = Json.createArrayBuilder();
		
		for ( DataverseUser u : usersSvc.findAll() ) {
			bld.add( json(u) );
		}
		
		return ok( bld.build() );
	}
	
	@GET
	@Path("{identifier}")
	public String view( @PathParam("identifier") String identifier ) {
		DataverseUser u = isNumeric(identifier) 
				? usersSvc.find(Long.parseLong(identifier)) 
				: usersSvc.findByUserName(identifier);
		
		return ( u!=null ) 
				? ok( json(u).build() ) 
				: error( "Can't find user with identifier '" + identifier + "'");
	}
	
	@POST
	public String save( DataverseUser user, @QueryParam("password") String password ) {
		try { 
			if ( password != null ) {
				user.setEncryptedPassword(usersSvc.encryptPassword(password));
			}
			user = usersSvc.save(user);
			return ok ( json(user).build() );
		} catch ( Exception e ) {
			logger.log( Level.WARNING, "Error saving user", e );
			return error( "Can't save user: " + e.getMessage() );
		}
	}
}