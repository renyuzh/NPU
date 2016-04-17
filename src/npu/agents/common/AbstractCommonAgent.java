package npu.agents.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import npu.agents.communication.utils.ConfigUtil;
import npu.agents.search.AStar;
import rescuecore2.misc.Pair;
import rescuecore2.standard.components.StandardAgent;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Hydrant;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

public abstract class AbstractCommonAgent<E extends StandardEntity> extends StandardAgent<E> {
	protected static final int RANDOM_WALK_LENGTH = 50;
	protected ConfigUtil configuration;
	/**
	 * The search algorithm.
	 */
	protected AStar search;
	/**
	 * Cache of building IDs.
	 */
	protected Set<EntityID> buildingIDs;

	/**
	 * Cache of road IDs.
	 */
	protected Set<EntityID> roadIDs;

	/**
	 * Cache of refuge
	 */
	protected Set<Refuge> refuges;
	/**
	 * Cache of hydrant IDs
	 */
	protected Set<EntityID> hydrantIDs;
    
	protected Set<EntityID> areaIDs;
	protected Map<EntityID, Set<EntityID>> neighbours;

	/*protected ChangeSetUtil seenChanges;
	protected Set<Message> messagesWillSend = new HashSet<Message>();*/

	private Map<EntityID, Set<EntityID>> entrancesOfRefuges;

	@Override
	protected void postConnect() {
		super.postConnect();
		buildingIDs = new HashSet<EntityID>();
		roadIDs = new HashSet<EntityID>();
		refuges = new HashSet<Refuge>();
		hydrantIDs = new HashSet<EntityID>();
		areaIDs = new HashSet<EntityID>();
		ArrayList<EntityID> test = new ArrayList<EntityID>();
		for (StandardEntity next : model) {
			if(next instanceof Area){
				areaIDs.add(next.getID());
			}
			if (next instanceof Building) {
				buildingIDs.add(next.getID());
			}
			if (next instanceof Road) {
				roadIDs.add(next.getID());
			}
			if (next instanceof Refuge) {
				refuges.add((Refuge) next);
			}
			if (next instanceof Hydrant) {
				hydrantIDs.add(next.getID());
			}
		}
		search = new AStar(model);
		neighbours = search.getGraph();
		configuration = new ConfigUtil(config);
		/*seenChanges = new ChangeSetUtil();*/
	}

/*	protected void callForATHelp(int time, MessageID messageID) {
		Message message = new Message(messageID, me().getID(), time, configuration.getAmbulanceChannel());
		messagesWillSend.add(message);
		sendRest(time);
		System.out.println(messageID.name() + " of " + me().getID() + " at " + time);
	}

	protected boolean canMoveToRefuge(int time, Human me, Map<EntityID, Set<EntityID>> refugesEntrancesMap) {
		Set<EntityID> refugeIDsInCluster = refugesEntrancesMap.keySet();
		if (!refugeIDsInCluster.isEmpty()) {
			List<EntityID> refugeIDs = new ArrayList<EntityID>(refugeIDsInCluster);
			distanceSort(refugeIDs, me);
			EntityID dest = ((EntityID[]) getEntrancesOfRefuges().get(refugeIDs.get(0)).toArray())[0];
			List<EntityID> path = search.getPath(me.getPosition(), dest, null);
			path.add(refugeIDs.get(0));
			if (path != null) {
				Refuge refuge = (Refuge) model.getEntity(refugeIDs.get(0));
				Logger.info("Moving to refuge");
				sendMove(time, path, refuge.getX(), refuge.getY());
				System.out.println(me().getID() + "of at move to refuge for damage");
				return true;
			}
		}
		return false;
	}*/

