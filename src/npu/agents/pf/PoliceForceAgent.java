package npu.agents.pf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import npu.agents.clustering.Cluster;
import npu.agents.clustering.ClustingMap;
import npu.agents.common.AbstractCommonAgent;
import npu.agents.communication.utils.ChangeSetUtil;
import npu.agents.communication.utils.CommUtils.MessageID;
import npu.agents.communication.utils.MessageCompressUtil;
import npu.agents.communication.utils.MessageHandler;
import npu.agents.model.Auction;
import npu.agents.model.Message;
import npu.agents.model.Point;
import npu.agents.utils.DistanceSorter;
import npu.agents.utils.KConstants;
import rescuecore2.log.Logger;
import rescuecore2.messages.Command;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Edge;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;

public class PoliceForceAgent extends AbstractCommonAgent<PoliceForce> {
	private int maxClearDistance;
	private Cluster cluster;
	private Set<EntityID> roadsUnexplored = new HashSet<EntityID>();
	private Set<EntityID> roadsExplored = new HashSet<EntityID>();
	private Set<EntityID> positionsBlockedCivilians = new HashSet<EntityID>();
	private Set<EntityID> positionsBlockedPlatoon = new HashSet<EntityID>();
	private Set<EntityID> positionsStuckedHuman = new HashSet<EntityID>();
	private Set<EntityID> positionsBlockdedMainRoadsTotally = new HashSet<EntityID>();
	private Set<EntityID> buildingsEntrancesHumanStuckedIn = new HashSet<EntityID>();
	private Set<EntityID> roadsInCluster;
	//private Map<EntityID, Set<EntityID>> refugesEntrancesMap;
	private List<EntityID> pathByPriotity;

	private ChangeSetUtil seenChanges;
	private MessageHandler messageHandler;

	private EntityID[] prePosition = new EntityID[2];
	private EntityID nowPosition;
	
	private Point2D[] preLocation = new Point2D[2];
	private Point2D lastLocation;
	private Vector2D lastV;
	private Map<EntityID,Set<EntityID>> buildingEntranceMap;
    private Set<Auction> auctions = new HashSet<Auction>();
	private Map<EntityID,StandardEntity> PFLocations = new HashMap<EntityID,StandardEntity>();
	private boolean isBuried = false;
	private boolean excuteTask = false;
	private EntityID lotPosition;//竞品位置
	@Override
	public void postConnect() {
		super.postConnect();
		try {
			ClustingMap.initMap(Math.min(KConstants.countOfpf, configuration.getPoliceForcesCount()), 100, model);
			buildingEntranceMap = ClustingMap.getBuildingEntrances();
			cluster = ClustingMap.assignAgentToCluster(me(), model);
			if (cluster == null) {
				System.out.println("该agent没有分配到cluster");
				return;
			}
			roadsInCluster = cluster.getRoadsInCluster();
			// roadsUnexplored = cluster.getRoadsInCluster();这样两者都指向相同的地方了
			Iterator<EntityID> iter = roadsInCluster.iterator();
			while (iter.hasNext()) {
				roadsUnexplored.add(iter.next());
			}
			maxClearDistance = configuration.getMaxCleardistance();
			seenChanges = new ChangeSetUtil();
			messageHandler = new MessageHandler(configuration);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("聚类失败");
			return;
		}
	}

