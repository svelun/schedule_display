/*
 * Copyright 2017 Enea Software AB
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 * 
 */

package enea.jenkins.scheduledisplay;

import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import hudson.model.Queue;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.Queue.Item;
import hudson.model.Queue.WaitingItem;
import hudson.plugins.view.dashboard.DashboardPortlet;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import enea.jenkins.scheduledisplay.FutureBuild.DescriptorImpl;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;

/**
 * @author svlu
 *
 */
@ExportedBean(defaultVisibility = 2)
public class SchedulePortlet extends DashboardPortlet {

	/**
	 * @param name
	 */
	@DataBoundConstructor
	public SchedulePortlet(String name) {
		super(name);
	}
	@SuppressWarnings("unchecked")
	@Exported(name = "schedule_portlet")

	public List<FutureBuild> getPlannedBuilds() {
		List<FutureBuild> futureBuilds = new Vector<FutureBuild>();

		List<ParameterizedJobMixIn.ParameterizedJob> projects;
		
		View view = Stapler.getCurrentRequest().findAncestorObject(View.class);
		DescriptorImpl descriptor = (DescriptorImpl)(Jenkins.getInstance().getDescriptorOrDie(FutureBuild.class));
		
		if(descriptor.getFilterCurrentView() && view != null)
		{
			Collection<TopLevelItem> viewItems = view.getItems();
			projects = new Vector<ParameterizedJobMixIn.ParameterizedJob>();
			for (TopLevelItem topLevelItem : viewItems) {
				if(topLevelItem instanceof ParameterizedJobMixIn.ParameterizedJob) {
					projects.add((ParameterizedJobMixIn.ParameterizedJob)topLevelItem);
				}
			}
		}
		else {
			projects = Jenkins.getInstance().getItems(ParameterizedJobMixIn.ParameterizedJob.class); 
		}
		
		long nowMs = new Date().getTime();
		long limitMs = nowMs + descriptor.getMaxDaysInt() * 24L * 60L * 60L * 1000L; // Set time limit on now + number of days

		for (ParameterizedJobMixIn.ParameterizedJob project: projects) {
			int count = descriptor.getMaxCountInt(); // Max number of future jobs to include
			FutureBuild fb = FutureBuild.getFutureBuild(project, nowMs);
			while(fb != null && count-- > 0 && fb.getCalandar().getTimeInMillis() < limitMs) {
				futureBuilds.add(fb);
				long nextTime = fb.getCalandar().getTimeInMillis() + 60000;
				fb = FutureBuild.getFutureBuild(project, nextTime);
			}
		}
		
		if (this.getClass() == SchedulePortlet.class) {
			Item[] queueItems = Queue.getInstance().getItems();
			for (Item item : queueItems) {
				if(item instanceof WaitingItem && item.task instanceof ParameterizedJobMixIn.ParameterizedJob) {
					WaitingItem waitingItem = (WaitingItem)item;
					Calendar now = Calendar.getInstance();
					long nowMilliseconds = now.getTimeInMillis();
					now.setTimeInMillis(nowMilliseconds + 60 * 1000);
					if(waitingItem.timestamp.after(now)) {
						FutureBuild nb = new FutureBuild((ParameterizedJobMixIn.ParameterizedJob)item.task,
								waitingItem.timestamp, waitingItem.getParams());
						futureBuilds.add(nb);
					}
			
				}
			}
		}

		Collections.sort(futureBuilds);
		int pos = 0;
		for(FutureBuild build: futureBuilds) {
			build.calculateBgColour(pos);
			pos++;
		}
		return futureBuilds;
		
	}
}