	/*protected void addBuildingInfoToMessageSend(int time) {
		
		 * for (EntityID unburntBuidingID : seenChanges.getBuildingsUnburnt()) {
		 * Message message = new Message(MessageID.BUILDING_UNBURNT,
		 * unburntBuidingID, time, configuration.getFireChannel());
		 * messagesWillSend.add(message); }
		 
		for (EntityID warmBuidingID : seenChanges.getBuildingsIsWarm()) {
			System.out.println("send warm building message");
			Message message = new Message(MessageID.BUILDING_WARM, warmBuidingID, time, configuration.getFireChannel());
			messagesWillSend.add(message);
		}
		for (EntityID onFireBuildingID : seenChanges.getBuildingsOnFire()) {
			System.out.println("send on fire building message");
			Message message = new Message(MessageID.BUILDING_ON_FIRE, onFireBuildingID, time,
					configuration.getFireChannel());
			messagesWillSend.add(message);
		}
		for (EntityID extinguishBuildingID : seenChanges.getBuildingsExtinguished()) {
			System.out.println("send extinguished building message");
			Message message = new Message(MessageID.BUILDING_EXTINGUISHED, extinguishBuildingID, time,
					configuration.getFireChannel());
			messagesWillSend.add(message);
		}
		for (EntityID burtOutBuildingID : seenChanges.getBuildingBurtOut()) {
			System.out.println("send burtOut building message");
			Message message = new Message(MessageID.BUILDING_BURNT_OUT, burtOutBuildingID, time,
					configuration.getFireChannel());
			messagesWillSend.add(message);
		}
	}

	protected void addInjuredHumanInfoToMessageSend(int time) {
		for (Human human : seenChanges.getBuriedPlatoons()) {
			Message message = new Message(MessageID.PLATOON_BURIED, human.getPosition(), time,
					configuration.getAmbulanceChannel());
			if (human.getID() != me().getID())
				messagesWillSend.add(message);
		}
		for (Human human : seenChanges.getInjuredCivilians()) {
			Message message = new Message(MessageID.CIVILIAN_INJURED, human.getPosition(), time,
					configuration.getAmbulanceChannel());
			if (human.getID() != me().getID())
				messagesWillSend.add(message);
		}
	}*/

	/*public void addRoadsInfoToMessageSend(int time) {
		for (Blockade blockade : seenChanges.getSeenBlockades()) {
			Message message = new Message(MessageID.PLATOON_BLOCKED, blockade.getID(), time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message);
		}
		for (Road road : seenChanges.getTotallyBlockedRoad()) {
			Message message = new Message(MessageID.ROAD_BLOCKED_TOTALLY, road.getID(), time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message);
		}

		for (Blockade blockadeStuckedHuman : seenChanges.getBlockadesHumanStuckedIn()) {
			Message message = new Message(MessageID.HUMAN_STUCKED, blockadeStuckedHuman.getPosition(), time,
					configuration.getPoliceChannel());
			messagesWillSend.add(message);
		}
		for (EntityID roadID : seenChanges.getClearedRoads()) {
			Message message = new Message(MessageID.ROAD_CLEARED, roadID, time, configuration.getPoliceChannel());
			messagesWillSend.add(message);
		}
	}*/

/*	protected void sendMessages(int time) {
		sendAllVoiceMessages(time);
		sendAllRadioMessages(time);
	}

	private void sendAllRadioMessages(int time) {
		for (Message message : messagesWillSend) {
			String data = message.getMessageID().ordinal() + "," + message.getPositionID().getValue();
			sendSpeak(time, message.getChannel(), data.getBytes());
		}
		messagesWillSend.clear();
	}

	private void sendAllVoiceMessages(int time) {
		for (Message message : messagesWillSend) {
			String data = message.getMessageID().ordinal() + "," + message.getPositionID().getValue();
			sendSpeak(time, message.getChannel(), data.getBytes());
		}
		messagesWillSend.clear();
	}*/

