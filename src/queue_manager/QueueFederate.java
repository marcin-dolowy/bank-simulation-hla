package queue_manager;

import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.FederatesCurrentlyJoined;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.PriorityQueue;
import java.util.Random;


public class QueueFederate {
    /**
     * The sync point all federates will sync up on before starting
     */
    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private QueueFederateAmbassador fedamb;  // created when we connect
    private HLAfloat64TimeFactory timeFactory; // set when we join
    protected EncoderFactory encoderFactory;     // set when we join

    // caches of handle types - set once we join a federation
    protected ObjectClassHandle storageHandle;
    protected AttributeHandle storageMaxHandle;
    protected AttributeHandle storageAvailableHandle;
    protected InteractionClassHandle getCustomerChangeQueue;
    protected InteractionClassHandle getCurrentQueueSize;
    protected InteractionClassHandle getMoveCustomerToWindow;
    protected InteractionClassHandle getAssignCustomerToQueue;
    protected InteractionClassHandle getAddCustomer;
    protected InteractionClassHandle getFreeWindow;
    private Queue queue0 = new Queue(0);
    private Queue queue1 = new Queue(1);
    protected java.util.Queue<Integer> customersWaitingToAddToQueue = new PriorityQueue<>();
    private double changeQueueTime = 3;
    protected boolean window0IsWaitingForCustomer;
    protected boolean window1IsWaitingForCustomer;

    protected ParameterHandle customerIdHandle;
    protected ParameterHandle windowIdHandle;

    protected int storageMax = 0;
    protected int storageAvailable = 0;

    private void log(String message) {
        System.out.println("ConsumerFederate   : " + message);
    }

