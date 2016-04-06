package npu.agents.communication.model;

import rescuecore2.worldmodel.EntityID;

public class BuildingStatus {
	private EntityID buidingID;
	private int fieryness;
	private int temperature;
	public BuildingStatus(EntityID id,int fieryness,int temperature) {
		this.buidingID = id;
		this.fieryness = fieryness;
		this.temperature = temperature;
	}
	@Override
	public String toString() {
		return "" + buidingID.getValue() + "," + fieryness + "" + temperature;
	}
	
}
