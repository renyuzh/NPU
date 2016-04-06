package npu.agents.communication.model;

import rescuecore2.worldmodel.EntityID;

public class HumanStatus {
	private EntityID humanID;
	public HumanStatus(EntityID id,int HP,int temperature) {
		this.humanID = id;
	}
	@Override
	public String toString() {
		return "" + humanID.getValue();
	}
	
}
