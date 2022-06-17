package lab1;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import javax.sound.midi.Soundbank;
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
    long minutesGuesting() {
        return r.nextInt(60 * 3) + 30;
    }
    void switchState() {
        if (state.equals("hosting")) {
            state = "guesting";
        } else {
            state = "hosting";
        }
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
        addBehaviour(new CitizenHostBehaviour());
        addBehaviour(new TickerBehaviour(this, 60 * 12 * 100) { //switching state each 12 hours
            @Override
            protected void onTick() {
                switchState();
                System.out.println("citizen " + id + " time waiging " + timeWaitingDriver);
            }
        });
        addBehaviour(new CitizenGuestBehaviour());

        System.out.println(this + " inited");
    }
    private class CitizenHostBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
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
                } else if (cmd.equals(Constants.HOSTER_CONFIRM)) {
                    busyUntil = Long.parseLong(content.replaceAll("[^\\d]", ""));
                }
                myAgent.send(reply);
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
        private int state = 0;
        private int repliesCount = 0;
        private long startWaiting;
        @Override
        public void action() {
            if (!isBusy(System.currentTimeMillis())) {
                switch (state) {
                    case 0:
                        System.out.println("1");
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
                            System.out.println("found " + drivers.length + " drivers");
                        }
                        catch (FIPAException fe) {
                            fe.printStackTrace();
                        }

                        DFAgentDescription template2 = new DFAgentDescription();
                        ServiceDescription sd2 = new ServiceDescription();
                        sd.setType("citizen");
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
                        state = 1;
                        break;
                    case 1:
                        System.out.println("2");
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
                        state = 2;
                        freeDrivers = new ArrayList<>();
                        freeDriversLocations = new ArrayList<>();
                        break;
                    case 2:
                        System.out.println("3");
                        // Receive all proposals/refusals from drivers
                        ACLMessage reply2 = myAgent.receive(mt);
                        if (reply2 != null) {
                            // Reply received
                            if (reply2.getContent().replaceAll(" .*", "").equals(Constants.DRIVER_AVAILABLE)) {
                                freeDrivers.add(reply2.getSender());
                                freeDriversLocations.add(Location.fromString(reply2.getContent()).get(0));
                            }
                            repliesCount++;
                            System.out.println("replies " + repliesCount);
                            System.out.println("drivers " + drivers.length);
                            if (repliesCount >= drivers.length * 2 / 3) {
                                // We received all replies
                                int nearestDriverIdx = 0;
                                double nearestDist = Double.MAX_VALUE;
                                nearestLoc = null;
                                System.out.println("loc3");
                                for (int i = 0; i < freeDriversLocations.size(); i++) {
                                    System.out.println("loc");
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
                                    state = 3;
                                }
                            }
                        }
                        else {
                            block();
                        }
                        break;
                    case 3:
                        System.out.println("4");
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
                        state = 4;
                        hosters = new ArrayList<>();
                        hosterLocations = new ArrayList<>();
                        break;
                    case 4:
                        System.out.println("5");
                        // Receive all proposals/refusals from hosts
                        ACLMessage reply = myAgent.receive(mt);
                        if (reply != null) {
                            // Reply received
                            System.out.println("content " + reply.getContent());
                            if (reply.getContent().equals(Constants.HOSTER_AVAILABLE)) {
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
                                    timeWaitingDriver += System.currentTimeMillis() - startWaiting;
                                    startWaiting = 0;
                                }
                                location = l;
                                repliesCount = 0;
                                state = 5;
                            }
                        }
                        else {
                            block();
                        }
                        break;
                    case 5:
                        System.out.println("6");
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
                            state = 6;
                            freeDrivers = new ArrayList<>();
                            freeDriversLocations = new ArrayList<>();
                        } else {
                            block();
                        }
                        break;
                    case 6:
                        System.out.println("7");
                        // Receive all proposals/refusals from drivers
                        ACLMessage reply3 = myAgent.receive(mt);
                        if (reply3 != null) {
                            // Reply received
                            if (reply3.getContent().equals(Constants.DRIVER_AVAILABLE)) {
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
                                    state = 7;
                                }
                            }
                        }
                        else {
                            block();
                        }
                        break;
                    case 7:
                        System.out.println("8");
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
                        timeWaitingDriver = 0;
                        state = 0;
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
