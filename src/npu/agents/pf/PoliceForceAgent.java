package npu.agents.pf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import npu.agents.clustering.Cluster;
import npu.agents.clustering.ClustingMap;
import npu.agents.common.AbstractCommonAgent;
import npu.agents.communication.model.Message;
import npu.agents.communication.utils.ChangeSetUtil;
import npu.agents.communication.utils.CommUtils.MessageID;
import npu.agents.communication.utils.MessagesSendUtil;
import npu.agents.utils.KConstants;
import npu.agents.utils.Point;
import rescuecore2.messages.Command;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.GasStation;
import rescuecore2.standard.entities.Hydrant;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;

public class PoliceForceAgent extends AbstractCommonAgent<PoliceForce> {
    private int distance;
    private Cluster cluster;
    private Set<EntityID> roadsInMyCluster = new HashSet<EntityID>();
    private Set<EntityID> roadsHasCleared = new HashSet<EntityID>();
    private Set<EntityID> roadsID ;
    private Set<EntityID> refugeHasCleared = new HashSet<EntityID>();
    private Set<EntityID> clearedEntities = new HashSet<EntityID>();
    private Set<EntityID> firingBuildings = new HashSet<EntityID>();
    private Set<EntityID> stuckingHumans = new HashSet<EntityID>();
    private Set<EntityID> nearbyStuckingHumans = new HashSet<EntityID>();
    private Set<EntityID> nearbyfiringBuildings = new HashSet<EntityID>();
    private Set<EntityID> extinguishFires= new HashSet<EntityID>();
    
    private Set<Message> messagesSend = new HashSet<Message>();
    
    private EntityID[] prePosition = {};
    private EntityID nowPosition;
	@Override
	protected void postConnect() {
		super.postConnect();
		try {
			ClustingMap.initMap(KConstants.countOfpf, 100, model);
			cluster = ClustingMap.assignAgentToCluster(me(), model);
		    if(cluster == null){
		    	   System.out.println("该agent没有分配到cluster");
		    	   return;
		    }
		    //将该簇内的道路拷贝一份
		    roadsID = cluster.getRoadsAroundCluster();
		    Iterator<EntityID> iter = roadsID.iterator();
		    while(iter.hasNext()) {
		    	roadsInMyCluster.add(iter.next());
		    }
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("聚类失败");
		}
		distance = configuration.getMaxCleardistance();
	}
	@Override
	protected void think(int time, ChangeSet changes, Collection<Command> heard) {
		  if(time < configuration.getIgnoreAgentCommand())
			  return;
	      if (time == configuration.getIgnoreAgentCommand()) {
	            sendSubscribe(time,configuration.getPoliceChannel());//注册了该通道的都可以接收到
	      }
	      handleHeared(heard);
	      ChangeSetUtil seenChanges = new ChangeSetUtil();
	      seenChanges.handleChanges(model,changes);
	      addFireInfo(seenChanges.getFireBuildings(),time);
	      addInjuredHumanInfo(seenChanges.getInjuredHuman(),time);
	      prePosition[1] = prePosition[0];
	      prePosition[0] = nowPosition;
	      nowPosition = me().getPosition();
	      if(me().getHP() > 0 && (me().getBuriedness() > 0 || me().getDamage() > 0)) {
	    	  handleInjuredMe(time);
	    	  return;
	      }
	      removeClearedRoads();
	      addAllClearedRoadsBack();
	      if(isStucked()) {
	    	  
	      }
	      //Am I near a blockade?
	      clearNearBlockes(time);
	      //Plan a path to a blocked seen area（不在清除范围内,去找路上能看到的有路障的点)
	      boolean successMove = moveToSeenBlockade(time,changes);
	      if(!successMove) {
	    	  //响应求救信息
          	  handleChannelInfo(heard);
          	  //遍历该簇内的道路
          	  boolean traverslOk =traversalRoads(time);
          	  if(!traverslOk) {
          		  sendMove(time, randomWalkAroundCluster(roadsID));
          		System.out.println();     		  
          	  }
	      }
	    }