	@Override
	public void think(int time, ChangeSet changes, Collection<Command> heard) {
		if (time < configuration.getIgnoreAgentCommand())
			return;
		handleReceiveMessages(time, changes, heard);
		sendMessages(time);
		if(time % 5 == 0 && !excuteTask)
			reportMyPosition(time);
	}
	private void handleReceiveMessages(int time, ChangeSet changes, Collection<Command> heard) {
		if (time == configuration.getIgnoreAgentCommand()) {
			sendSubscribe(time, configuration.getPoliceChannel());
			if (me().isHPDefined() && me().isBuriednessDefined() && me().getHP() > 0 && me().getBuriedness() > 0) {
				callForATHelp(time, MessageID.PLATOON_BURIED);
			}
			int bandwidth = configuration.getRadioChannelByID(configuration.getPoliceChannel()).getBandwidth();
			System.out.println("带宽共 "+bandwidth);
			return;
		}
		handleHeard(time, heard);
		seenChanges.handleChanges(model, changes);
		messageHandler.reportBuildingInfo(time, seenChanges);
		messageHandler.reportInjuredHumanInfo(time, seenChanges);
		updateClearedRoads();
		if (me().isHPDefined() && me().isBuriednessDefined() && me().getHP() > 0 && me().getBuriedness() > 0) {
			callForATHelp(time, MessageID.PLATOON_BURIED);
			System.out.println(me().getID() + " is buried,hp is" + me().getHP());
			EntityID position = positionWhereIStuckedIn(seenChanges.getSeenBlockades(),me());
			if(position != null){
				callForPFHelp(time,MessageID.PLATOON_STUCKED);
				return;
			}
		}
		if (me().isHPDefined() && me().isDamageDefined() && me().getHP() > 0 && me().getDamage() > 0) {
			if (canMoveToRefuge(time, me())) {
			} else {
				callForATHelp(time, MessageID.PLATOON_BURIED);
			}
			System.out.println(me().getID() + " is buried,hp is" + me().getHP() + ",damage is" + me().getDamage());
		}
		if(stuckedInBuilding(time)){
			System.out.println(me().getID()+" leave for building");
			return;
		}
		if(lotPosition == null){
			lotPosition = makeAuction();
			if(lotPosition != null){
				pathByPriotity = search.getPath(me().getPosition(), lotPosition, null);
			}
		}else if(lotPosition.equals(me().getPosition())&&seenChanges.getClearedRoads().contains(me().getPosition())){
			excuteTask = false;
		}
		/*List<EntityID> templist = new ArrayList<EntityID>();*/
		/*if (!buildingsEntrancesHumanStuckedIn.isEmpty()) {
			templist.addAll(buildingsEntrancesHumanStuckedIn);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);
		} else *//*if (!positionsStuckedHuman.isEmpty()) {
			templist.addAll(positionsStuckedHuman);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);
			System.out.println(me().getID()+"路径也不为空"+(pathByPriotity == null ? 0:pathByPriotity.size()));
		} else if (!positionsBlockdedMainRoadsTotally.isEmpty()) {
			templist.addAll(positionsBlockdedMainRoadsTotally);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);
		} else if (!positionsBlockedPlatoon.isEmpty()) {
			templist.addAll(positionsBlockedPlatoon);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);
		} else if (!positionsBlockedCivilians.isEmpty()) {
			templist.addAll(positionsBlockedCivilians);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);
		}*/
		if(pathByPriotity != null) {
			excuteTask = true;
			for(Blockade blockade : seenChanges.getSeenBlockades()){
				if(blockadeOnPath(blockade)){
					if (clearNearBlockes(time)) {
						System.out.println("pf of "+me().getID()+" is clearBlockades to lot in the way");
						return;
					}
					/*if (moveToSeenBlockade(time)) {
						System.out.println();
						return;
					}*/
					if(me().getPosition().equals(pathByPriotity.get(pathByPriotity.size()-1))){
						System.out.println("pf of "+me().getID()+" 到达求助点");
						pathByPriotity = null;
						return;
					}
					sendMove(time,pathByPriotity);
				}
			}
		}
		basicClear(time, seenChanges);
	}

	private void updateClearedRoads() {
		Set<EntityID> clearedRoadsIDs = seenChanges.getClearedRoads();
		roadsExplored.addAll(clearedRoadsIDs);
		for (EntityID clearedRoadID : roadsExplored) {
			roadsUnexplored.remove(clearedRoadID);
			positionsBlockedCivilians.remove(clearedRoadID);
			positionsBlockedPlatoon.remove(clearedRoadID);
			positionsBlockdedMainRoadsTotally.remove(clearedRoadID);
			positionsStuckedHuman.remove(clearedRoadID);
		}
	}

