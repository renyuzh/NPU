package npu.main;

import npu.agents.at.AmbulanceTeamAgent;
import npu.agents.centre.AmbulanceCenter;
import npu.agents.centre.FireCenter;
import npu.agents.centre.PoliceCenter;
import npu.agents.fb.FireBrigadeAgent;
import npu.agents.pf.PoliceForceAgent;
import npu.agents.utils.KConstants;
import rescuecore2.Constants;
import rescuecore2.components.ComponentConnectionException;
import rescuecore2.components.ComponentLauncher;
import rescuecore2.components.TCPComponentLauncher;
import rescuecore2.config.Config;
import rescuecore2.connection.ConnectionException;
import rescuecore2.registry.Registry;
import rescuecore2.standard.entities.StandardEntityFactory;
import rescuecore2.standard.entities.StandardPropertyFactory;
import rescuecore2.standard.messages.StandardMessageFactory;
public final class LaunchAgents {
	public static void main(String[] args) {
		Registry.SYSTEM_REGISTRY.registerEntityFactory(StandardEntityFactory.INSTANCE);
		Registry.SYSTEM_REGISTRY.registerMessageFactory(StandardMessageFactory.INSTANCE);
		Registry.SYSTEM_REGISTRY.registerPropertyFactory(StandardPropertyFactory.INSTANCE);
		
		int fb = Integer.parseInt(args[0]);
		int fc = Integer.parseInt(args[3]);
		int pf = Integer.parseInt(args[1]);
		int pc = Integer.parseInt(args[4]);
		int at = Integer.parseInt(args[2]);
		int ac = Integer.parseInt(args[5]);
		String host = args[6];

		KConstants.countOfpf = pf;
		
		lauchAgents(host,fb,pf,at,fc,pc,ac);		
	}

	private static void lauchAgents(String host, int fb, int pf, int at, int fc, int pc, int ac) {
		Config  config = new Config();
		int port = config.getIntValue(Constants.KERNEL_PORT_NUMBER_KEY,Constants.DEFAULT_KERNEL_PORT_NUMBER);
	
		ComponentLauncher  launcher = new TCPComponentLauncher(host,port,config);
        try {
        	/*int i = 1;
			try {
				while(fb != 0) {
					launcher.connect(new FireBrigadeAgent());
					fb--;
					System.out.println("Success: 成功连接第" + (i++) + "个FireBrigadeAgent");
				}		
			} catch (ComponentConnectionException e) {
				System.out.println("Failed: 连接第" + i+"个FireBrigade时失败");
			}*/
        	int i = 1;
			try {
				while(pf != 0) {
					launcher.connect(new PoliceForceAgent());
					pf--;
					System.out.println("Success: 成功连接第" + (i++) + "个PoliceForceAgent");
				}		
			} catch (ComponentConnectionException e) {
				System.out.println("Failed: 连接第" + i+"个PoliceForceAgent时失败");
			}
        /*	i = 1;
			try {
				while(at != 0) {
					launcher.connect(new AmbulanceTeamAgent());
					at--;
					System.out.println("Success: 成功连接第" + (i++)  + "个AmbulanceTeamAgent");
				}		
			} catch (ComponentConnectionException e) {
				System.out.println("Failed: 连接第" + i+"个AmbulanceTeamAgent时失败");
			}
        	i = 1;
			try {
				while(fc != 0) {
					launcher.connect(new FireCenter());
					fc--;
					System.out.println("Success: 成功连接第" + (i++)  + "个FireCenter");
				}		
			} catch (ComponentConnectionException e) {
				System.out.println("Failed: 连接第" + i+"个FireCenter时失败");
			}
        	i = 1;
			try {
				while(pc != 0) {
					launcher.connect(new PoliceCenter());
					pc--;
					System.out.println("Success: 成功连接第" + (i++)  + "个PoliceCenter");
				}		
			} catch (ComponentConnectionException e) {
				System.out.println("Failed: 连接第" + i+"个PoliceCenter时失败");
			}
            i = 1;
			try {
				while(ac != 0) {
					launcher.connect(new AmbulanceCenter());
					ac--;
					System.out.println("Success: 成功连接第" + (i++)  + "个AmbulanceCenter");
				}		
			} catch (ComponentConnectionException e) {
				System.out.println("Failed: 连接第" + i+"个AmbulanceCenter时失败");
			}*/
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ConnectionException e) {
			e.printStackTrace();
		} 
    }
}
