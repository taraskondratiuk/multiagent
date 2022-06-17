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
        int N = 115;
        for (int i = 0; i < 1; i++) {
            AgentController agent = mainContainer.createNewAgent("driver" + i,
                    "lab1.DriverAgent", new Object[] {X, Y, i});
            agent.start();
        }
        for (int i = 0; i < 2; i++) {
            AgentController agent = mainContainer.createNewAgent("citizen" + i,
                    "lab1.CitizenAgent", new Object[] {X, Y, i});
            agent.start();
        }
//        Thread.sleep(1500L);
//        System.exit(0);
    }
}