	private void handleHeard(int time, Collection<Command> heards) {
		for (Command heard : heards) {
			if (!(heard instanceof AKSpeak))
				continue;
			AKSpeak info = (AKSpeak) heard;
			EntityID agentID = info.getAgentID();
			if(agentID.equals(me().getID())){
				System.out.println("pf of " +me().getID()+" 收到了自己打出的消息，跳过");
				continue;
			}
			String content = new String(info.getContent());
			if (content.isEmpty()) {
				System.out.println("pf of " + me().getID() + " heard dropped message from " + info.getAgentID()
						+ " from " + info.getChannel());
				continue;
			}
			// 居民发出voice呼救信息，要么受伤，要么被困
			if (content.equalsIgnoreCase("Ouch") || content.equalsIgnoreCase("Help")) {
				StandardEntity entity = model.getEntity(agentID);
				if(!(entity instanceof Human)){
					System.out.println(me().getID()+" 听到呼救 ，但不是普通居民，怪哉");
					continue;
				}
				Human civilian = (Human) model.getEntity(agentID);
				EntityID civilianPositionID = civilian.getPosition();
				if (civilian.isHPDefined() && civilian.isBuriednessDefined() && civilian.isDamageDefined()
						&& civilian.getHP() > 0 && (civilian.getBuriedness() > 0 || civilian.getDamage() > 0)) {
					int index = MessageCompressUtil.getAreaIndex(civilianPositionID);
					Message message = new Message(MessageID.CIVILIAN_INJURED, index, time,
							configuration.getAmbulanceChannel());
					messageHandler.addMessage(message);
				} else {
					if (roadsInCluster.contains(civilianPositionID))
						positionsBlockedCivilians.add(civilianPositionID);
				}
				System.out.println("pf of " + me().getID() + "heard civilian help");
				continue;
			}
			String[] heardInfo = content.split(",");
			String typeOfhearedInfo = heardInfo[0];
			String positionOfInfo = heardInfo[1];
			int id = Integer.parseInt(typeOfhearedInfo);
			int position = Integer.parseInt(positionOfInfo);
			EntityID positionID = new EntityID(position);
			MessageID messageID = MessageID.values()[id];
			restorePFposition(agentID,messageID,position);
			if (!roadsInCluster.contains(positionID))
				continue;
			handleMessage(positionID, messageID);
			
		}
	}

	private void handleMessage(EntityID positionID, MessageID messageID) {
		System.out.println("pf of " + me().getID() + " is handling heard message ");
		switch (messageID) {
		case ROAD_CLEARED:
			roadsExplored.add(positionID);
			break;
		case CIVILIAN_BLOCKED:
			positionsBlockedCivilians.add(positionID);
			break;
		case PLATOON_BLOCKED:
			positionsBlockedPlatoon.add(positionID);
			break;
		case HUMAN_STUCKED:
			positionsStuckedHuman.add(positionID);
			System.out.println("收到消息");
			break;
		case MAIN_ROAD_BLOCKED_TOTALLY:
			positionsBlockdedMainRoadsTotally.add(positionID);
			break;
		case ENTRANCE_BLOCKED_TOTALLY:
			buildingsEntrancesHumanStuckedIn.add(positionID);
			break;
		default:
			break;
		}
	}

	private void basicClear(int time, ChangeSetUtil seenChanges) {
		if (clearNearBlockes(time)) {
			System.out.println();
			return;
		}
		if (moveToSeenBlockade(time)) {
			System.out.println();
			return;
		} else if (pathByPriotity != null) {
			sendMove(time, pathByPriotity);
			pathByPriotity = null;
			System.out.println();
			return;
		} else {
			boolean traverslOk = traversalRoads(time);
			System.out.println();
			if (!traverslOk) {
				List<EntityID> path = randomWalk(areaIDs);
				if (path != null) {
					System.out.println(me().getID() + " random walk in cluster");
					System.out.println();
					sendMove(time, path);
				}
				System.out.println();
			}
			return;
		}
	}

