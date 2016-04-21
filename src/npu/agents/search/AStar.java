package npu.agents.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import npu.agents.model.Point;
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
	public List<EntityID>  getPath(EntityID startID,EntityID destID,Set<Road> blockadeRoads) {
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
				 if((entity instanceof Building))
					 continue;
				 else if(entity instanceof Road ){
					 if(blockadeRoads != null && blockadeRoads.contains(entity))
						 continue;
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
	 public List<EntityID> breadthFirstSearch(EntityID start, EntityID... goals) {
	        return breadthFirstSearch(start, Arrays.asList(goals));
	    }

	    /**
	       Do a breadth first search from one location to the closest (in terms of number of nodes) of a set of goals.
	       @param start The location we start at.
	       @param goals The set of possible goals.
	       @return The path from start to one of the goals, or null if no path can be found.
	    */
	    public List<EntityID> breadthFirstSearch(EntityID start, List<EntityID> goals) {
	    	if(start == null || goals.size() == 0 || goals.contains(start))
				return null;
	        List<EntityID> open = new LinkedList<EntityID>();
	        Map<EntityID, EntityID> ancestors = new HashMap<EntityID, EntityID>();
	        open.add(start);
	        EntityID next = null;
	        boolean found = false;
	        ancestors.put(start, start);
	        do {
	            next = open.remove(0);
	            if (isGoal(next, goals)) {
	                found = true;
	                break;
	            }
	            Collection<EntityID> neighbours = graph.get(next);
	            if (neighbours.isEmpty()) {
	                continue;
	            }
	            for (EntityID neighbour : neighbours) {
	                if (isGoal(neighbour, goals)) {
	                    ancestors.put(neighbour, next);
	                    next = neighbour;
	                    found = true;
	                    break;
	                }
	                else {
	                    if (!ancestors.containsKey(neighbour)) {
	                        open.add(neighbour);
	                        ancestors.put(neighbour, next);
	                    }
	                }
	            }
	        } while (!found && !open.isEmpty());
	        if (!found) {
	            // No path
	            return null;
	        }
	        // Walk back from goal to start
	        EntityID current = next;
	        List<EntityID> path = new LinkedList<EntityID>();
	        do {
	            path.add(0, current);
	            current = ancestors.get(current);
	            if (current == null) {
	                throw new RuntimeException("Found a node with no ancestor! Something is broken.");
	            }
	        } while (current != start);
	        return path;
	    }

	    private boolean isGoal(EntityID e, Collection<EntityID> test) {
	        return test.contains(e);
	    }
}
