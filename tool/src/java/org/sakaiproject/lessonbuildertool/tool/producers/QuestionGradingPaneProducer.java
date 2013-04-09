package org.sakaiproject.lessonbuildertool.tool.producers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.sakaiproject.lessonbuildertool.SimplePage;
import org.sakaiproject.lessonbuildertool.SimplePageItem;
import org.sakaiproject.lessonbuildertool.SimplePageQuestionAnswer;
import org.sakaiproject.lessonbuildertool.SimplePageQuestionResponse;
import org.sakaiproject.lessonbuildertool.model.SimplePageToolDao;
import org.sakaiproject.lessonbuildertool.tool.beans.SimplePageBean;
import org.sakaiproject.lessonbuildertool.tool.view.GeneralViewParameters;
import org.sakaiproject.lessonbuildertool.tool.view.QuestionGradingPaneViewParameters;
import org.sakaiproject.user.cover.UserDirectoryService;

import uk.org.ponder.localeutil.LocaleGetter;
import uk.org.ponder.messageutil.MessageLocator;
import uk.org.ponder.rsf.builtin.UVBProducer;
import uk.org.ponder.rsf.components.UIBranchContainer;
import uk.org.ponder.rsf.components.UIContainer;
import uk.org.ponder.rsf.components.UIForm;
import uk.org.ponder.rsf.components.UIInitBlock;
import uk.org.ponder.rsf.components.UIInput;
import uk.org.ponder.rsf.components.UIInternalLink;
import uk.org.ponder.rsf.components.UIOutput;
import uk.org.ponder.rsf.components.decorators.UIFreeAttributeDecorator;
import uk.org.ponder.rsf.view.ComponentChecker;
import uk.org.ponder.rsf.view.ViewComponentProducer;
import uk.org.ponder.rsf.viewstate.SimpleViewParameters;
import uk.org.ponder.rsf.viewstate.ViewParameters;
import uk.org.ponder.rsf.viewstate.ViewParamsReporter;


public class QuestionGradingPaneProducer implements ViewComponentProducer, ViewParamsReporter {
	public static final String VIEW_ID = "QuestionGradingPane";

	private SimplePageBean simplePageBean;
	private SimplePageToolDao simplePageToolDao;
	private MessageLocator messageLocator;
	public LocaleGetter localeGetter;                                                                                             
	
	public String getViewID() {
		return VIEW_ID;
	}
	
	public void setSimplePageBean(SimplePageBean simplePageBean) {
		this.simplePageBean = simplePageBean;
	}
	
	public void setSimplePageToolDao(SimplePageToolDao simplePageToolDao) {
		this.simplePageToolDao = simplePageToolDao;
	}
	
	public void setMessageLocator(MessageLocator messageLocator) {
		this.messageLocator = messageLocator;
	}
	
	private class SimpleUser implements Comparable<SimpleUser> {
		public String displayName;
		public String userId;
		public Double grade;
		public SimplePageQuestionResponse response = null;
		
		public int compareTo(SimpleUser user) {
			return displayName.compareTo(user.displayName);
		}
	}

