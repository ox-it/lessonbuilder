package org.sakaiproject.lessonbuildertool.tool.beans;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Collection;

import org.sakaiproject.lessonbuildertool.SimplePageComment;
import org.sakaiproject.lessonbuildertool.SimplePageItem;
import org.sakaiproject.lessonbuildertool.SimpleStudentPage;
import org.sakaiproject.lessonbuildertool.model.SimplePageToolDao;
import org.sakaiproject.lessonbuildertool.service.GradebookIfc;
import org.sakaiproject.authz.cover.AuthzGroupService;

public class GradingBean {
	public String id;
	public String points;
	public String jsId;
	public String type;
	
	private SimplePageToolDao simplePageToolDao;
	private GradebookIfc gradebookIfc;
	private SimplePageBean simplePageBean;
	
	public void setSimplePageToolDao(SimplePageToolDao simplePageToolDao) {
		this.simplePageToolDao = simplePageToolDao;
	}
	
	public void setGradebookIfc(GradebookIfc gradebookIfc) {
		this.gradebookIfc = gradebookIfc;
	}
	
	public void setSimplePageBean(SimplePageBean simplePageBean) {
		this.simplePageBean = simplePageBean;
	}
	
	public String[] getResults() {
	    System.out.println("called getresults");
		if(simplePageBean.getEditPrivs() != 0) {
			return new String[]{"failure", jsId, "-1"};
		}
		
		// Make sure they gave us a valid amount of points.
		try {
			Double.valueOf(points);
		}catch(Exception ex) {
			return new String[]{"failure", jsId, "-1"};
		}
		
		boolean r = false;
		
		if("comment".equals(type)) {
			System.out.println(id);
			SimplePageComment comment = simplePageToolDao.findCommentByUUID(id);
			SimplePageItem commentItem = simplePageToolDao.findItem(comment.getItemId());
			if(Double.valueOf(points).equals(comment.getPoints())) {
				return new String[] {"success", jsId, String.valueOf(comment.getPoints())};
			}
			
			try {
				r = gradebookIfc.updateExternalAssessmentScore(simplePageBean.getCurrentSiteId(), commentItem.getGradebookId(), comment.getAuthor(), Double.toString(Double.valueOf(points)));
			}catch(Exception ex) {
				ex.printStackTrace();
			}
			
			if(r) {
				List<SimplePageComment> comments;
				if(commentItem.getPageId() > 0) {
					comments = simplePageToolDao.findCommentsOnItemByAuthor(comment.getItemId(), comment.getAuthor());
				}else {
					SimpleStudentPage studentPage = simplePageToolDao.findStudentPage(Long.valueOf(commentItem.getSakaiId()));
					List<SimpleStudentPage> studentPages = simplePageToolDao.findStudentPages(studentPage.getItemId());
					
					List<Long> commentsItemIds = new ArrayList<Long>();
					for(SimpleStudentPage p : studentPages) {
						commentsItemIds.add(p.getCommentsSection());
					}
					
					comments = simplePageToolDao.findCommentsOnItemsByAuthor(commentsItemIds, comment.getAuthor());
				}
				
				// Make sure all of the comments by this person have the grade.
				for(SimplePageComment c : comments) {
					c.setPoints(Double.valueOf(points));
					simplePageBean.update(c, false);
				}
			}
		}else if("student".equals(type)) {
			SimpleStudentPage page = simplePageToolDao.findStudentPage(Long.valueOf(id));
			SimplePageItem pageItem = simplePageToolDao.findItem(page.getItemId());
			// the idea was to not update if there's no change in points
			// but there can be reasons to want to force grades back to the gradebook,
			// particually for group pages where the group may have changed
			//if(Double.valueOf(points).equals(page.getPoints())) {
			//  return new String[] {"success", jsId, String.valueOf(page.getPoints())};
		        //}
			
			try {
			    String owner = page.getOwner();
			    String group = page.getGroup();
			    if (group == null)
				r = gradebookIfc.updateExternalAssessmentScore(simplePageBean.getCurrentSiteId(), pageItem.getGradebookId(), page.getOwner(), Double.toString(Double.valueOf(points)));
			    else {
				HashSet<String>groups = new HashSet<String>();
				if (group != null)
				    group = "/site/" + simplePageBean.getCurrentSiteId() + "/group/" + group;
				groups.add(group);
                                Collection<String>users = AuthzGroupService.getAuthzUsersInGroups(groups);
				// if we have more than one user, in theory some might fail and some succeed. For the
				// moment just update the grade 
				r = true;
                                for (String u: users)
                                    gradebookIfc.updateExternalAssessmentScore(simplePageBean.getCurrentSiteId(), pageItem.getGradebookId(),
									       u, Double.toString(Double.valueOf(points)));
				
			    }
			}catch(Exception ex) {
			    System.out.println("Exception updating grade " + ex);
			}
			if(r) {
				page.setPoints(Double.valueOf(points));
				simplePageBean.update(page, false);
			}
		}
		
		if(r) {
			return new String[] {"success", jsId, String.valueOf(Double.valueOf(points))};
		}else {
			return new String[]{"failure", jsId, "-1"};
		}
	}
}
