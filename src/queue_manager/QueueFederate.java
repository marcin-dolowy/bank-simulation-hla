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
import java.util.ArrayList;
import java.util.List;


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
    private List<Queue> queues;

    protected ParameterHandle customerIdHandle;
    protected ParameterHandle windowIdHandle;

    protected int storageMax = 0;
    protected int storageAvailable = 0;

    public QueueFederate() {
        queues = new ArrayList<>();
        queues.add(new Queue(1));
        queues.add(new Queue(2));
    }

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
        while (fedamb.isRunning) {
            int consumed = queue.consume();
            if (storageAvailable - consumed >= 0) {
                ParameterHandleValueMap parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(1);
                ParameterHandle addProductsCountHandle = rtiamb.getParameterHandle(getProductsHandle, "count");
                HLAinteger32BE count = encoderFactory.createHLAinteger32BE(consumed);
                parameterHandleValueMap.put(addProductsCountHandle, count.toByteArray());
                rtiamb.sendInteraction(getProductsHandle, parameterHandleValueMap, generateTag());
            } else {
                log("Consuming canceled because of lack of products.");
            }
            // 9.3 request a time advance and wait until we get it
            advanceTime(queue.getTimeToNext());
            log("Time Advanced to " + fedamb.federateTime);
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
        } catch (FederationExecutionDoesNotExist dne) {
            log("No need to destroy federation, it doesn't exist");
        } catch (FederatesCurrentlyJoined fcj) {
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

        customerIdHandle = rtiamb.getParameterHandle(rtiamb.getInteractionClassHandle("HLAinteractionRoot.addCustomer"), "customerId");
        windowIdHandle = rtiamb.getParameterHandle(rtiamb.getInteractionClassHandle("HLAinteractionRoot.freeWindow"), "windowIdHandle");
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

    public List<Queue> getQueues() {
        return queues;
    }
}