	protected List<EntityID> randomWalk(Set<EntityID> ids) {
		List<EntityID> result = new ArrayList<EntityID>(RANDOM_WALK_LENGTH);
		Set<EntityID> seen = new HashSet<EntityID>();
		EntityID current = ((Human) me()).getPosition();
		for (int i = 0; i < RANDOM_WALK_LENGTH; ++i) {
			result.add(current);
			seen.add(current);
			List<EntityID> possible = new ArrayList<EntityID>(neighbours.get(current));
			Collections.shuffle(possible, random);
			boolean found = false;
			for (EntityID next : possible) {
				// 警察和消防只能在该簇内的道路上遍历，医生都要遍历
				if (ids.contains(next)) {
					if (seen.contains(next)) {
						continue;
					}
					current = next;
					found = true;
					System.out.print("在该簇内的道路遍历");
					break;
				}
			}
			if (!found) {
				break;
			}
		}
		return result;
	}

	protected Map<EntityID, Set<EntityID>> getEntrancesOfRefuges() {
		Set<EntityID> roads = new HashSet<EntityID>();
		EntityID refugeID = null;
		for (Refuge refuge : refuges) {
			for (EntityID id : refuge.getNeighbours()) {
				StandardEntity nearEntity = model.getEntity(id);
				if (nearEntity instanceof Road) {
					roads.add(nearEntity.getID());
					refugeID = refuge.getID();
				}
			}
			if (refugeID != null) {
				entrancesOfRefuges.put(refugeID, roads);
				refugeID = null;
				roads.clear();
			}
		}
		return entrancesOfRefuges;
	}

	protected void distanceSort(List<EntityID> targets, final Human me) {
		Collections.sort(targets, new Comparator<EntityID>() {
			@Override
			public int compare(EntityID o1, EntityID o2) {
				int distance1 = findDistanceTo(o1, me.getX(), me.getY());
				int distance2 = findDistanceTo(o2, me.getX(), me.getY());
				if (distance1 > distance2)
					return 1;
				if (distance1 < distance2)
					return -1;
				return 0;
			}
		});
	}

	protected int findDistanceTo(EntityID id, int x, int y) {
		StandardEntity entity = model.getEntity(id);
		Pair<Integer, Integer> pair = entity.getLocation(model);
		return (int) Math.hypot(pair.first() - x, pair.second() - y);
	}

	protected int getDistanceByPath(List<EntityID> path, Human me) {
		int distance = 0;
		Pair<Integer, Integer> start = new Pair(me.getX(), me.getY());
		for (EntityID id : path) {
			Pair<Integer, Integer> next = model.getEntity(id).getLocation(model);
			distance += (int) Math.hypot(next.first() - start.first(), next.second() - start.second());
			start = next;
		}
		return distance;
	}

	protected EntityID positionWhereIStuckedIn(Set<Blockade> seenBlockades, Human me) {
		Set<Blockade> blockades = new HashSet<Blockade>();
		for (Blockade blockade : seenBlockades) {
			ArrayList<Integer> X = new ArrayList<Integer>();
			ArrayList<Integer> Y = new ArrayList<Integer>();
			for (int i = 0; i < blockade.getApexes().length - 1; i = i + 2) {
				X.add(blockade.getApexes()[i]);
				Y.add(blockade.getApexes()[i + 1]);
			}
			if (in_or_out_of_polygon(X, Y, me.getX(), me.getY())) {
				return blockade.getPosition();
			}
		}
		return null;
	}

	public boolean in_or_out_of_polygon(ArrayList<Integer> X, ArrayList<Integer> Y, int x, int y) {
		int i, j;
		boolean c = false;
		for (i = 0, j = X.size() - 1; i < X.size(); j = i++) {
			if ((((Y.get(i) <= y) && (y < Y.get(j))) || ((Y.get(j) <= y) && (y < Y.get(i))))
					&& (x < (X.get(j) - X.get(i)) * (y - Y.get(i)) / (Y.get(j) - Y.get(i)) + X.get(i)))
				c = !c;
		}
		return c;
	}
}
