package npu.agents.fb;

import java.util.Collection;
import java.util.EnumSet;

import npu.agents.common.AbstractCommonAgent;
import rescuecore2.messages.Command;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.ChangeSet;

public class FireBrigadeAgent extends AbstractCommonAgent<FireBrigade>{

	@Override
	protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void think(int time, ChangeSet changes, Collection<Command> heard) {
		// TODO Auto-generated method stub
		
	}

}
