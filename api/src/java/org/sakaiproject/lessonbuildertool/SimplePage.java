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

package org.sakaiproject.lessonbuildertool;

import java.util.Date;

public interface SimplePage {

    public static String PERMISSION_LESSONBUILDER_UPDATE = "lessonbuilder.upd";
    public static String PERMISSION_LESSONBUILDER_READ = "lessonbuilder.read";
    public static String PERMISSION_LESSONBUILDER_PREFIX = "lessonbuilder.";

    public long getPageId();

    public void setPageId(long p);

    public String getToolId();

    public void setToolId(String toolId);

    public String getSiteId();

    public void setSiteId(String i);

    public String getTitle();

    public void setTitle(String t);

    public Long getParent();

    public void setParent(Long l);

    public Long getTopParent();

    public void setTopParent(Long l);

    public boolean isHidden();

    public void setHidden(boolean hidden);

    public Date getReleaseDate();

    public void setReleaseDate(Date releaseDate);

}