	public void fillComponents(UIContainer tofill, ViewParameters viewparams, ComponentChecker checker) {
		QuestionGradingPaneViewParameters params = (QuestionGradingPaneViewParameters) viewparams;
		
		SimplePage currentPage = simplePageToolDao.getPage(params.pageId);
		simplePageBean.setCurrentSiteId(params.siteId);
		simplePageBean.setCurrentPage(currentPage);
		simplePageBean.setCurrentPageId(params.pageId);

		GeneralViewParameters backParams = new GeneralViewParameters(ShowPageProducer.VIEW_ID, params.pageId);
		backParams.setItemId(params.pageItemId);
		backParams.setPath("log");
		
		UIOutput.make(tofill, "html").decorate(new UIFreeAttributeDecorator("lang", localeGetter.get().getLanguage()))
			.decorate(new UIFreeAttributeDecorator("xml:lang", localeGetter.get().getLanguage()));        

		UIInternalLink.make(tofill, "back-link", messageLocator.getMessage("simplepage.go-back"), backParams);
		
		if(simplePageBean.getEditPrivs() != 0) {
			UIOutput.make(tofill, "permissionsError");
			return;
		}
		
		SimplePageItem questionItem = simplePageToolDao.findItem(params.questionItemId);
		SimplePage containingPage = simplePageToolDao.getPage(questionItem.getPageId());
		String heading = messageLocator.getMessage("simplepage.question-grading").replace("{}", questionItem.getAttribute("questionText"));
		
		UIOutput.make(tofill, "page-header", heading);
		
		List<SimplePageQuestionResponse> responses = simplePageToolDao.findQuestionResponses(questionItem.getId());
		
		ArrayList<String> userIds = new ArrayList<String>();
		HashMap<String, SimpleUser> users = new HashMap<String, SimpleUser>();
		
		for(SimplePageQuestionResponse response : responses) {			
			if(!userIds.contains(response.getUserId())) {
				userIds.add(response.getUserId());
				try {
					SimpleUser user = new SimpleUser();
					user.displayName = UserDirectoryService.getUser(response.getUserId()).getDisplayName();
					user.userId = response.getUserId();
					user.grade = response.getPoints();
					user.response = response;
					
					users.put(response.getUserId(), user);
				}catch(Exception ex) {}
			}
		}
		
		ArrayList<SimpleUser> simpleUsers = new ArrayList<SimpleUser>(users.values());
		Collections.sort(simpleUsers);
		
		if(simpleUsers.size() > 0) {
			UIOutput.make(tofill, "gradingTable");
		}else {
			UIOutput.make(tofill, "noEntriesWarning");
		}

		boolean graded = "true".equals(questionItem.getAttribute("questionGraded")) || questionItem.getGradebookId() != null;
		
		if (graded) {
		    UIOutput.make(tofill, "clickToSubmit", messageLocator.getMessage("simplepage.update-points")).
			    decorate(new UIFreeAttributeDecorator("title", 
								  messageLocator.getMessage("simplepage.update-points")));
		    UIOutput.make(tofill, "grade-header",  messageLocator.getMessage("simplepage.grading-grade"));
		}
		
		String pointsText = messageLocator.getMessage("simplepage.question-points");
		for(SimpleUser user : simpleUsers) {
			UIBranchContainer branch = UIBranchContainer.make(tofill, "student-row:");
			
			UIOutput.make(branch, "data-row");
			
			UIOutput.make(branch, "student-name", user.displayName);
			if("multipleChoice".equals(questionItem.getAttribute("questionType"))) {
				UIOutput.make(branch, "student-response", user.response.getOriginalText());
			}else {
				UIOutput.make(branch, "student-response", user.response.getShortanswer());
			}
			
			// The grading stuff
			UIOutput.make(branch, "student-grade");
			UIOutput.make(branch, "gradingSpan");
			UIOutput.make(branch, "responseId", String.valueOf(user.response.getId()));
			if (graded) {
			    UIOutput.make(branch, "points-text", pointsText);
			    UIOutput.make(branch, "responsePoints",
					  (user.grade == null? "" : String.valueOf(user.grade)));
			    UIOutput.make(branch, "pointsBox").
				decorate(new UIFreeAttributeDecorator("title", 
								      messageLocator.getMessage("simplepage.grade-for-student").replace("{}", user.displayName)));
			    UIOutput.make(branch, "maxpoints", " / " + (questionItem.getGradebookPoints()));
			}
		}
		
		UIForm gradingForm = UIForm.make(tofill, "gradingForm");
		gradingForm.viewparams = new SimpleViewParameters(UVBProducer.VIEW_ID);
		UIInput idInput = UIInput.make(gradingForm, "gradingForm-id", "gradingBean.id");
		UIInput jsIdInput = UIInput.make(gradingForm, "gradingForm-jsId", "gradingBean.jsId");
		UIInput pointsInput = UIInput.make(gradingForm, "gradingForm-points", "gradingBean.points");
		UIInput typeInput = UIInput.make(gradingForm, "gradingForm-type", "gradingBean.type");
		UIInitBlock.make(tofill, "gradingForm-init", "initGradingForm", new Object[] {idInput, pointsInput, jsIdInput, typeInput, "gradingBean.results"});
	}
	
	public ViewParameters getViewParameters() {
		return new QuestionGradingPaneViewParameters();
	}

}
