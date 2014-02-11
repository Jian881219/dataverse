/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.dataaccess.OptionalAccessService;

import java.lang.reflect.Type;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.io.IOException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import javax.servlet.http.HttpServletResponse;

/*
    Custom API exceptions [NOT YET IMPLEMENTED]
import edu.harvard.iq.dataverse.api.exceptions.NotFoundException;
import edu.harvard.iq.dataverse.api.exceptions.ServiceUnavailableException;
import edu.harvard.iq.dataverse.api.exceptions.PermissionDeniedException;
import edu.harvard.iq.dataverse.api.exceptions.AuthorizationRequiredException;
*/

/**
 *
 * @author Leonid Andreev
 * 
 * The data (file) access API is based on the DVN access API v.1.0 (that came 
 * with the v.3.* of the DVN app) and extended for DVN 4.0 to include some
 * extra fancy functionality, such as subsetting individual columns in tabular
 * data files and more.
 */

@Path("access")
public class Access {
    private static final Logger logger = Logger.getLogger(Meta.class.getCanonicalName());
    
    @EJB
    DataFileServiceBean dataFileService;

    @EJB
    
    @Path("datafile/{fileId}")
    @GET
    @Produces({ "application/xml" })
    public DownloadInstance datafile(@PathParam("fileId") Long fileId, @Context UriInfo uriInfo, @Context HttpHeaders headers, @Context HttpServletResponse response) /*throws NotFoundException, ServiceUnavailableException, PermissionDeniedException, AuthorizationRequiredException*/ {        
        ByteArrayOutputStream outStream = null;
        

        DataFile df = dataFileService.find(fileId);
        /* TODO: 
         * Throw a meaningful exception if file not found!
         * -- L.A. 4.0alpha1
         */
        
        DownloadInfo dInfo = new DownloadInfo(df);

        /*
         * The only "optional access service" supported as of now (4.0alpha1)
         * is image thumbnail generation: 
         * (and yes, this is a hack)
         */
        if (df.getContentType() != null && df.getContentType().startsWith("image/")) {
            dInfo.addServiceAvailable(new OptionalAccessService("thumbnail", "image/png", "imageThumb=true", "Image Thumbnail (64x64)"));
        }

        DownloadInstance downloadInstance = new DownloadInstance(dInfo);
        
        for (String key : uriInfo.getQueryParameters().keySet()) {
            String value = uriInfo.getQueryParameters().getFirst(key);
            
            if (downloadInstance.isDownloadServiceSupported(key, value)) {
                // this automatically sets the conversion parameters in 
                // the download instance to key and value;
                // TODO: I should probably set these explicitly instead. 
                break;
            } else {
                // Service unknown/not supported/bad arguments, etc.:
                // TODO: throw new ServiceUnavailableException(); 
            }
            
        }
        /* 
         * Provide content type header:
         * (this will be done by the InstanceWriter class - ?)
         */
         
        /* Provide "Access-Control-Allow-Origin" header:
         * (may not be needed here... - that header was added specifically
         * to get the data exploration app to be able to access the metadata
         * API; may have been something specific to Vito's installation too
         * -- L.A.)
         */
        response.setHeader("Access-Control-Allow-Origin", "*");
                
        /* 
         * Provide some browser-friendly headers: (?)
         */
        //return retValue; 
        return downloadInstance;
    }
}