	   private void handleHeared(Collection<Command> heards) {
	    	for(Command heard: heards) {
	    		if(!(heard instanceof AKSpeak)) 
	    			continue;
	    		AKSpeak  info = (AKSpeak)heard;
	    		String content = new String(info.getContent());

	    		if(content.isEmpty())
	    			continue;
	    		String	typeOfhearedInfo = content.split(",")[0];
	 		   int id = Integer.parseInt(typeOfhearedInfo);
			   MessageID messageID = MessageID.values()[id];
			   EntityID entityID = info.getAgentID();
	    		if(configuration.getRadioChannels().contains(info.getChannel()))
	    			handleRadio(entityID,messageID);
	    		else
	    			handleVoice(entityID,messageID);
	    	}
	   }
	   
	   private void handleVoice(EntityID voice,MessageID messageID) {
		   switch(messageID){
		   case STUCK_HUMAN:	nearbyStuckingHumans.add(voice);
		   break;
		   case BUILDING_ON_FIRE:	nearbyfiringBuildings.add(voice);
		   break;
		   default:break;
		   }
	   }
	   private void handleRadio(EntityID radio,MessageID messageID) {
		   switch(messageID){
		   case STUCK_HUMAN:	stuckingHumans.add(radio);
		   break;
		   case BUILDING_ON_FIRE:	firingBuildings.add(radio);
		   break;
		   default:break;
		   }
	   }
	   
	   public void addFireInfo(Set<EntityID> fireBuildings,int time) {
			for(EntityID buidingID : fireBuildings) {
				Message message = new Message(MessageID.BUILDING_ON_FIRE,buidingID,time,configuration.getFireChannel());
				messagesSend.add(message);
			}
		}

		public void addInjuredHumanInfo(Set<EntityID> injuredHuman,int time) {
			for(EntityID humanID : injuredHuman) {
				Message message = new Message(MessageID.INJURED_HUMAN,humanID,time,configuration.getAmbulanceChannel());
				messagesSend.add(message);
			}		
		}
	  
