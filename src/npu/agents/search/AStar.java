package npu.agents.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import npu.agents.utils.Point;
import rescuecore2.misc.collections.LazyMap;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardWorldModel;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;

public class AStar {
    private Map<EntityID, Set<EntityID>> graph;
    private StandardWorldModel world;
    /**
       Construct a new AStar.
       @param world The world model to construct the neighbourhood graph from.
    */
    public AStar(StandardWorldModel world) {
        Map<EntityID, Set<EntityID>> neighbours = new LazyMap<EntityID, Set<EntityID>>() {
            @Override
            public Set<EntityID> createValue() {
                return new HashSet<EntityID>();
            }
        };
        for (Entity next : world) {
            if (next instanceof Area) {
                Collection<EntityID> areaNeighbours = ((Area)next).getNeighbours();
                neighbours.get(next.getID()).addAll(areaNeighbours);
            }
        }
        setGraph(neighbours);
        this.world = world;
    }
    public void setGraph(Map<EntityID, Set<EntityID>> newGraph) {
        this.graph = newGraph;
    }
	public Map<EntityID, Set<EntityID>> getGraph() {
		return graph;
	}
	public List<EntityID>  getPath(EntityID startID,EntityID destID) {
		if(startID == null || destID == null || startID.equals(destID))
			return null;
		StandardEntity startEntity = world.getEntity(startID);
		StandardEntity destEntity = world.getEntity(destID);
		Point start = new Point(((Area)startEntity).getX(),((Area)startEntity).getY());
		start.setId(startID);
		Point dest = new Point(((Area)destEntity).getX(),((Area)destEntity).getY());
		dest.setId(destID);
		PriorityQueue<Point> open = new PriorityQueue<Point>();
		LinkedList<Point> close = new LinkedList<Point>();
		start.setCostG(0);
		start.setCostH(start.getDistance(dest));
		start.setCostF(start.getDistance(dest));
		open.offer(start);
		while(!open.isEmpty()) {
			Point current = open.poll();
			if(current.getX() == dest.getX() && current.getY() == dest.getY()) {
				List<EntityID> temp = new ArrayList<EntityID>();
				while(current.getFather()!=null) {
					temp.add(current.getId());
					current = current.getFather();
				}
				temp.add(current.getId());
			    List<EntityID> path = new LinkedList<EntityID>();
			    for(int i = temp.size() - 1;i >= 0;i--) {
			    	path.add(temp.get(i));
			    }
				return path;
			}
			 Collection<EntityID> neighbours = graph.get(current.getId());
			 for(EntityID id : neighbours) {
				 StandardEntity entity = world.getEntity(id);
				 if(entity instanceof Building)
					 continue;
				 else if(entity instanceof Road){
					 Road r = (Road)entity;
					 Point next = new Point(r.getX(),r.getY());
					 next.setId(r.getID());
					 int costG = current.getCostG() + current.getDistance(next);
					 int costH = next.getDistance(dest); 
					 int costF = costG + costH;
						//下一结点在close表中,不再检查
					 if(close.contains(next)) {
							continue;
					 }else if (!open.contains(next)) {
							//下一结点不在open表和close表，更新估价值和父节点
							next.setFather(current);
							next.setCostF(costF);
							next.setCostG(costG);
							next.setCostH(costH);
							open.offer(next);
					 }else {
							//下一结点在open表，比较当前估计值Ｆ和open表中的估价值,更新估价值和父节点
							Iterator<Point> iter = open.iterator();
							while(iter.hasNext()) {
								Point p = iter.next();
								if(p.equals(next) && costF < p.getCostF()) {
									open.remove(p);
									next.setFather(current);
									next.setCostF(costF);
									next.setCostG(costG);
									next.setCostH(costH);
									open.offer(next);
									break;
								}
							}
						}
				 }
			 }
			close.add(current);
		}
		return null;
	}
}
