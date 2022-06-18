package lab1;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

public class Main {
    public static void main(String[] args) throws StaleProxyException, InterruptedException {
        // Get a hold on JADE runtime
        jade.core.Runtime rt = jade.core.Runtime.instance();

        rt.setCloseVM(true);
        System.out.print("runtime created\n");

        Profile profile = new ProfileImpl("localhost", 1200, "MyPlatform");
        System.out.print("profile created\n");
        System.out.println("Launching a whole in-process platform..." + profile);
        jade.wrapper.AgentContainer mainContainer = rt.createMainContainer(profile);

        ProfileImpl pContainer = new ProfileImpl("localhost", 1200, "MyPlatform");
        System.out.println("Launching the agent container ..." + pContainer);
        System.out.println("containers created");
        System.out.println("Launching the rma agent on the main container ...");


        int X = 7900;
        int Y = 8100;
        int NUM_CITIZENS = 8;
        int NUM_DRIVERS = 3;
        System.out.println("X: " + X);
        System.out.println("Y: " + Y);
        System.out.println("NUM_CITIZENS: " + NUM_CITIZENS);
        System.out.println("NUM_DRIVERS: " + NUM_DRIVERS);
        for (int i = 0; i < NUM_DRIVERS; i++) {
            AgentController agent = mainContainer.createNewAgent("driver" + i,
                    "lab1.DriverAgent", new Object[] {X, Y, i});
            agent.start();
        }
        for (int i = 0; i < NUM_CITIZENS; i++) {
            AgentController agent = mainContainer.createNewAgent("citizen" + i,
                    "lab1.CitizenAgent", new Object[] {X, Y, i});
            agent.start();
        }
    }
}