		public void handleInjuredMe(int time) {
			Message message = new Message(MessageID.PF_INJURED,me().getID(),time,configuration.getAmbulanceChannel());
			messagesSend.add(message);
			sendRest(time);
		}
		public boolean isStucked() {
			if(prePosition[0] == prePosition[1] && prePosition[0] == nowPosition)
				return true;
			else
				return false;
		}
		private void removeClearedRoads() {
		      EntityID pos = me().getPosition();
		      if(roadsInMyCluster.contains(pos)) {
		    	  StandardEntity entity = model.getEntity(pos);
		    		  Road r = (Road)entity;
		    		  if(r.isBlockadesDefined() && r.getBlockades().isEmpty()){
		    			  roadsInMyCluster.remove(pos);
		    			  roadsHasCleared.add(pos);
		    			  System.out.println("removeClearedRoads");
		    	  }
		      }
	    }
	    private void addAllClearedRoadsBack() {
		      if(roadsInMyCluster.size() == 0) {
		    	  Iterator<EntityID> roadsID = roadsHasCleared.iterator();
		    	  while(roadsID.hasNext()){
		    		  roadsInMyCluster.add(roadsID.next());
		    	  }
		    	  roadsHasCleared.clear();
		    	  System.out.println("addAllClearedRoadsBack");
		      }
	    }
	    private void clearNearBlockes(int time) {
		      Blockade target = getTargetBlockade();
		      if (target != null) {
		            sendSpeak(time, configuration.getPoliceChannel(), ("Clearing " + target).getBytes());
		            List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(target.getApexes()), true);
		            double best = Double.MAX_VALUE;
		            Point2D bestPoint = null;
		            Point2D origin = new Point(me().getX(), me().getY());
		            for (Line2D next : lines) {
		                Point2D closest = GeometryTools2D.getClosestPointOnSegment(next, origin);
		                double d = GeometryTools2D.getDistance(origin, closest);
		                if (d < best) {
		                    best = d;
		                    bestPoint = closest;
		                }
		            }
		            Vector2D v = bestPoint.minus(new Point(me().getX(), me().getY()));
		            v = v.normalised().scale(1000000);
		            sendClear(time, (int)Math.ceil((me().getX() + v.getX())), (int)Math.ceil((me().getY() + v.getY())));
		            System.out.println("Clearing blockade " + target);
		        }
	    }
	    private boolean moveToSeenBlockade(int time,ChangeSet changes) {
	    	List<EntityID> nearPath = search.getPath(me().getPosition(),getBlockedArea(changes));
            if (nearPath != null) {
                Road r = (Road)model.getEntity(nearPath.get(nearPath.size() - 1));
                Blockade b = getTargetBlockade(r, -1);
                if(b!=null) {
                	sendMove(time, nearPath, b.getX(), b.getY());
                	System.out.println("移动到视力所及的有路障的地方");
                	return true;
                }
            }	  
            return false;
	    }
	    private void handleChannelInfo(Collection<Command> heard) {
        	for(Command com : heard) {
        		AKSpeak speak = (AKSpeak)com;
        		String string = new String(speak.getContent());
        		if(string.length() == 0 && speak.getAgentID().equals(me().getID()))
        			continue;
        		else{
        			System.out.println("待处理");
        		}
        	}
	    }
	    private boolean traversalRoads(int time) {
	    	if(roadsInMyCluster.size() != 0){
	    		EntityID[]  roadsID = roadsInMyCluster.toArray(new EntityID[0]);
        		List<EntityID> path = search.getPath(me().getPosition(),roadsID[0]);
        		if(path !=null) {
        			System.out.println("遍历道路");
        			System.out.println();
        			Road r = (Road)model.getEntity(roadsID[0]);
        			sendMove(time,path,r.getX(),r.getY());
        			return true;
        		}
        	}
	    	return false;
	    }
	    //获得能看到的离我最近的障碍ID
	    private EntityID getBlockedArea(ChangeSet changes) {
	    	Set<EntityID> seen = changes.getChangedEntities();
	       // Set<EntityID> roads = cluster.getRoadsAroundCluster();只要能看到连续的,都可以清除
	        double minDistance = Integer.MAX_VALUE;
	        int x = me().getX();
	        int y = me().getY();
	        EntityID closestRoad = null;
	        for (EntityID areaID : seen) {
	        	StandardEntity entity = model.getEntity(areaID);
	        	if(entity instanceof Area){
	        		Area area = (Area)entity;
	        		if (area.isBlockadesDefined() && !area.getBlockades().isEmpty()) {
	        			System.out.println("看到的路障"+areaID.getValue());
	        			for(EntityID blockadeID : area.getBlockades()) {
	        				Blockade blockade = (Blockade) model.getEntity(blockadeID);
	        				double distance = findDistanceTo(blockade,x,y);
	        				if(distance < minDistance) {
	        					minDistance = distance;
	        					closestRoad = areaID;
	        				}
	        			}
	        		}
	        	}
	        }
	        return closestRoad;
	    }
	    private Blockade getTargetBlockade() {
	        Area location = (Area)location();
	        Blockade result = getTargetBlockade(location, distance);
	        if (result != null) {
	            return result;
	        }
	        for (EntityID next : location.getNeighbours()) {
	            location = (Area)model.getEntity(next);
	            result = getTargetBlockade(location, distance);
	            if (result != null) {
	                return result;
	            }
	        }
	        return null;
	    }
	    //获得目标路障
	    private Blockade getTargetBlockade(Area area, int maxDistance) {
	        if (area == null || !area.isBlockadesDefined()) {
	            return null;
	        }
	        List<EntityID> ids = area.getBlockades();
	        int x = me().getX();
	        int y = me().getY();
	        for (EntityID next : ids) {
	            Blockade b = (Blockade)model.getEntity(next);
	            double d = findDistanceTo(b, x, y);
	            if(maxDistance < 0 && d != 0)
	            	System.out.println("移动到能看到的最短距离"+d);
	            if (d < maxDistance) {
		            System.out.println("距路障最短距离"+d);
	                return b;
	            }
	        }
	        return null;
	    }

	    private double findDistanceTo(Blockade b, int x, int y) {
	        List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(b.getApexes()), true);
	        double best = Double.MAX_VALUE;
	        Point origin = new Point(x, y);
	        //算距离最近的点
	        for (Line2D next : lines) {
	            Point2D closest = GeometryTools2D.getClosestPointOnSegment(next, origin);
	            double d = GeometryTools2D.getDistance(origin, closest);
	            if (d < best) {
	                best = d;
	            }

	        }
	        return best;
	    }

	    @Override
	    protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
	        return EnumSet.of(StandardEntityURN.POLICE_FORCE);
	    }

	}