	private boolean clearNearBlockes(int time) {
		Blockade target = getTargetBlockade();
		if (target != null) {
			List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(target.getApexes()),
					true);
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
			if(best < maxClearDistance){
				Vector2D v = bestPoint.minus(new Point(me().getX(), me().getY()));
				v = v.normalised().scale(1000000);
			/*	if(somethingWrong()){
					List<EntityID> path = randomWalk(areaIDs);
					sendMove(time,path);
					System.out.println(me().getID()+"清理路障莫名其妙的错误，随机动一动咯");
					return false;
				}*/
				if(lastV != null && Math.abs(lastV.getX()-v.getX()) < 5 
						&& Math.abs(lastV.getY()-v.getY()) < 5){
					System.out.println(me().getID()+"清理路障莫名其妙的错误，往障碍物方向动一动");
					List<EntityID> path = new ArrayList<EntityID>();
					path.add(me().getPosition());
					sendMove(time, path,(int) ((me().getX() + v.getX())), (int)((me().getY() + v.getY())));
					lastV = null;
					return true;
				}
				sendClear(time, (int) ((me().getX() + v.getX())), (int)((me().getY() + v.getY())));
				System.out.println(me().getID() + "Clearing blockade " + target);
				lastV= v;
				return true;
			}else{
				return false;
			}
		}
		return false;
	}

	private boolean moveToSeenBlockade(int time) {
		Blockade target = getSeenFarBlockade();
		if (target == null) {
			System.out.println(me().getID() + " 没有看到任何路障");
			return false;
		}
		Point2D closest = findClosestPoint(target, me().getX(), me().getY());
		if(closest == null){
			return false;
		}
		List<EntityID> path = new ArrayList<EntityID>();
		if (target.getPosition().equals(me().getPosition())) {
			System.out.println("blockade " + target.getID() + "and human " + me().getID() + " is at same position "
					+ me().getPosition());
			path.add(me().getPosition());
			sendMove(time, path, (int) closest.getX(), (int) closest.getY());
			return true;
		} else {
			path = search.getPath(me().getPosition(), target.getPosition(), null);
			if (path != null) {
				if (somethingWrong()) {
					path.clear();
					path.add(nowPosition);
					Area entity = (Area) model.getEntity(nowPosition);
					if (entity instanceof Road) {
						Road road = (Road) entity;
						boolean flag = false;
						int x = 0, y = 0;
						for (Edge edge : road.getEdges()) {
							if (edge.isPassable()) {
								StandardEntity neirEntity = model.getEntity(edge.getNeighbour());
								if (neirEntity instanceof Building) {
									flag = true;
									continue;
								} else {
									x = (edge.getStartX() + edge.getEndX()) / 2;
									y = (edge.getStartY() + edge.getEndY()) / 2;
								}
							}
						}
						if (flag && x != 0 && y != 0) {
							sendMove(time, path, x, y);
							System.out.println(me().getID() + "往边的中间移动");
							return true;
						}
					}
				}
				System.out.println((path == null ? 0 : 1)+":"+(closest == null ? 2 : 3));
				sendMove(time, path/*, (int) closest.getX(), (int) closest.getY()*/);
				System.out.println(me().getID() + " 移动到视力所及的不在同一条路上的有路障的地方");
				return true;
			}
		}
		return false;
	}

	private boolean traversalRoads(int time) {
		System.out.println(me().getID()+":"+roadsUnexplored.size());
		if (roadsUnexplored.size() == 0) {
			resetAllRoadsUnexplored();
		}
		if (roadsUnexplored.size() != 0) {
			List<EntityID> roadsIDs = new ArrayList<EntityID>(roadsUnexplored);
			System.out.println("roadsUnexplored has " + roadsIDs.size());
			distanceSort(roadsIDs, me());
			if(roadsIDs.size() < 3){
				roadsUnexplored.removeAll(roadsIDs);
				return false;
			}
			List<EntityID> path = search.getPath(me().getPosition(), roadsIDs.get(1), null);
			if (path != null) {
				System.out.println(me().getID() + " 遍历道路,非随机");
				roadsUnexplored.removeAll(path);
				Road r = (Road) model.getEntity(roadsIDs.get(0));
				sendMove(time, path, r.getX(), r.getY());
				return true;
			}
		}
		return false;
	}

	private void resetAllRoadsUnexplored() {
		Iterator<EntityID> roadsID = roadsInCluster.iterator();
		while (roadsID.hasNext()) {
			EntityID roadID = roadsID.next();
			System.out.println("add roads in cluster back " + roadID);
			roadsUnexplored.add(roadID);
		}
		System.out.println(me().getID() + " reset all roads unexplored " + roadsUnexplored.size());
	}

	private Blockade getSeenFarBlockade() {
		Area location = (Area) location();
		/*if (!(location instanceof Road))
			return null;*/
		Blockade result = getTargetBlockade(location, -1);
		if (result != null) {
			System.out.println(me().getID() + " 移动到同一条路 " + result.getID() + " 看到但清理不到的路障 ");
			return result;
		}
		for (EntityID next : location.getNeighbours()) {
			location = (Area) model.getEntity(next);
			if (!(location instanceof Road))
				continue;
			result = getTargetBlockade(location, -1);
			if (result != null) {
				System.out.println(me().getID() + " 移动到另一条路上 " + result.getID() + " 看到但清理不到的路障 ");
				return result;
			}
		}
		List<Blockade> otherBlockades = new ArrayList<Blockade>(seenChanges.getSeenBlockades());
		Collections.sort(otherBlockades, new DistanceSorter(location(), model));
		if (!otherBlockades.isEmpty()) {
			System.out.println(me().getID() + " 在当前路和和它的邻近路都没有看到路障，在其它地方看到了");
			return otherBlockades.get(0);
		}
		return null;
	}

	private Blockade getTargetBlockade() {
		Area location = (Area) location();
		/*if (!(location instanceof Road))
			return null;*/
		Blockade result = getTargetBlockade(location, maxClearDistance);
		if (result != null) {
			return result;
		}
		for (EntityID next : location.getNeighbours()) {
			location = (Area) model.getEntity(next);
			if (!(location instanceof Road))
				continue;
			result = getTargetBlockade(location, maxClearDistance);
			if (result != null) {
				System.out.println(me().getID() + " 能清理到的路障在另一条路上");
				return result;
			}
		}
		return null;
	}

	// 获得目标路障
	private Blockade getTargetBlockade(Area area, int maxDistance) {
		if (area == null || !area.isBlockadesDefined()) {
			return null;
		}
		List<EntityID> ids = area.getBlockades();
		int x = me().getX();
		int y = me().getY();
		for (EntityID next : ids) {
			Blockade b = (Blockade) model.getEntity(next);
			if(b == null)
				return null;
			int d = findDistanceTo(b, x, y);
			if (maxDistance < 0) {
				System.out.println(me().getID() + " 看到但清理不到的路障 " + b.getID() + " 最短距离 " + d);
				return b;
			}
			if (d <= maxDistance) {
				System.out.println(me().getID() + " 距能清理的路障 " + b.getID() + " 最短距离" + d);
				return b;
			}
		}
		return null;
	}

	private int findDistanceTo(Blockade b, int x, int y) {
		if(b == null)
			return (int) Double.MAX_VALUE;
		List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(b.getApexes()), true);
		double best = Double.MAX_VALUE;
		Point2D origin = new Point2D(x, y);
		// 算距离最近的点
		for (Line2D next : lines) {
			Point2D closest = GeometryTools2D.getClosestPointOnSegment(next, origin);
			double d = GeometryTools2D.getDistance(origin, closest);
			if (d < best) {
				best = d;
			}
		}
		return (int) best;
	}

	private Point2D findClosestPoint(Blockade b, int x, int y) {
		List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(b.getApexes()), true);
		double best = Double.MAX_VALUE;
		Point2D origin = new Point2D(x, y);
		Point2D closest = null;
		// 算距离最近的点
		for (Line2D next : lines) {
			Point2D near = GeometryTools2D.getClosestPointOnSegment(next, origin);
			double d = GeometryTools2D.getDistance(origin, near);
			if (d < best) {
				best = d;
				closest = near;
			}
		}
		return closest;
	}

	@Override
	public EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
		return EnumSet.of(StandardEntityURN.POLICE_FORCE);
	}

	public void callForATHelp(int time, MessageID messageID) {
		int index = MessageCompressUtil.getAreaIndex(me().getPosition());
		Message message = new Message(messageID, index, time, configuration.getAmbulanceChannel());
		messageHandler.addMessage(message);
		sendRest(time);
		System.out.println(messageID.name() + " of " + me().getID() + " at " + time);
	}
	public void callForPFHelp(int time, MessageID messageID) {
		int index = MessageCompressUtil.getAreaIndex(me().getPosition());
		Message message = new Message(messageID, index, time, configuration.getPoliceChannel());
		messageHandler.addMessage(message);
		sendRest(time);
		System.out.println(messageID.name() + " of " + me().getID() + " at " + time);
	}
	public boolean canMoveToRefuge(int time, Human me) {
		   EntityID[] refugeIDs = (EntityID[]) getEntrancesOfRefuges().keySet().toArray();
		   if(refugeIDs.length == 0)
			   System.out.println("没有任何避难所");
		   int key = new Random(refugeIDs.length).nextInt();
		   Set<EntityID> refugeEntrancesIDs = getEntrancesOfRefuges().get(key);
			List<EntityID> refugeEntrances = new ArrayList<EntityID>(refugeEntrancesIDs);
			distanceSort(refugeEntrances, me);
			List<EntityID> path = search.getPath(me.getPosition(), refugeEntrances.get(0), null);
			if (path != null) {
				path.add(refugeIDs[key]);
				sendMove(time, path);
				System.out.println(me().getID() + "of at move to refuge for damage");
				return true;
			}
		return false;
	}

	public void sendMessages(int time) {
		sendAllVoiceMessages(time);
		sendAllRadioMessages(time);
		messageHandler.getMessagesWillSend().clear();
	}

	private void sendAllRadioMessages(int time) {
		for (Message message : messageHandler.getMessagesWillSend()) {
			sendSpeak(time, message.getChannel(), message.toMessage().getBytes());
		}
	}

	private void sendAllVoiceMessages(int time) {
		for (Message message : messageHandler.getMessagesWillSend()) {
			sendSpeak(time, message.getChannel(), message.toMessage().getBytes());
		}
	}

	private boolean somethingWrong() {
		preLocation[1] = preLocation[0];
		preLocation[0] = lastLocation;
		lastLocation = new Point2D(me().getX(),me().getY());
		if (preLocation[0] != null && preLocation[1] != null && lastLocation != null
				&& Math.abs(lastLocation.getX()-preLocation[0].getX())<5 && 
				Math.abs(preLocation[1].getX()-preLocation[0].getX())<5 &&
				Math.abs(lastLocation.getY()-preLocation[0].getY())<5 && 
				Math.abs(preLocation[1].getY()-preLocation[0].getY())<5) {
			preLocation[1] = null;
			preLocation[0] = null;
			return true;
		}
		return false;
	}
	private boolean blockadeOnPath(Blockade blockade) {
		if (blockade.getPosition().getValue() == location().getID().getValue()) {
			return true;
		}
		if (pathByPriotity != null) {
			for (EntityID pathID : pathByPriotity) {
				if (blockade.getPosition().getValue() == pathID.getValue()) {
					return true;
				}
			}
		}
		return false;
	}
	private boolean stuckedInBuilding(int time){
		List<EntityID> path = new ArrayList<EntityID>();
		Area location = (Area)location();
		if(location instanceof Building ){
			if(clearNearBlockes(time))
				return true;
			if(location instanceof Refuge && me().getDamage() > 0)
				return false;
			List<EntityID> neighboursIDs = location.getNeighbours();
			for(EntityID neighbourID: neighboursIDs){
				StandardEntity neighbour = model.getEntity(neighbourID);
				if(neighbour instanceof Road){
					path.add(location.getID());
					path.add(neighbour.getID());
					sendMove(time,path);
					System.out.println(me().getID()+" 从建筑物中走出去");
					return true;
				}
			}
		}
		return false;
	}
	private void reportMyPosition(int time) {
		int index = -1;
		EntityID position = me().getPosition();
		StandardEntity entity = model.getEntity(position);
		String data = null;
		if(entity instanceof Building){
			index = MessageCompressUtil.getBuildingIndex(position);
			data = MessageID.BUILDING_LOCATION.ordinal()+","+index;
			System.out.println(me().getID()+" report my position "+data+"，消息长度"+data.length());
		}else if (entity instanceof Road){
			index = MessageCompressUtil.getRoadIndex(position);
			data = MessageID.ROAD_LOCATION.ordinal()+","+index;
			System.out.println(me().getID()+" report my position "+data+"，消息长度"+data.length());
		}
		sendSpeak(time,configuration.getPoliceChannel(),data.getBytes());
	}
	private void restorePFposition(EntityID agentID,MessageID messageID,int index){
		if(messageID.equals(MessageID.ROAD_LOCATION)){
			Road road = MessageCompressUtil.getRoadByIndex(index);
			if(road != null){
				PFLocations.put(agentID, road);
			}
		}
		if(messageID.equals(MessageID.BUILDING_LOCATION)){
			Building building = MessageCompressUtil.getBuildingByIndex(index);
			if(building != null){
				PFLocations.put(agentID, building);
			}
		}
	}
	private EntityID makeAuction(){
		List<EntityID> templist = new ArrayList<EntityID>();
		templist.addAll(PFLocations.keySet());
		/*if (!buildingsEntrancesHumanStuckedIn.isEmpty()) {
			templist.addAll(buildingsEntrancesHumanStuckedIn);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);
		} else */if (!positionsStuckedHuman.isEmpty()) {
			for(EntityID positionID:positionsStuckedHuman){
				distanceSort3(templist,(Area)model.getEntity(positionID));
				System.out.println("pf of "+me().getID()+" is sort distance "+templist);
				if(model.getEntity(templist.get(0)).equals(me().getID())){
					System.out.println("pf of "+me().getID()+" take the lot of "+positionID);
					positionsStuckedHuman.clear();
					return positionID;
				}
			}
			
			/*templist.addAll(positionsStuckedHuman);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);
			System.out.println(me().getID()+"路径也不为空"+(pathByPriotity == null ? 0:pathByPriotity.size()));
		} else if (!positionsBlockdedMainRoadsTotally.isEmpty()) {
			templist.addAll(positionsBlockdedMainRoadsTotally);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);
		} else if (!positionsBlockedPlatoon.isEmpty()) {
			templist.addAll(positionsBlockedPlatoon);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);
		} else if (!positionsBlockedCivilians.isEmpty()) {
			templist.addAll(positionsBlockedCivilians);
			distanceSort(templist, me());
			pathByPriotity = search.getPath(me().getPosition(), templist.get(0), null);*/
		}		
		return null;
	}
	private void removeAuction(EntityID buyerPosition){
		for(Auction auction:auctions){
			if(auction.getAuctionID().equals(buyerPosition)){
				auctions.remove(auction);
				System.out.println(me().getID()+":已有人接手位置-"+buyerPosition+"处的拍卖");
				break;
			}
		}
	}
	
}
