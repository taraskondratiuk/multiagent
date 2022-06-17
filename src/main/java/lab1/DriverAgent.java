package lab1;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.List;

public class DriverAgent extends Agent {
    int id;
    Location currentLocation;
    long busyUntil = 0L;
    boolean isBusy(long currentTime) {
        return this.busyUntil > currentTime;
    }

    @Override
    protected void setup() {
        Object[] args = getArguments();
        int xBound = (int) args[0];
        int yBound = (int) args[1];
        int id = (int) args[2];
        this.currentLocation = Location.random(xBound, yBound);
        this.id = id;

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("driver");
        sd.setName("JADE-driver");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        addBehaviour(new DriverBehaviour());
        System.out.println(this + " inited");
    }

    private class DriverBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                System.out.println("driver " + msg.getContent());
                String content = msg.getContent();
                ACLMessage reply = msg.createReply();

                String cmd;
                if (content.contains(" ")) {
                    cmd = content.substring(0, content.indexOf(' '));
                } else {
                    cmd = content;
                }
                if (cmd.equals(Constants.DRIVER_CHECK)) {
                    if (!isBusy(System.currentTimeMillis())) {
                        reply.setPerformative(ACLMessage.CONFIRM);
                        reply.setContent(Constants.DRIVER_AVAILABLE + " " + currentLocation.toString());
                    } else {
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent(Constants.DRIVER_NOT_AVAILABLE);
                    }
                } else if (cmd.equals(Constants.DRIVER_CONFIRM)) {
                    List<Location> locs = Location.fromString(content);
                    int timeForRide = 0;
                    for (int i = 0; i < locs.size(); i++) {
                        if (i == 0) {
                            timeForRide += Location.minutesBetweenLocations(currentLocation, locs.get(i));
                        } else {
                            timeForRide += Location.minutesBetweenLocations(locs.get(i - 1), locs.get(i));
                        }
                    }
                    busyUntil = System.currentTimeMillis() + timeForRide;
                    currentLocation = locs.get(locs.size() - 1);
                }

                myAgent.send(reply);
            } else {
                block();
            }
        }
    }

    @Override
    public String toString() {
        return "Driver " + id;
    }

    @Override
    protected void takeDown() {
        System.out.println(this + " take down");
    }
}
