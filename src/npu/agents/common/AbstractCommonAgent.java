package npu.agents.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import npu.agents.clustering.ClustingMap;
import npu.agents.communication.utils.ChangeSetUtil;
import npu.agents.communication.utils.ConfigUtil;
import npu.agents.pf.strategy.ClearUtil;
import npu.agents.search.AStar;
import npu.agents.utils.KConstants;
//import npu.agents.utils.SampleSearch;
import rescuecore2.config.Config;
import rescuecore2.connection.Connection;
import rescuecore2.messages.Command;
import rescuecore2.standard.components.StandardAgent;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Hydrant;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;

public abstract class AbstractCommonAgent<E extends StandardEntity> extends StandardAgent<E> {
 private static final int RANDOM_WALK_LENGTH = 50;
 protected ConfigUtil configuration;
 /**
    The search algorithm.
 */
 protected AStar search;
 /**
    Cache of building IDs.
 */
 protected List<EntityID> buildingIDs;

 /**
    Cache of road IDs.
 */
 protected List<EntityID> roadIDs;

 /**
    Cache of refuge IDs.
 */
 protected List<EntityID> refugeIDs;
 /**
  * Cache of hydrant IDs
  */
 protected List<EntityID> hydrantIDs;

 private Map<EntityID, Set<EntityID>> neighbours;
 
 protected ClearUtil clearUtil;
 protected ChangeSetUtil seenChanges;
	@Override
	protected void postConnect() {
		super.postConnect();
		buildingIDs = new ArrayList<EntityID>();
        roadIDs = new ArrayList<EntityID>();
        refugeIDs = new ArrayList<EntityID>();
        hydrantIDs = new ArrayList<EntityID>();
        ArrayList<EntityID> test = new ArrayList<EntityID>();
        for (StandardEntity next : model) {
            if (next instanceof Building) {
                buildingIDs.add(next.getID());
                test.add(next.getID());
            }
            if (next instanceof Road) {
                roadIDs.add(next.getID());
            }
            if (next instanceof Refuge) {
                refugeIDs.add(next.getID());
            }
            if (next instanceof Hydrant) {
            	hydrantIDs.add(next.getID());
            }
        }
        search = new AStar(model);
        neighbours = search.getGraph();
        configuration = new ConfigUtil(config);
        clearUtil = new ClearUtil();
        seenChanges = new ChangeSetUtil();
	}
    protected List<EntityID> randomWalkAroundCluster(Set<EntityID> roadsID) {
        List<EntityID> result = new ArrayList<EntityID>(RANDOM_WALK_LENGTH);
        Set<EntityID> seen = new HashSet<EntityID>();
        EntityID current = ((Human)me()).getPosition();
        for (int i = 0; i < RANDOM_WALK_LENGTH; ++i) {
            result.add(current);
            seen.add(current);
            List<EntityID> possible = new ArrayList<EntityID>(neighbours.get(current));
            Collections.shuffle(possible, random);
            boolean found = false;
            for (EntityID next : possible) {
            	//只能在该簇内的道路遍历
            	if(roadsID.contains(next)){
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
}
