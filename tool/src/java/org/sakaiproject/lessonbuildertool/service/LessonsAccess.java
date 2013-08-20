/**********************************************************************************
 * $URL: https://newtools.oirt.rutgers.edu:8443/repos/sakai2.x/sakai/trunk/assignment/assignment-impl/impl/src/java/org/sakaiproject/assignment/impl/AssignmentGradeInfoProvider.java $
 * $Id: AssignmentGradeInfoProvider.java 4492 2013-03-22 15:02:00Z willkara $
 ***********************************************************************************
 *
 * Copyright (c) 2011 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.lessonbuildertool.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.service.gradebook.shared.ExternalAssignmentProvider;
import org.sakaiproject.service.gradebook.shared.GradebookExternalAssessmentService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.db.cover.SqlService;
import org.sakaiproject.db.api.SqlReader;

import org.sakaiproject.memory.api.Cache;
import org.sakaiproject.memory.api.CacheRefresher;
import org.sakaiproject.memory.api.MemoryService;

import org.sakaiproject.lessonbuildertool.SimplePageItem;
import org.sakaiproject.lessonbuildertool.SimplePage;
import org.sakaiproject.lessonbuildertool.model.SimplePageToolDao;
import org.sakaiproject.lessonbuildertool.SimpleStudentPage;

// This class is used to check whether a page or item should be
// accessible for the user. It checks all conditions. SimplePageBean
// has individual checks for them, but this puts them all together,
// for use in services where individual checks aren't needed.
// In general this will be used by services such as /access or /direct

public class LessonsAccess {

    private Log log = LogFactory.getLog(LessonsAccess.class);

    // caching
    private static Cache cache = null;
    // currently using 10 sec. The real goal is to prevent continual
    // reevaluation of items as we follow different paths. I.e. we mostly
    // care about it during a single transaction. But I'm using the normal
    // default of 10 min
    protected static final int DEFAULT_EXPIRATION = 60 * 10;

    // Sakai Service Beans
    private SimplePageToolDao dao;
    private AuthzGroupService authzGroupService;
    private SecurityService securityService;
    private MemoryService memoryService;

    public void init() {
	cache = memoryService
	    .newCache("org.sakaiproject.lessonbuildertool.service.LessonsAccess.cache");
        log.info("init()");
    }

    public void destroy() {
        log.info("destroy()");
	cache.destroy();
	cache = null;
    }

    // my best estimate is about 100 bytes / call. The maximum likely chain is 
    // 100. So that could put 10K on the stack. With 1 G stacks I think that's OK

    /*
      main entry to this is getItemPaths(item, [])

      null means no constraints
      [], i.e. empty set, means impossible
      otherwise return a list of paths to get to it. Only one needs to work
      pageId == null if path has no prerequisites
    */
    

    public class Path implements Cloneable{
	Long itemId;
	Set<String>groups;
	public Path clone() {
	    Path ret = new Path();
	    ret.itemId = this.itemId;
	    ret.groups = this.groups;
	    return ret;
	}
    }

    // the code for access is broken into 3 parts:
    //
    // 1) getPagePaths returns accessibility data that is the same for all users.
    // that means that we can reasonably cache it, and get a good performance improvement.
    //
    // 2) isPageAccessible interprets the results of getPathPaths for a given user.
    //
    // 3) isItemAccessible checks that the containing page is accessible, and then
    // calls the normal available and visible tests from SimplePageBean. In a lot of
    // code those tests will be used directly, so this class will be used only
    // for isPageAccessible. But for the /access and /direct handlers, this is
    // the right way to check items.


    Set<Path> getPagePaths (long pageId) {
	return getPagePaths(pageId, new HashSet<Long>());
    }

    Set<Path> getPagePaths(long pageId, Set<Long>seen) {
	/// System.out.println("page " + pageId + " getgroups");
	// if pageid is 0 this is a top level page. No further constraints
	Set<Path> ret = new HashSet<Path>();
	if (pageId == 0) {
	    Path path = new Path();
	    path.groups = null;
	    path.itemId = null;
	    ret.add(path);
	    return ret;
	}

	// List of items with this page on it
	List<SimplePageItem> items = null;

	// if it's a student page, have to handle specially. Find item that has the student content section
	SimplePage page = dao.getPage(pageId);
	if (page.getOwner() != null) {
	    SimpleStudentPage student = dao.findStudentPage(page.getTopParent());
	    SimplePageItem item = dao.findItem(student.getItemId());
	    items = new ArrayList<SimplePageItem>();
	    items.add(item);
	} else
	    items = dao.findPageItemsBySakaiId(Long.toString(pageId));

	seen.add(pageId);

	for (SimplePageItem item: items) {
	    // union all their groups

	    String itemGroupString = item.getGroups();
	    Set<String>itemGroups = null;
	    if (itemGroupString != null && itemGroupString.length() > 0)
		itemGroups = new HashSet<String>(Arrays.asList(itemGroupString.split(",")));

	    if (item.isPrerequisite()) {
		// we don't do a recursive call for groups. because of the restriction on prerequisites,
		// we will only allow access to this page if there is a log entry. But in that case
		// we don't need further tests because the user had at some point gotten to the containing
		// page. Note that a log entry will be created when the containing page is shown, as long
		// as the page is marked has having prerequisites and they are satisfied. So log entry
		// is a good test for prerequisites, and a lot less work than the real test.
		Path path = new Path();
		path.itemId = item.getId();
		path.groups = itemGroups;
		// TODO: check for duplicates
		ret.add(path);
	    } else {
		Set<Path> paths = getPagePaths(item.getPageId(), seen);
		for (Path path: paths) {
		    // the values will end up cached. That means we can't
		    // modify the values in place, but have to copy them first
		    path = path.clone();
		    if (path.groups == null)
			path.groups = itemGroups;
		    else if (itemGroups == null)
			; // use path.groups as is
		    else {
			// note: we can't modify that set in path.groups, because it coudl be shared
			// with other copies of this Path
			itemGroups.retainAll(path.groups);  // intersect
			path.groups = itemGroups;
		    }
		    ret.add(path);
		}
	    }
	}

	seen.remove(pageId);

	return ret;
    }

    public boolean isPageAccessible(long pageId, String siteId, String currentUserId) {

	Set<Path> paths = getPagePaths(pageId);

	for (Path path: paths) {
	    if (path.itemId != null) {
		// page needs to be marked available. When the containing page
		// finds that is accessible a dummy entry is created. So existence
		// of an entry, dummy or real means it is accessible.

		if (dao.getLogEntry(currentUserId, path.itemId, -1L) == null)
		    continue;

	    }

	    if (path.groups == null) 
		return true;
	    else {
		Set<String>groupIds = path.groups;
		ArrayList<String> groups = new ArrayList<String>();
		for (String groupId: groupIds)
		    groups.add("/site/" + siteId + "/group/" + groupId);
		
		List<AuthzGroup> matched = authzGroupService.getAuthzUserGroupIds(groups, currentUserId);
		if (matched.size() > 0)
		    return true;
	    }

	}

	return false;

    }

    public void setAuthzGroupService(AuthzGroupService authzGroupService) {
        this.authzGroupService = authzGroupService;
    }

    public AuthzGroupService getAuthzGroupService() {
        return authzGroupService;
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public SecurityService getSecurityService() {
        return securityService;
    }

    public void setSimplePageToolDao(SimplePageToolDao s) {
        dao = s;
    }

    public void setMemoryService(MemoryService m) {
	memoryService = m;
    }


}

