package npu.agents.clustering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import npu.agents.utils.Point;
import rescuecore2.standard.entities.Road;
import rescuecore2.worldmodel.EntityID;

public class Cluster {
	private Point centerPoint;
	private List<Point> members = new ArrayList<Point>();
	private Set<EntityID> roadsAroundCluster;
	private EntityID agentID;
	private Road closestRoadAroundCenter;
	private Map<EntityID,Set<EntityID>> roadAroundRefuge = new HashMap<EntityID,Set<EntityID>>();
	public Road getClosestRoadAroundCenter() {
		return closestRoadAroundCenter;
	}
	public void setClosestRoadAroundCenter(Road closestRoadAroundCenter) {
		this.closestRoadAroundCenter = closestRoadAroundCenter;
	}
	public EntityID getAgentID() {
		return agentID;
	}
	public void setAgentID(EntityID id) {
		this.agentID = id;
	}
	public Cluster(Point centerPoint) {
		this.centerPoint = centerPoint;
	}
	public void addMember(Point member) {
		if(!members.contains(member)&& member.getId()!=null)
			members.add(member);
	}
	public void setRoadsAroundCluster(Set<EntityID> roadsAroundCluster) {
		this.roadsAroundCluster = roadsAroundCluster;
	}
	public void setRoadAroudRefuge(EntityID refugeID,Set<EntityID> roadsID) {
		this.roadAroundRefuge.put(refugeID, roadsID);
	}
	public Point getCenterPoint() {
		return centerPoint;
	}
	public void setCenterPoint(Point centerPoint) {
		this.centerPoint = centerPoint;
	}
	public Set<EntityID> getRoadsAroundCluster() {
		return roadsAroundCluster;
	}
	public Map<EntityID,Set<EntityID>> getRoadARoundRefuge() {
		return roadAroundRefuge;
	}
	public List<Point> getMembers() {
		return members;
	}
	public List<EntityID> getMembersID() {
		List<Point> points = this.getMembers();
		List<EntityID> ids = new ArrayList<EntityID>();
		for(Point  point : points) {
			ids.add(point.getId());
		}
		return ids;
	}
}
