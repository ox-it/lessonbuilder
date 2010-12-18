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

package org.sakaiproject.lessonbuildertool.tool.producers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.sakaiproject.assignment.api.Assignment;
import org.sakaiproject.assignment.cover.AssignmentService;
import org.sakaiproject.lessonbuildertool.SimplePage;
import org.sakaiproject.lessonbuildertool.SimplePageItem;
import org.sakaiproject.lessonbuildertool.tool.beans.SimplePageBean;
import org.sakaiproject.lessonbuildertool.tool.view.GeneralViewParameters;
import org.sakaiproject.lessonbuildertool.model.SimplePageToolDao;

import uk.org.ponder.messageutil.MessageLocator;
import uk.org.ponder.rsf.components.UIBranchContainer;
import uk.org.ponder.rsf.components.UICommand;
import uk.org.ponder.rsf.components.UIContainer;
import uk.org.ponder.rsf.components.UIForm;
import uk.org.ponder.rsf.components.UILink;
import uk.org.ponder.rsf.components.UIOutput;
import uk.org.ponder.rsf.components.UIInput;
import uk.org.ponder.rsf.components.UISelect;
import uk.org.ponder.rsf.components.UISelectChoice;
import uk.org.ponder.rsf.flow.jsfnav.NavigationCase;
import uk.org.ponder.rsf.flow.jsfnav.NavigationCaseReporter;
import uk.org.ponder.rsf.view.ComponentChecker;
import uk.org.ponder.rsf.view.ViewComponentProducer;
import uk.org.ponder.rsf.viewstate.SimpleViewParameters;
import uk.org.ponder.rsf.viewstate.ViewParameters;
import uk.org.ponder.rsf.viewstate.ViewParamsReporter;

/**
 * Creates a window for the user to choose which assignment to add to the page.
 * 
 * @author Eric Jeney <jeney@rutgers.edu>
 * 
 */
public class AssignmentPickerProducer implements ViewComponentProducer, NavigationCaseReporter, ViewParamsReporter {
	public static final String VIEW_ID = "AssignmentPicker";

	private SimplePageBean simplePageBean;
	private SimplePageToolDao simplePageToolDao;
	public MessageLocator messageLocator;

	public void setSimplePageBean(SimplePageBean simplePageBean) {
		this.simplePageBean = simplePageBean;
	}

	public void setSimplePageToolDao(Object dao) {
		simplePageToolDao = (SimplePageToolDao) dao;
	}

	public String getViewID() {
		return VIEW_ID;
	}

	public void fillComponents(UIContainer tofill, ViewParameters viewparams, ComponentChecker checker) {
		if (((GeneralViewParameters) viewparams).getSendingPage() != -1) {
		    // will fail if page not in this site
		    // security then depends upon making sure that we only deal with this page
		    try {
			simplePageBean.updatePageObject(((GeneralViewParameters) viewparams).getSendingPage());
		    } catch (Exception e) {
			System.out.println("AssignmentPicker permission exception " + e);
			return;
		    }
		}

		Long itemId = ((GeneralViewParameters) viewparams).getItemId();

		simplePageBean.setItemId(itemId);

		if (simplePageBean.canEditPage()) {
		        
			SimplePage page = simplePageBean.getCurrentPage();

			String assignId = null; // default, normally current

			// if itemid is null, we'll append to current page, so it's ok
			if (itemId != null && itemId != -1) {
			    SimplePageItem currentItem = simplePageToolDao.findItem(itemId);
			    if (currentItem == null)
				return;
			    // trying to hack on item not on this page
			    if (currentItem.getPageId() != page.getPageId())
				return;
			    assignId = currentItem.getSakaiId();
			}
			
			Iterator i = AssignmentService.getAssignmentsForContext(page.getSiteId());
			UIForm form = UIForm.make(tofill, "assignment-picker");

			ArrayList<Assignment> assignments = new ArrayList<Assignment>();
			ArrayList<String> values = new ArrayList<String>();
			while (i.hasNext()) {
				Assignment a = (Assignment) i.next();
				values.add(a.getId());
				assignments.add(a);
			}

			if (values.size() < 1) {
			    UIOutput.make(tofill, "error", messageLocator.getMessage("simplepage.no_assignments"));
			    return;
			}

			// if no current item, use first
			if (assignId == null)
			    assignId = values.get(0);

			UISelect select = UISelect.make(form, "assignment-span", values.toArray(new String[1]), "#{simplePageBean.selectedAssignment}", assignId);
			for (Assignment a : assignments) {

				UIBranchContainer row = UIBranchContainer.make(form, "assignment:", String.valueOf(assignments.indexOf(a)));

				UISelectChoice.make(row, "select", select.getFullID(), assignments.indexOf(a));
				UILink.make(row, "link", a.getTitle(), "/direct/assignment/" + a.getId());
				UIOutput.make(row, "due", a.getDueTimeString());

			}

			UIInput.make(form, "item-id", "#{simplePageBean.itemId}");

			UICommand.make(form, "submit", messageLocator.getMessage("simplepage.chooser.select"), "#{simplePageBean.addAssignment}");
			UICommand.make(form, "cancel", messageLocator.getMessage("simplepage.cancel"), "#{simplePageBean.cancel}");
		}
	}

	public ViewParameters getViewParameters() {
		return new GeneralViewParameters();
	}

	public List reportNavigationCases() {
		List<NavigationCase> togo = new ArrayList<NavigationCase>();
		togo.add(new NavigationCase("success", new SimpleViewParameters(ShowPageProducer.VIEW_ID)));
		togo.add(new NavigationCase("failure", new SimpleViewParameters(AssignmentPickerProducer.VIEW_ID)));
		togo.add(new NavigationCase("cancel", new SimpleViewParameters(ShowPageProducer.VIEW_ID)));
		return togo;
	}
}
