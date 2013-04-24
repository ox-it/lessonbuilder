package org.sakaiproject.lessonbuildertool.ccexport;;

/***********
 *
 * Copyright (c) 2013 Rutgers, the State University of New Jersey
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");                                                                
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;

import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentEntity;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;

import org.sakaiproject.lessonbuildertool.tool.beans.SimplePageBean;
import org.sakaiproject.lessonbuildertool.ccexport.SamigoExport;
import org.sakaiproject.lessonbuildertool.ccexport.ZipPrintStream;
import uk.org.ponder.messageutil.MessageLocator;

public class CCExport {

    private File root;
    private String rootPath;
    long nextid = 1;
  
    SimplePageBean simplePageBean;
    static ContentHostingService contentHostingService;
    public void setContentHostingService(ContentHostingService chs) {
	contentHostingService = chs;
    }
    static SamigoExport samigoExport;
    public void setSamigoExport(SamigoExport se) {
	samigoExport = se;
    }
    private MessageLocator messageLocator;
    public void setMessageLocator(MessageLocator x) {
	messageLocator = x;
    }

    HttpServletResponse response;
    File errFile = null;
    PrintStream errStream = null;

    class Resource {
	String sakaiId;
	String resourceId;
	String location;
    }

    // map of all file resource to be included in cartridge
    Map<String, Resource> fileMap = new HashMap<String, Resource>();
    // map of all Samigo tests
    Map<String, Resource> samigoMap = new HashMap<String, Resource>();


    /*
     * maintain global lists of resources, adding as they are referenced on a page or
     * adding all resources of a kind, depending. Each type of resource has a map
     * indexed by sakai ID, with a generated ID for the cartridge and the name of the
     * file or XML file.
     *
     * the overall flow will be to load all resources and tests into the temp directory
     * and the maps, the walk the lesson hierarchy building imsmanifest.xml. Any resources
     * not used will get some kind of dummy entries in imsmanifest.xml, so that the whole
     * contents of the site is brought over.
     */

    public void doExport(String siteId, HttpServletResponse httpServletResponse, SimplePageBean bean) {
	response = httpServletResponse;
	simplePageBean = bean;

	if (! startExport())
	    return;
	if (! addAllFiles(siteId))
	    return;
	if (! addAllSamigo(siteId))
	    return;
	download();

    }

    /*
     * create temp dir and start writing 
     */
    public boolean startExport() {
	try {
	    root = File.createTempFile("ccexport", "root");
	    if (root.exists())
		root.delete();
	    root.mkdir();
	    errFile = new File(root, "export-errors");
	    errStream = new PrintStream(errFile);
	    
	} catch (Exception e) {
	    simplePageBean.setErrKey("simplepage.exportcc-fileerr", e.getMessage());
	    return false;
	}
	return true;
    }

    String getResourceId () {
       	return "res" + (nextid++);
    }

    public void addFile(String sakaiId, String location) {
	Resource res = new Resource();
	res.sakaiId = sakaiId;
	res.resourceId = getResourceId();
	res.location = location;

	fileMap.put(sakaiId, res);

    }

    public boolean addAllFiles(String siteId) {
	try {
	    String base = contentHostingService.getSiteCollection(siteId);
	    ContentCollection baseCol = contentHostingService.getCollection(base);
	    return addAllFiles(baseCol, base.length());
	} catch (Exception e) {
	    simplePageBean.setErrKey("simplepage.exportcc-fileerr", e.getMessage());
	    return false;
	}

    }

    public boolean addAllFiles(ContentCollection baseCol, int baselen) {
	try {

	    List<ContentEntity> members = baseCol.getMemberResources();
	    for (ContentEntity e: members) {
		if (e instanceof ContentResource)
		    addFile(e.getId(), e.getId().substring(baselen));
		else
		    addAllFiles((ContentCollection)e, baselen);
	    }
	} catch (Exception e) {
	    simplePageBean.setErrKey("simplepage.exportcc-fileerr", e.getMessage());
	    return false;
	}
	return true;
    }

    public boolean outputAllFiles (ZipPrintStream out) {
	try {
	    for (Map.Entry<String, Resource> entry: fileMap.entrySet()) {
		System.out.println(entry.getKey() + " " + entry.getValue().location);

		ZipEntry zipEntry = new ZipEntry(entry.getValue().location);

		ContentResource resource = contentHostingService.getResource(entry.getKey());

		zipEntry.setSize(resource.getContentLength());
		out.putNextEntry(zipEntry);
		InputStream contentStream = null;
		try {
		    contentStream = resource.streamContent();
		    IOUtils.copy(contentStream, out);
		} finally {
		    if (contentStream != null) {
			contentStream.close();
		    }
		}
	    }
	} catch (Exception e) {
	    simplePageBean.setErrKey("simplepage.exportcc-fileerr", e.getMessage());
	    return false;
	}

	return true;

    }

    public boolean addAllSamigo(String siteId) {
	List<String> tests = samigoExport.getEntitiesInSite(siteId);
	if (tests == null)
	    return true;
	for (String sakaiId: tests) {
	    Resource res = new Resource();
	    res.resourceId = getResourceId();
	    res.location = res.resourceId + ".xml";
	    res.sakaiId = sakaiId;
	    System.out.println(res.sakaiId + " " + res.resourceId + " " + res.location);
	    samigoMap.put(res.sakaiId, res);
	}

	return true;
    }

    public boolean outputAllSamigo(ZipPrintStream out) {
	try {
	    for (Map.Entry<String, Resource> entry: samigoMap.entrySet()) {

		ZipEntry zipEntry = new ZipEntry(entry.getValue().location);

		out.putNextEntry(zipEntry);
		boolean ok = samigoExport.outputEntity(entry.getValue().sakaiId, out, errStream);
		if (!ok)
		    return false;

	    }
	} catch (Exception e) {
	    simplePageBean.setErrKey("simplepage.exportcc-fileerr", e.getMessage());
	    return false;
	}

	return true;

    }

    public boolean outputManifest(ZipPrintStream out) {
	try {
	    ZipEntry zipEntry = new ZipEntry("imsmanifest.xml");
	    out.putNextEntry(zipEntry);
	    out.print(
		      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<manifest identifier=\"sakai1\"\n  xmlns=\"http://www.imsglobal.org/xsd/imsccv1p2/imscp_v1p1\"\nxmlns:lom=\"http://ltsc.ieee.org/xsd/imsccv1p2/LOM/resource\"\nxmlns:lomimscc=\"http://ltsc.ieee.org/xsd/imsccv1p2/LOM/manifest\"\nxmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\nxsi:schemaLocation=\"                                                                                                                        \n  http://ltsc.ieee.org/xsd/imsccv1p2/LOM/resource http://www.imsglobal.org/profile/cc/ccv1p2/LOM/ccv1p2_lomresource_v1p0.xsd                  \n  http://www.imsglobal.org/xsd/imsccv1p2/imscp_v1p1 http://www.imsglobal.org/profile/cc/ccv1p2/ccv1p2_imscp_v1p2_v1p0.xsd                     \n  http://ltsc.ieee.org/xsd/imsccv1p2/LOM/manifest http://www.imsglobal.org/profile/cc/ccv1p2/LOM/ccv1p2_lommanifest_v1p0.xsd\">\n  <metadata>\n    <schema>IMS Common Cartridge</schema>\n    <schemaversion>1.2.0</schemaversion>\n    <lomimscc:lom>\n      <lomimscc:general>\n	<lomimscc:title>\n	  <lomimscc:string language=\"en-US\">Sakai Export</lomimscc:string>\n	</lomimscc:title>\n	<lomimscc:description>\n	  <lomimscc:string language=\"en-US\">Sakai Export, including only files from site</lomimscc:string>\n	</lomimscc:description>\n	<lomimscc:keyword>\n	  <lomimscc:string language=\"en-US\">Export</lomimscc:string>\n	</lomimscc:keyword>\n      </lomimscc:general>\n    </lomimscc:lom>\n  </metadata>\n ");

	    out.println("  <organizations>");

	    out.println("  <organization identifier=\"page\" structure=\"rooted-hierarchy\">");
	    out.println("    <item identifier=\"I_1\">");
	    out.println("      <item identifer=\"I_1_1\">");
	    out.println("        <title>Dummy page</title>");
	    int n = 0;
	    for (Map.Entry<String, Resource> entry: samigoMap.entrySet()) {
		out.println("        <item idenitifer=\"I_I_1_" + n + "\" identifierref=\"" + entry.getValue().resourceId + "\">");
		out.println("          <title>test " + n + "</title>");
		out.println("        </item>");
	    }
	    out.println("      </item>");
	    out.println("    </item>");
	    out.println("  </organization>");
	    out.println("  </organizations>");
	    out.println("  <resources>");
	    for (Map.Entry<String, Resource> entry: fileMap.entrySet()) {
		out.print(("    <resource href=\"" + entry.getValue().location + "\" identifier=\"" + entry.getValue().resourceId + 
			   "\" type=\"webcontent\">\n      <file href=\"" + entry.getValue().location + "\"/>\n    </resource>\n"));
	    }

	    for (Map.Entry<String, Resource> entry: samigoMap.entrySet()) {
		out.print(("    <resource href=\"" + entry.getValue().location + "\" identifier=\"" + entry.getValue().resourceId + 
			   "\" type=\"imsqti_xmlv1p2/imscc_xmlv1p2/assessment\">\n      <file href=\"" + entry.getValue().location + "\"/>\n    </resource>\n"));
	    }

	    // add error log at the very end
	    String errId = getResourceId();

	    out.println(("    <resource href=\"export-errors\" identifier=\"" + errId + 
			   "\" type=\"webcontent\">\n      <file href=\"export-errors\"/>\n    </resource>"));
	    
	    out.println("  </resources>\n</manifest>");

	    errStream.close();
	    zipEntry = new ZipEntry("export-error");
	    out.putNextEntry(zipEntry);
	    InputStream contentStream = null;
	    try {
		contentStream = new FileInputStream(errFile);
		IOUtils.copy(contentStream, out);
	    } finally {
		if (contentStream != null) {
		    contentStream.close();
		}
	    }
	} catch (Exception e) {
	    simplePageBean.setErrKey("simplepage.exportcc-fileerr", e.getMessage());
	    return false;
	}

	return true;


    }

    public boolean download() {

        OutputStream htmlOut = null;
	ZipPrintStream out = null;
        try {
	    htmlOut = response.getOutputStream();
	    out = new ZipPrintStream(htmlOut);

	    response.setHeader("Content-disposition", "inline; filename=sakai-export.imscc");
	    response.setContentType("application/zip");
	    
	    outputAllFiles (out);
	    outputAllSamigo (out);
	    outputManifest (out);
	    
	    if (out != null)
		out.close();

        } catch (Exception ioe) {
	    simplePageBean.setErrKey("simplepage.exportcc-fileerr", ioe.getMessage());
	    return false;
	}

	return true;

    }

}