    private void waitForUser() {
        log(" >>>>>>>>>> Press Enter to Continue <<<<<<<<<<");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            reader.readLine();
        } catch (Exception e) {
            log("Error while waiting for user input: " + e.getMessage());
            e.printStackTrace();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    ////////////////////////// Main Simulation Method /////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    /**
     * This is the main simulation loop. It can be thought of as the main method of
     * the federate. For a description of the basic flow of this federate, see the
     * class level comments
     */
    public void runFederate(String federateName) throws Exception {
        /////////////////////////////////////////////////
        // 1 & 2. create the RTIambassador and Connect //
        /////////////////////////////////////////////////
        log("Creating RTIambassador");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();

        // connect
        log("Connecting...");
        fedamb = new QueueFederateAmbassador(this);
        rtiamb.connect(fedamb, CallbackModel.HLA_EVOKED);

        //////////////////////////////
        // 3. create the federation //
        //////////////////////////////
        log("Creating Federation...");
        // We attempt to create a new federation with the first three of the
        // restaurant FOM modules covering processes, food and drink
        try {
            URL[] modules = new URL[]{
                    (new File("foms/BankSimulation.xml")).toURI().toURL(),
            };

            rtiamb.createFederationExecution("BankSimulationFederation", modules);
            log("Created Federation");
        } catch (FederationExecutionAlreadyExists exists) {
            log("Didn't create federation, it already existed");
        } catch (MalformedURLException urle) {
            log("Exception loading one of the FOM modules from disk: " + urle.getMessage());
            urle.printStackTrace();
            return;
        }

        ////////////////////////////
        // 4. join the federation //
        ////////////////////////////

        rtiamb.joinFederationExecution(federateName,            // name for the federate
                "queue",   // federate type
                "BankSimulationFederation"     // name of federation
        );           // modules we want to add

        log("Joined Federation as " + federateName);

        // cache the time factory for easy access
        this.timeFactory = (HLAfloat64TimeFactory) rtiamb.getTimeFactory();

        ////////////////////////////////
        // 5. announce the sync point //
        ////////////////////////////////
        // announce a sync point to get everyone on the same page. if the point
        // has already been registered, we'll get a callback saying it failed,
        // but we don't care about that, as long as someone registered it
        rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);
        // wait until the point is announced
        while (fedamb.isAnnounced == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        // WAIT FOR USER TO KICK US OFF
        // So that there is time to add other federates, we will wait until the
        // user hits enter before proceeding. That was, you have time to start
        // other federates.
        waitForUser();

        ///////////////////////////////////////////////////////
        // 6. achieve the point and wait for synchronization //
        ///////////////////////////////////////////////////////
        // tell the RTI we are ready to move past the sync point and then wait
        // until the federation has synchronized on
        rtiamb.synchronizationPointAchieved(READY_TO_RUN);
        log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
        while (fedamb.isReadyToRun == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        /////////////////////////////
        // 7. enable time policies //
        /////////////////////////////
        // in this section we enable/disable all time policies
        // note that this step is optional!
        enableTimePolicy();
        log("Time Policy Enabled");

        //////////////////////////////
        // 8. publish and subscribe //
        //////////////////////////////
        // in this section we tell the RTI of all the data we are going to
        // produce, and all the data we want to know about
        publishAndSubscribe();
        log("Published and Subscribed");

        /////////////////////////////////////
        // 10. do the main simulation loop //
        /////////////////////////////////////

        Random rand = new Random();
        while (fedamb.isRunning) {
            //send current queue0 size
            ParameterHandleValueMap parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(2);
            ParameterHandle getCurrentQueueSizeHandleQueue0Id = rtiamb.getParameterHandle(getCurrentQueueSize, "queueId");
            ParameterHandle getCurrentQueueSizeHandleQueue0Size = rtiamb.getParameterHandle(getCurrentQueueSize, "size");
            HLAinteger32BE queue0Id = encoderFactory.createHLAinteger32BE(queue0.getId());
            HLAinteger32BE queue0Size = encoderFactory.createHLAinteger32BE(queue0.getQueue().size());
            parameterHandleValueMap.put(getCurrentQueueSizeHandleQueue0Id, queue0Id.toByteArray());
            parameterHandleValueMap.put(getCurrentQueueSizeHandleQueue0Size, queue0Size.toByteArray());
            rtiamb.sendInteraction(getCurrentQueueSize, parameterHandleValueMap, generateTag());
//            //send current queue1 size
            parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(2);
            ParameterHandle getCurrentQueueSizeHandleQueue1Id = rtiamb.getParameterHandle(getCurrentQueueSize, "queueId");
            ParameterHandle getCurrentQueueSizeHandleQueue1Size = rtiamb.getParameterHandle(getCurrentQueueSize, "size");
            HLAinteger32BE queue1Id = encoderFactory.createHLAinteger32BE(queue1.getId());
            HLAinteger32BE queue1Size = encoderFactory.createHLAinteger32BE(queue1.getQueue().size());
            parameterHandleValueMap.put(getCurrentQueueSizeHandleQueue1Id, queue1Id.toByteArray());
            parameterHandleValueMap.put(getCurrentQueueSizeHandleQueue1Size, queue1Size.toByteArray());
            rtiamb.sendInteraction(getCurrentQueueSize, parameterHandleValueMap, generateTag());


                //assign customer to q0 or q1
                Integer cid = customersWaitingToAddToQueue.poll();
                int randomQueueIdNumber = rand.nextInt(2);
                if (!customersWaitingToAddToQueue.isEmpty()) {
                    System.out.println(customersWaitingToAddToQueue.size());
                }
//            System.out.println(randomQueueIdNumber);
//            System.out.println(cid);
                if (randomQueueIdNumber == 0 && cid != null) {
                    queue0.getQueue().add(cid);

                    parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(2);
                    ParameterHandle getAssignCustomerToQueueHandleQueue0Id = rtiamb.getParameterHandle(getAssignCustomerToQueue, "customerId");
                    ParameterHandle getAssignCustomerToQueueHandleQueue0Size = rtiamb.getParameterHandle(getAssignCustomerToQueue, "queueId");

                    HLAinteger32BE customerId = encoderFactory.createHLAinteger32BE(cid);
                    queue0Id = encoderFactory.createHLAinteger32BE(queue0.getId());
                    parameterHandleValueMap.put(getAssignCustomerToQueueHandleQueue0Id, customerId.toByteArray());
                    parameterHandleValueMap.put(getAssignCustomerToQueueHandleQueue0Size, queue0Id.toByteArray());
                    rtiamb.sendInteraction(getAssignCustomerToQueue, parameterHandleValueMap, generateTag());

                } else if (randomQueueIdNumber == 1 && cid != null) {
                    queue1.getQueue().add(cid);

                    parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(2);
                    ParameterHandle getAssignCustomerToQueueHandleQueue1Id = rtiamb.getParameterHandle(getAssignCustomerToQueue, "customerId");
                    ParameterHandle getAssignCustomerToQueueHandleQueue1Size = rtiamb.getParameterHandle(getAssignCustomerToQueue, "queueId");
                    HLAinteger32BE customerId = encoderFactory.createHLAinteger32BE(cid);
                    queue1Id = encoderFactory.createHLAinteger32BE(queue1.getId());
                    parameterHandleValueMap.put(getAssignCustomerToQueueHandleQueue1Id, customerId.toByteArray());
                    parameterHandleValueMap.put(getAssignCustomerToQueueHandleQueue1Size, queue1Id.toByteArray());
                    rtiamb.sendInteraction(getAssignCustomerToQueue, parameterHandleValueMap, generateTag());
                }

                //change customer in queue
                if (fedamb.federateTime == changeQueueTime) {
                    randomQueueIdNumber = rand.nextInt(2);

                    if (randomQueueIdNumber == 0 && !queue0.getQueue().isEmpty()) {

                        int randomCustomerIdNumber = rand.nextInt(queue0.getQueue().size());
                        int customerId = queue0.getQueue().get(randomCustomerIdNumber);

                        if (randomCustomerIdNumber > queue1.getQueue().size()) {
                            queue0.getQueue().remove(customerId);
                            queue1.getQueue().add(customerId);

                            parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(2);
                            ParameterHandle getCustomerChangeQueueHandleCustomerId = rtiamb.getParameterHandle(getCustomerChangeQueue, "customerId");
                            ParameterHandle getCustomerChangeQueueHandleQueueId = rtiamb.getParameterHandle(getCustomerChangeQueue, "queueId");
                            HLAinteger32BE cusId = encoderFactory.createHLAinteger32BE(customerId);
                            HLAinteger32BE queueId = encoderFactory.createHLAinteger32BE(queue0.getId());
                            parameterHandleValueMap.put(getCustomerChangeQueueHandleCustomerId, cusId.toByteArray());
                            parameterHandleValueMap.put(getCustomerChangeQueueHandleQueueId, queueId.toByteArray());
                            rtiamb.sendInteraction(getCustomerChangeQueue, parameterHandleValueMap, generateTag());
                        }
                    } else if (randomQueueIdNumber == 1 && !queue1.getQueue().isEmpty()) {
                        int randomCustomerIdNumber = rand.nextInt(queue1.getQueue().size());
                        int customerId = queue1.getQueue().get(randomCustomerIdNumber);

                        if (randomCustomerIdNumber > queue0.getQueue().size()) {
                            queue1.getQueue().remove(customerId);
                            queue0.getQueue().add(customerId);

                            parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(2);
                            ParameterHandle getCustomerChangeQueueHandleCustomerId = rtiamb.getParameterHandle(getCustomerChangeQueue, "customerId");
                            ParameterHandle getCustomerChangeQueueHandleQueueId = rtiamb.getParameterHandle(getCustomerChangeQueue, "queueId");
                            HLAinteger32BE cusId = encoderFactory.createHLAinteger32BE(customerId);
                            HLAinteger32BE queueId = encoderFactory.createHLAinteger32BE(queue1.getId());
                            parameterHandleValueMap.put(getCustomerChangeQueueHandleCustomerId, cusId.toByteArray());
                            parameterHandleValueMap.put(getCustomerChangeQueueHandleQueueId, queueId.toByteArray());
                            rtiamb.sendInteraction(getCustomerChangeQueue, parameterHandleValueMap, generateTag());
                        }
                    }

                    int min = (int) fedamb.federateTime;
                    int max = (int) (fedamb.federateTime + 5.0);
                    changeQueueTime = rand.nextInt(max - min + 1) + min;
                }

                if (window0IsWaitingForCustomer && !queue0.getQueue().isEmpty()) {
                    queue0.getQueue().remove(0);
                    parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(1);
                    ParameterHandle getMoveCustomerToWindowHandle = rtiamb.getParameterHandle(getMoveCustomerToWindow, "windowId");
                    HLAinteger32BE windowId = encoderFactory.createHLAinteger32BE(0);
                    parameterHandleValueMap.put(getMoveCustomerToWindowHandle, windowId.toByteArray());
                    rtiamb.sendInteraction(getMoveCustomerToWindow, parameterHandleValueMap, generateTag());
                    window0IsWaitingForCustomer = false;
                }

                if (window1IsWaitingForCustomer && !queue1.getQueue().isEmpty()) {
                    queue1.getQueue().remove(0);
                    parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(1);
                    ParameterHandle getMoveCustomerToWindowHandle = rtiamb.getParameterHandle(getMoveCustomerToWindow, "windowId");
                    HLAinteger32BE windowId = encoderFactory.createHLAinteger32BE(1);
                    parameterHandleValueMap.put(getMoveCustomerToWindowHandle, windowId.toByteArray());
                    rtiamb.sendInteraction(getMoveCustomerToWindow, parameterHandleValueMap, generateTag());
                    window1IsWaitingForCustomer = false;
                }

            advanceTime(1.0);
        }


        ////////////////////////////////////
        // 12. resign from the federation //
        ////////////////////////////////////
        rtiamb.resignFederationExecution(ResignAction.DELETE_OBJECTS);

        log("Resigned from Federation");

        ////////////////////////////////////////
        // 13. try and destroy the federation //
        ////////////////////////////////////////
        // NOTE: we won't die if we can't do this because other federates
        //       remain. in that case we'll leave it for them to clean up
        try {
            rtiamb.destroyFederationExecution("ExampleFederation");
            log("Destroyed Federation");
        } catch (
                FederationExecutionDoesNotExist dne) {
            log("No need to destroy federation, it doesn't exist");
        } catch (
                FederatesCurrentlyJoined fcj) {
            log("Didn't destroy federation, federates still joined");
        }
    }


////////////////////////////////////////////////////////////////////////////
////////////////////////////// Helper Methods //////////////////////////////
////////////////////////////////////////////////////////////////////////////

    /**
     * This method will attempt to enable the various time related properties for
     * the federate
     */
    private void enableTimePolicy() throws Exception {
        // NOTE: Unfortunately, the LogicalTime/LogicalTimeInterval create code is
        //       Portico specific. You will have to alter this if you move to a
        //       different RTI implementation. As such, we've isolated it into a
        //       method so that any change only needs to happen in a couple of spots
        HLAfloat64Interval lookahead = timeFactory.makeInterval(fedamb.federateLookahead);

        ////////////////////////////
        // enable time regulation //
        ////////////////////////////
        this.rtiamb.enableTimeRegulation(lookahead);

        // tick until we get the callback
        while (fedamb.isRegulating == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        /////////////////////////////
        // enable time constrained //
        /////////////////////////////
        this.rtiamb.enableTimeConstrained();

        // tick until we get the callback
        while (fedamb.isConstrained == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    /**
     * This method will inform the RTI about the types of data that the federate will
     * be creating, and the types of data we are interested in hearing about as other
     * federates produce it.
     */
    private void publishAndSubscribe() throws RTIexception {
        String iAddCustomer = "HLAinteractionRoot.addCustomer";
        getAddCustomer = rtiamb.getInteractionClassHandle(iAddCustomer);
        String iFreeWindow = "HLAinteractionRoot.freeWindow";
        getFreeWindow = rtiamb.getInteractionClassHandle(iFreeWindow);


        String iCustomerChangeQueue = "HLAinteractionRoot.customerChangeQueue";
        getCustomerChangeQueue = rtiamb.getInteractionClassHandle(iCustomerChangeQueue);
        String iCurrentQueueSize = "HLAinteractionRoot.currentQueueSize";
        getCurrentQueueSize = rtiamb.getInteractionClassHandle(iCurrentQueueSize);
        String iMoveCustomerToWindow = "HLAinteractionRoot.moveCustomerToWindow";
        getMoveCustomerToWindow = rtiamb.getInteractionClassHandle(iMoveCustomerToWindow);
        String iAssignCustomerToQueue = "HLAinteractionRoot.assignCustomerToQueue";
        getAssignCustomerToQueue = rtiamb.getInteractionClassHandle(iAssignCustomerToQueue);
        // do the publication
        rtiamb.publishInteractionClass(getCustomerChangeQueue);
        rtiamb.publishInteractionClass(getCurrentQueueSize);
        rtiamb.publishInteractionClass(getMoveCustomerToWindow);
        rtiamb.publishInteractionClass(getAssignCustomerToQueue);

        customerIdHandle = rtiamb.getParameterHandle(getAddCustomer, "customerId");
        windowIdHandle = rtiamb.getParameterHandle(getFreeWindow, "windowId");
        rtiamb.subscribeInteractionClass(getFreeWindow);
        rtiamb.subscribeInteractionClass(getAddCustomer);
    }

    /**
     * This method will request a time advance to the current time, plus the given
     * timestep. It will then wait until a notification of the time advance grant
     * has been received.
     */
    private void advanceTime(double timestep) throws RTIexception {
        // request the advance
        fedamb.isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + timestep);
        rtiamb.timeAdvanceRequest(time);

        // wait for the time advance to be granted. ticking will tell the
        // LRC to start delivering callbacks to the federate
        while (fedamb.isAdvancing) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    private short getTimeAsShort() {
        return (short) fedamb.federateTime;
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }

    public static void main(String[] args) {
        String federateName = "Queue";
        if (args.length != 0) {
            federateName = args[0];
        }

        try {
            new QueueFederate().runFederate(federateName);
        } catch (Exception rtie) {
            rtie.printStackTrace();
        }
    }

    public Queue getQueue0() {
        return queue0;
    }

    public Queue getQueue1() {
        return queue1;
    }
}
