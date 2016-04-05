package kernel;

import java.util.Collection;
import java.util.Map;
import java.util.ArrayList;

import rescuecore2.connection.Connection;
import rescuecore2.connection.ConnectionListener;
import rescuecore2.messages.Message;
import rescuecore2.messages.Command;
import rescuecore2.messages.control.KASense;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.log.Logger;
import rescuecore2.misc.collections.LazyMap;
import rescuecore2.messages.control.AKDone;

/**
   This class is the kernel interface to an agent.
 */
public class AgentProxy extends AbstractKernelComponent {
    private Entity entity;
    private Map<Integer, Collection<Command>> commands;
	private Map<Integer, Boolean> dones;

    /**
       Construct an agent.
       @param name The name of the controlling agent.
       @param e The entity controlled by the agent.
       @param c The connection to the agent.
     */
    public AgentProxy(String name, Entity e, Connection c) {
        super(name, c);
        this.entity = e;
        commands = new LazyMap<Integer, Collection<Command>>() {
            @Override
            public Collection<Command> createValue() {
                return new ArrayList<Command>();
            }
        };
		dones = new LazyMap<Integer, Boolean>() {
            @Override
            public Boolean createValue() {
                return false;
            }
        };
        c.addConnectionListener(new AgentConnectionListener());
    }

    @Override
    public String toString() {
        return getName() + ": " + entity.getURN() + " " + entity.getID();
    }

    /**
       Get the entity controlled by this agent.
       @return The entity controlled by this agent.
     */
    public Entity getControlledEntity() {
        return entity;
    }

    /**
       Get all agent commands at a particular time.
       @param timestep The current timestep.
       @return A collection of messages representing the commands
     */
    public Collection<Command> getAgentCommands(int timestep) {
        Collection<Command> result;
        synchronized (commands) {
            result = commands.get(timestep);
        }
        Logger.trace(entity.toString() + " getAgentCommands(" + timestep + ") returning " + result);
        return result;
    }

	/**
       Check if an agent is done for a particular time.
       @param timestep The current timestep.
       @return True if the agent has signaled that it is done, or false otherwise
     */
	public boolean isDone(int timestep) {
		boolean result;
		synchronized (dones) {
			result = dones.get(timestep);
		}
		Logger.trace(entity.toString() + " isDone(" + timestep + ") returning " + result);
		return result;
	}

    /**
       Notify the of a perception update.
       @param time The current timestep.
       @param visible The set of visible changes.
       @param heard The set of communication messages that the agent heard.
     */
    public void sendPerceptionUpdate(int time, ChangeSet visible, Collection<? extends Command> heard) {
        KASense sense = new KASense(getControlledEntity().getID(), time, visible, heard);
        send(sense);
    }

    /**
       Register an agent command received.
       @param c The command that was received.
     */
    protected void commandReceived(Command c) {
        // Check that the command is for the right agent
        if (!c.getAgentID().equals(entity.getID())) {
            Logger.warn("Ignoring bogus command: Agent " + entity.getID() + " tried to send a command for agent " + c.getAgentID());
            return;
        }
        int time = c.getTime();
        Logger.trace("AgentProxy " + entity + " received " + c);
		
		if (c instanceof AKDone) {
			synchronized (dones) {
				dones.put(time, Boolean.TRUE);
				dones.notifyAll();
			}
			return;
		}

        synchronized (commands) {
            Collection<Command> result = commands.get(time);
            result.add(c);
            commands.notifyAll();
        }
    }

    private class AgentConnectionListener implements ConnectionListener {
        @Override
        public void messageReceived(Connection c, Message msg) {
            if (msg instanceof Command) {
                EntityID id = ((Command)msg).getAgentID();
                if (id.equals(getControlledEntity().getID())) {
                    commandReceived((Command)msg);
                }
            }
        }
    }
}
