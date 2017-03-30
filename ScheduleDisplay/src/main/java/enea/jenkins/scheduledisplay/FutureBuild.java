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

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.scheduler.CronTab;
import hudson.scheduler.CronTabList;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import hudson.Util;
import jenkins.model.ParameterizedJobMixIn;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import enea.jenkins.scheduledisplay.Messages;

/**
 * Represents a planned/scheduled build of a Jenkins project.
 */
@ExportedBean(defaultVisibility = 2)
public class FutureBuild implements Comparable, Describable<FutureBuild>{
	private ParameterizedJobMixIn.ParameterizedJob project;
	private String name;
	private Calendar date;
	private String params;
	private String paramsShort;
	private String bgColour = "#FFFFFF";
	
	public FutureBuild(ParameterizedJobMixIn.ParameterizedJob project, Calendar date, String params) {
		this.project = project;
		this.name = Util.escape(project.getDisplayName());
		this.date = date;
		this.params = params;
	}
	
	@Exported
	public String getName() {
		return name;
	}
	
	@Exported
	public String getUrl(){
		return Jenkins.getInstance().getRootUrl() + project.getUrl();
	}
	
	public String getshortName() {
		return (name.length() > 22)? name.substring(0, 19) + "...": name;
	}

	public int compareTo(Object o) {
		if(o instanceof FutureBuild){
			FutureBuild toCompare = (FutureBuild)o;
			return this.date.compareTo(toCompare.date);
			
		}
		else{
			return 0;
		}
	}

	private String formatDate(Date d) {
		String dateFormat = this.getDescriptor().getDateFormat();
		if(dateFormat == null){
			dateFormat = this.getDescriptor().getDateFormatDefault();
		}
		SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
		return sdf.format(d.getTime());
	}
	
	@Exported
	public String getDate() {
		return formatDate(date.getTime());
	}
	
	public Calendar getCalandar() {
		return date;
	}

	@Exported
	public String getAssignedLabel() {
		Label label = project.getAssignedLabel();
		if(label != null) return label.getDisplayName();
		else return "";
	}
	
