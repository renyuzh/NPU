package rescuecore2.messages.control;

import rescuecore2.worldmodel.EntityID;
import rescuecore2.messages.AbstractCommand;

import java.io.InputStream;
import java.io.IOException;
import rescuecore2.messages.Control;

/**
   An agent "I'm done for this iteration" command.
 */
public class AKDone extends AbstractCommand implements Control {

    /**
       An AKDone message that populates its data from a stream.
       @param in The InputStream to read.
       @throws IOException If there is a problem reading the stream.
     */
    public AKDone(InputStream in) throws IOException {
        this();
        read(in);
    }

    /**
       Construct a done command.
       @param agent The ID of the agent issuing the command.
       @param time The time the command was issued.
       @param channels The IDs of the channels to speak on.
     */
    public AKDone(EntityID agent, int time) {
        this();
        setAgentID(agent);
        setTime(time);
    }

    private AKDone() {
        super(ControlMessageURN.AK_DONE);
    }

}
