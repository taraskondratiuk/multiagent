package lab1;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class CitizenAgent extends Agent {
    int id;
    Location home;
    Location location;
    long timeWaitingDriver = 0L;
    long busyUntil = 0L;
    boolean isBusy(long currentTime) {
        return this.busyUntil > currentTime;
    }
    String state;
    Random r = new Random();
    int stateSwitches = 0;
    long minutesGuesting() {
        return r.nextInt(60 * 3) + 30;
    }
    void switchState() {
        if (state.equals("hosting")) {
            state = "guesting";
        } else {
            state = "hosting";
        }
        stateSwitches++;
    }

    @Override
    protected void setup() {
        Object[] args = getArguments();
        int xBound = (int) args[0];
        int yBound = (int) args[1];
        int id = (int) args[2];
        state = id % 2 == 0 ? "hosting" : "guesting";
        this.home = Location.random(xBound, yBound);
        this.location = this.home;
        this.id = id;
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("citizen");
        sd.setName("JADE-citizen");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new TickerBehaviour(this, 60 * 12) { //switching state each 12 hours
            @Override
            protected void onTick() {
                if (stateSwitches < 3) {
                    switchState();
                    if (state.equals("guesting")) {
                        addBehaviour(new CitizenGuestBehaviour());
                    } else {
                        addBehaviour(new CitizenHostBehaviour());
                    }
                } else {
                    System.out.println("citizen " + id + " time waiting " + timeWaitingDriver);
                    System.exit(0);
                }
            }
        });

        System.out.println(this + " inited");
    }
    private class CitizenHostBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null && msg.getContent() != null) {
                String content = msg.getContent();
                ACLMessage reply = msg.createReply();

                String cmd;
                if (content.contains(" ")) {
                    cmd = content.substring(0, content.indexOf(' '));
                } else {
                    cmd = content;
                }

                if (cmd.equals(Constants.HOSTER_CHECK)) {
                    if (state.equals("hosting") && !isBusy(System.currentTimeMillis()) && location == home) {
                        reply.setPerformative(ACLMessage.CONFIRM);
                        reply.setContent(Constants.HOSTER_AVAILABLE + " " + location);
                    } else {
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent(Constants.HOSTER_NOT_AVAILABLE);
                    }
                    myAgent.send(reply);
                } else if (cmd.equals(Constants.HOSTER_CONFIRM)) {
                    busyUntil = Long.parseLong(content.replaceAll("[^\\d]", ""));
                }
            } else {
                block();
            }
        }
    }

    private class CitizenGuestBehaviour extends CyclicBehaviour {
        private MessageTemplate mt;
        private AID[] drivers;
        private AID[] citizens;
        private List<AID> hosters;
        private List<Location> hosterLocations;
        private List<AID> freeDrivers;
        private AID nearestDriver;
        private Location nearestLoc;
        private List<Location> freeDriversLocations;
        private int stage = 0;
        private int repliesCount = 0;
        private long startWaiting;
        @Override
        public void action() {
            if (!isBusy(System.currentTimeMillis()) && (stage > 0 || state.equals("guesting"))) {
                switch (stage) {
                    case 0:
                        startWaiting = 0;
                        DFAgentDescription template = new DFAgentDescription();
                        ServiceDescription sd = new ServiceDescription();
                        sd.setType("driver");
                        template.addServices(sd);
                        try {
                            DFAgentDescription[] result = DFService.search(myAgent, template);
                            drivers = new AID[result.length];
                            for (int i = 0; i < result.length; ++i) {
                                drivers[i] = result[i].getName();
                            }
                        }
                        catch (FIPAException fe) {
                            fe.printStackTrace();
                        }

                        DFAgentDescription template2 = new DFAgentDescription();
                        ServiceDescription sd2 = new ServiceDescription();
                        sd2.setType("citizen");
                        template2.addServices(sd2);
                        try {
                            DFAgentDescription[] result = DFService.search(myAgent, template2);
                            citizens = new AID[result.length];
                            for (int i = 0; i < result.length; ++i) {
                                citizens[i] = result[i].getName();
                            }
                        }
                        catch (FIPAException fe) {
                            fe.printStackTrace();
                        }
                        stage = 1;
                        break;
                    case 1:
                        // Send the cfp to all drivers
                        ACLMessage cfp2 = new ACLMessage(ACLMessage.CFP);
                        for (AID driver : drivers) {
                            cfp2.addReceiver(driver);
                        }
                        cfp2.setContent(Constants.DRIVER_CHECK);
                        cfp2.setConversationId("driver-check");
                        cfp2.setReplyWith(UUID.randomUUID().toString()); // Unique value
                        myAgent.send(cfp2);
                        // Prepare the template to get proposals
                        mt = MessageTemplate.and(MessageTemplate.MatchConversationId("driver-check"),
                                MessageTemplate.MatchInReplyTo(cfp2.getReplyWith()));
                        stage = 2;
                        freeDrivers = new ArrayList<>();
                        freeDriversLocations = new ArrayList<>();
                        break;
                    case 2:
                        // Receive all proposals/refusals from drivers
                        ACLMessage reply2 = myAgent.receive(mt);
                        if (reply2 != null) {
                            // Reply received
                            if (reply2.getContent().replaceAll(" .*", "").equals(Constants.DRIVER_AVAILABLE)) {
                                freeDrivers.add(reply2.getSender());
                                freeDriversLocations.add(Location.fromString(reply2.getContent()).get(0));
                            }
                            repliesCount++;
                            if (repliesCount >= drivers.length * 2 / 3) {
                                // We received all replies
                                int nearestDriverIdx = 0;
                                double nearestDist = Double.MAX_VALUE;
                                nearestLoc = null;
                                for (int i = 0; i < freeDriversLocations.size(); i++) {
                                    if (nearestLoc == null || Location.distanceBetweenLocations(location, freeDriversLocations.get(i)) < nearestDist) {
                                        nearestDist = Location.distanceBetweenLocations(location, freeDriversLocations.get(i));
                                        nearestLoc = freeDriversLocations.get(i);
                                        nearestDriverIdx = i;
                                    }
                                }
                                if (nearestLoc == null) {
                                    if (startWaiting != 0) startWaiting = System.currentTimeMillis();
                                    block();
                                } else {
                                    nearestDriver = freeDrivers.get(nearestDriverIdx);
                                    repliesCount = 0;
                                    stage = 3;
                                }
                            }
                        }
                        else {
                            block();
                        }
                        break;
                    case 3:
                        // Send the cfp to all citizens
                        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                        for (AID citizen : citizens) {
                            cfp.addReceiver(citizen);
                        }
                        cfp.setContent(Constants.HOSTER_CHECK);
                        cfp.setConversationId("host-check");
                        cfp.setReplyWith(UUID.randomUUID().toString()); // Unique value
                        myAgent.send(cfp);
                        // Prepare the template to get proposals
                        mt = MessageTemplate.and(MessageTemplate.MatchConversationId("host-check"),
                                MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                        stage = 4;
                        hosters = new ArrayList<>();
                        hosterLocations = new ArrayList<>();
                        break;
                    case 4:
                        // Receive all proposals/refusals from hosts
                        ACLMessage reply = myAgent.receive(mt);
                        if (reply != null && reply.getContent() != null) {
                            // Reply received
                            if (reply.getContent().contains(Constants.HOSTER_AVAILABLE)) {
                                hosterLocations.add(Location.fromString(reply.getContent()).get(0));
                                hosters.add(reply.getSender());
                            }
                            repliesCount++;
                            if (repliesCount >= citizens.length) {
                                // We received all replies

                                int hosterIdx = r.nextInt(hosters.size());
                                AID h = hosters.get(hosterIdx);
                                Location l = hosterLocations.get(hosterIdx);
                                busyUntil = Location.minutesBetweenLocations(home, l) + minutesGuesting();

                                ACLMessage confirmHost = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                                confirmHost.addReceiver(h);
                                confirmHost.setContent(Constants.HOSTER_CONFIRM + " " + busyUntil);
                                myAgent.send(confirmHost);


                                ACLMessage confirm = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                                confirm.addReceiver(nearestDriver);
                                confirm.setContent(Constants.DRIVER_CONFIRM + " " + home + " " + l);
                                myAgent.send(confirm);


                                timeWaitingDriver += Location.minutesBetweenLocations(nearestLoc, home);
                                if (startWaiting > 0) {
                                    timeWaitingDriver += (System.currentTimeMillis() - startWaiting);
                                    startWaiting = 0;
                                }
                                location = l;
                                repliesCount = 0;
                                stage = 5;
                            }
                        }
                        else {
                            block();
                        }
                        break;
                    case 5:
                        if (!isBusy(System.currentTimeMillis())) {
                            // Send the cfp to all drivers
                            ACLMessage cfp3 = new ACLMessage(ACLMessage.CFP);
                            for (AID driver : drivers) {
                                cfp3.addReceiver(driver);
                            }
                            cfp3.setContent(Constants.DRIVER_CHECK);
                            cfp3.setConversationId("driver-check");
                            cfp3.setReplyWith(UUID.randomUUID().toString()); // Unique value
                            myAgent.send(cfp3);
                            // Prepare the template to get proposals
                            mt = MessageTemplate.and(MessageTemplate.MatchConversationId("driver-check"),
                                    MessageTemplate.MatchInReplyTo(cfp3.getReplyWith()));
                            stage = 6;
                            freeDrivers = new ArrayList<>();
                            freeDriversLocations = new ArrayList<>();
                        } else {
                            block();
                        }
                        break;
                    case 6:
                        // Receive all proposals/refusals from drivers
                        ACLMessage reply3 = myAgent.receive(mt);
                        if (reply3 != null) {
                            // Reply received
                            if (reply3.getContent().contains(Constants.DRIVER_AVAILABLE)) {
                                freeDrivers.add(reply3.getSender());
                                freeDriversLocations.add(Location.fromString(reply3.getContent()).get(0));
                            }
                            repliesCount++;

                            if (repliesCount >= drivers.length) {
                                // We received all replies
                                int nearestDriverIdx = 0;
                                double nearestDist = Double.MAX_VALUE;
                                nearestLoc = null;
                                for (int i = 0; i < freeDriversLocations.size(); i++) {
                                    if (nearestLoc == null || Location.distanceBetweenLocations(location, freeDriversLocations.get(i)) < nearestDist) {
                                        nearestDist = Location.distanceBetweenLocations(location, freeDriversLocations.get(i));
                                        nearestLoc = freeDriversLocations.get(i);
                                        nearestDriverIdx = i;
                                    }
                                }
                                if (nearestLoc == null) {
                                    if (startWaiting != 0) startWaiting = System.currentTimeMillis();
                                    block();
                                } else {
                                    nearestDriver = freeDrivers.get(nearestDriverIdx);
                                    repliesCount = 0;
                                    stage = 7;
                                }
                            }
                        }
                        else {
                            block();
                        }
                        break;
                    case 7:
                        ACLMessage confirm = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                        confirm.addReceiver(nearestDriver);
                        confirm.setContent(Constants.DRIVER_CONFIRM + " " + home);
                        myAgent.send(confirm);


                        timeWaitingDriver += Location.minutesBetweenLocations(nearestLoc, home);
                        if (startWaiting > 0) {
                            timeWaitingDriver += System.currentTimeMillis() - startWaiting;
                            startWaiting = 0;
                        }
                        busyUntil = System.currentTimeMillis() + Location.minutesBetweenLocations(location, home);
                        location = home;
                        repliesCount = 0;
                        stage = 0;
                        break;
                }
            } else {
                block();
            }
        }
    }


    @Override
    public String toString() {
        return "Citizen " + id;
    }

    @Override
    protected void takeDown() {
        System.out.println(this + " take down");
    }
}