	public void calculateBgColour(int pos) {
		if(date.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
			bgColour = "#FFB0A0"; // light red/pink
		else if (pos % 2 == 0)
			bgColour = "#FFFFFF"; // White
		else
			bgColour = "#F0F0F0"; // Light grey
	}
	public String getBgColour() {
		return bgColour;
	}
	
	public String getParameters() {
		return params;
	}
	
	public String getParametersShort() {
		if (paramsShort == null) {
			paramsShort = "";
			try {
				int startIdx = 0;
				int equalIdx = params.indexOf('=');
				while (equalIdx > 0) {
					int nextEqualIdx = params.indexOf('=', equalIdx + 1);
					int endIdx = params.lastIndexOf('\n', nextEqualIdx < 0 ? params.length() : nextEqualIdx);
					System.out
							.println("X " + params + " eqIdx: " + equalIdx + " eqIdx2: " + nextEqualIdx + " endIdx: " + endIdx);
					if (endIdx <= equalIdx)
						endIdx = params.length();
					String value = params.substring(equalIdx + 1, endIdx).trim();
					// Remove trailing ] if the name value pair is surrounded with brackets.
					if(params.charAt(startIdx) == '[' && value.endsWith("]")) value = value.substring(0, value.length() - 1);
					paramsShort = paramsShort + "\n" + value;
					equalIdx = nextEqualIdx;
					startIdx = endIdx + 1;
				}
			} catch (IndexOutOfBoundsException e) {
				System.out.println("Oops: " + paramsShort);
				paramsShort = "";
			}
		}
		return paramsShort;
	}
	
	public String getWeekDay() {
		return new SimpleDateFormat("EEEE").format(date.getTime());
	}
	
    public static FutureBuild getFutureBuild(ParameterizedJobMixIn.ParameterizedJob project, long minTime) {
        Calendar cal = null;
        if ((project instanceof AbstractProject && !((AbstractProject) project).isDisabled())
                || !(project instanceof AbstractProject)) {
            Map<TriggerDescriptor, Trigger<?>> triggers = project.getTriggers();
            Iterator<Map.Entry<TriggerDescriptor, Trigger<?>>> iterator = triggers.entrySet().iterator();
            while (iterator.hasNext()) {
                Trigger trigger = iterator.next().getValue();
                if (trigger.getClass().equals(TimerTrigger.class)) {
                    try {
                        Field triggerTabsField = Trigger.class.getDeclaredField("tabs");
                        triggerTabsField.setAccessible(true);

                        CronTabList cronTabList = (CronTabList) triggerTabsField.get(trigger);

                        Field crontablistTabsField = CronTabList.class.getDeclaredField("tabs");
                        crontablistTabsField.setAccessible(true);

                        Collection<CronTab> crons = (Collection<CronTab>) crontablistTabsField.get(cronTabList);

                        for (CronTab cronTab : crons) {
                            //Date d = new Date();
                            cal = (cal == null || cal.compareTo(cronTab.ceil(minTime)) > 0) ? cronTab.ceil(minTime) : cal;
                        }
                    } catch (NoSuchFieldException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if (cal != null) {
            return new FutureBuild(project, cal, "<default parameters>");
        } else {
            return null;
        }
    }

	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl)Jenkins.getInstance().getDescriptorOrDie(getClass());
	}
	
	@Extension
	public static class DescriptorImpl extends Descriptor<FutureBuild>  {
		private String dateFormat;
		private Boolean filterCurrentView;
		private String maxCount = "100";
		private String maxDays = "7";

		public DescriptorImpl() {
			load();
		}
		
		@Override
		public String getDisplayName() {
			return "Schedule ";
		}
		
		public String getDateFormat() {
			return dateFormat;
		}

		public String getDateFormatDefault() {
			return "yyyy-MM-dd HH:mm";
		}
		
		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws hudson.model.Descriptor.FormException {
			dateFormat = json.getString("dateFormat");
			filterCurrentView = json.getBoolean("filterCurrentView");
			maxCount = json.getString("maxCount");
			maxDays = json.getString("maxDays");
			save();
			return true;
		}
		
		public FormValidation doCheckDateFormat(@QueryParameter String value) {
			try{
				new SimpleDateFormat(value);
				return FormValidation.ok();
			}
			catch (IllegalArgumentException e) {
				return FormValidation.error(Messages.Format_Error());
			}
		}
		public boolean getFilterCurrentView() {
			if (filterCurrentView == null)
				return getFilterCurrentViewDefault();
			return filterCurrentView;
		}

		public boolean getFilterCurrentViewDefault() {
			return true;
		}

		public String getMaxCount() {
			try {
				int maxCountInt = Integer.parseInt(maxCount);
				if (maxCountInt < 1)
					return getMaxCountDefault();
				return maxCount;
			} catch (NumberFormatException e) {
				return getMaxCountDefault();
			}
		}

		public String getMaxCountDefault() {
			return "100";
		}

		public int getMaxCountInt() {
			try {
				return Integer.parseInt(getMaxCount());
			} catch (NumberFormatException e) {
				return 100;
			}
		}

		public FormValidation doCheckMaxCount(@QueryParameter String value) {
			try {
				int count = Integer.parseInt(value);
				if (count < 1 || count > 1000000)
					return FormValidation
							.warning("Maximum count of scheduled builds are out of range ( 1 <= count < 1000000)");
				else
					return FormValidation.ok();
			} catch (NumberFormatException e) {
				return FormValidation.error("Not a valid number");
			}
		}

		public String getMaxDays() {
			try {
				int maxDaysInt = Integer.parseInt(maxDays);
				if (maxDaysInt < 1)
					return getMaxDaysDefault();
				return maxDays;
			} catch (NumberFormatException e) {
				return getMaxDaysDefault();
			}
		}

		public String getMaxDaysDefault() {
			return "7"; // Default is a week
		}

		public int getMaxDaysInt() {
			try {
				return Integer.parseInt(getMaxDays());
			} catch (NumberFormatException e) {
				return 7;
			}
		}

		public FormValidation doCheckMaxDays(@QueryParameter String value) {
			try {
				int days = Integer.parseInt(value);
				if (days < 1 || days > 366)
					return FormValidation.warning("Number of days are out of range ( 1 <= days < 367)");
				else
					return FormValidation.ok();
			} catch (NumberFormatException e) {
				return FormValidation.error("Not a valid number");
			}
		}
	}
}
