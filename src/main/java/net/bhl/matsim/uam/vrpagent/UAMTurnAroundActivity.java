package net.bhl.matsim.uam.vrpagent;

import net.bhl.matsim.uam.schedule.UAMTurnAroundTask;
import org.matsim.contrib.dynagent.DynActivity;

/**
 * An implementation of AbstractDynActivity for UAM DynAgents for the
 * UAMTurnAroundTask.
 *
 * @author balacmi (Milos Balac), RRothfeld (Raoul Rothfeld)
 */
public class UAMTurnAroundActivity implements DynActivity {

	final private UAMTurnAroundTask turnAroundTask;
	private double now;
	private String activityType;

	public UAMTurnAroundActivity(UAMTurnAroundTask turnAroundTask) {
		this.activityType = turnAroundTask.getName();
		this.turnAroundTask = turnAroundTask;
		this.now = turnAroundTask.getBeginTime();
	}

	@Override
	public void doSimStep(double now) {
		this.now = now;
	}

	@Override
	public String getActivityType() {
		return activityType;
	}

	@Override
	public double getEndTime() {
		return turnAroundTask.getEndTime();
	}

}
