package npu.agents.clustering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import npu.agents.utils.Point;
import rescuecore2.standard.entities.Road;
import rescuecore2.worldmodel.EntityID;

public class Cluster {
	private Point centerPoint;
	private Set<Point> members = new HashSet<Point>();
	private Set<EntityID> roadsInCluster = new HashSet<EntityID>();
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
		if(member.getId()!=null)
			members.add(member);
	}
	public void setRoadsInCluster(EntityID roadID) {
		roadsInCluster.add(roadID);
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
	public Set<EntityID> getRoadsInCluster() {
		return roadsInCluster;
	}
	public Map<EntityID,Set<EntityID>> getRoadARoundRefuge() {
		return roadAroundRefuge;
	}
	public Set<Point> getMembers() {
		return members;
	}
	public Set<EntityID> getBuildingsIDs() {
		List<Point> points = new ArrayList<Point>(getMembers());
		Set<EntityID> ids = new HashSet<EntityID>();
		for(Point  point : points) {
			ids.add(point.getId());
		}
		return ids;
	}
}
