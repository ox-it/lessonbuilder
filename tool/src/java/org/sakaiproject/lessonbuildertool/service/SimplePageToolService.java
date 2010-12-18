/**********************************************************************************
 * $URL: $
 * $Id: $
 ***********************************************************************************
 *
 * Author: Eric Jeney, jeney@rutgers.edu
 *
 * Copyright (c) 2010 Rutgers, the State University of New Jersey
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

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.cover.FunctionManager;
import org.sakaiproject.db.api.SqlService;

import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Currently, the sole purpose of this service is to register our edit permission, and create table
 * indices.
 * 
 * @author Maarten van Hoof
 * 
 */
public class SimplePageToolService implements ResourceLoaderAware {
	private static Log log = LogFactory.getLog(SimplePageToolService.class);

	SqlService sqlService = null;

        public static String PERMISSION_LESSONBUILDER_UPDATE = "lessonbuilder.upd";
        public static String PERMISSION_LESSONBUILDER_READ = "lessonbuilder.read";
        public static String PERMISSION_LESSONBUILDER_PREFIX = "lessonbuilder.";

	private ResourceLoader resourceLoader;
 
	public void setResourceLoader(ResourceLoader resourceLoader) {
	    this.resourceLoader = resourceLoader;
	}
 
	public Resource getResource(String location){
	    return resourceLoader.getResource(location);
	}

	public SimplePageToolService() {}

	public void init() {

		log.info("Initializing Lesson Builder Tool");

		// for debugging I'd like to be able to reload, so avoid duplicates
		List <String> registered = FunctionManager.getRegisteredFunctions(PERMISSION_LESSONBUILDER_PREFIX);
		if (registered == null || !registered.contains(PERMISSION_LESSONBUILDER_UPDATE))
		    FunctionManager.registerFunction(PERMISSION_LESSONBUILDER_UPDATE);
		if (registered == null || !registered.contains(PERMISSION_LESSONBUILDER_READ))
		    FunctionManager.registerFunction(PERMISSION_LESSONBUILDER_READ);

		sqlService = org.sakaiproject.db.cover.SqlService.getInstance();

		try {
			// hibernate will do the tables, but we need this for the indices
			sqlService.ddl(this.getClass().getClassLoader(), "simplepage");
			log.info("Completed Lesson Builder DDL");
		} catch (Exception e) {
			log.warn("Unable to DDL Lesson Builder", e);
		}

	}

}
