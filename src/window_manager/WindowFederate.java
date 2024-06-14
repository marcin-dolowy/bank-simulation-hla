package window_manager;

import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

public class WindowFederate {
    public static final String READY_TO_RUN = "ReadyToRun";
    private RTIambassador rtiamb;
    private WindowFederateAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory;
    protected EncoderFactory encoderFactory;

    protected ObjectClassHandle windowHandle;
    protected AttributeHandle storageMaxHandle;
    protected AttributeHandle storageAvailableHandle;

    protected InteractionClassHandle freeWindowID;
    protected InteractionClassHandle assignCustomerToWindow;
    protected InteractionClassHandle moveCustomerToWindow;

    protected ParameterHandle addWindowIdHandle;

    private double serviceEndTime = 5;


    protected int storageMax = 0;
    protected int storageAvailable = 0;

    private Window window0;
    private Window window1;

    public WindowFederate() {
        window0 = new Window(0);
        window1 = new Window(1);
    }

    private void log(String message) {
        System.out.println("WindowFederate   : " + message);
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

    public void runFederate(String federateName) throws Exception {
        log("Creating RTIambassador");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();

        log("Connecting...");
        fedamb = new WindowFederateAmbassador(this);
        rtiamb.connect(fedamb, CallbackModel.HLA_EVOKED);

        log("Creating Federation...");
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

        rtiamb.joinFederationExecution(federateName,
                "window",
                "BankSimulationFederation"
        );

        log("Joined Federation as " + federateName);

        this.timeFactory = (HLAfloat64TimeFactory) rtiamb.getTimeFactory();

        rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);

        while (fedamb.isAnnounced == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved(READY_TO_RUN);
        log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
        while (fedamb.isReadyToRun == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        enableTimePolicy();
        log("Time Policy Enabled");

        publishAndSubscribe();
        log("Published and Subscribed");

        while (fedamb.isRunning) {

            serviceCustomerInWindow(window0);
            serviceCustomerInWindow(window1);

            advanceTime(1);
            log("Time Advanced to " + fedamb.federateTime);
        }

        rtiamb.resignFederationExecution(ResignAction.DELETE_OBJECTS);
        log("Resigned from Federation");

        try {
            rtiamb.destroyFederationExecution("ExampleFederation");
            log("Destroyed Federation");
        } catch (FederationExecutionDoesNotExist dne) {
            log("No need to destroy federation, it doesn't exist");
        } catch (FederatesCurrentlyJoined fcj) {
            log("Didn't destroy federation, federates still joined");
        }
    }

    private void serviceCustomerInWindow(Window window) throws FederateNotExecutionMember, NotConnected, NameNotFound, InvalidInteractionClassHandle, RTIinternalError, InteractionClassNotPublished, InteractionParameterNotDefined, InteractionClassNotDefined, SaveInProgress, RestoreInProgress {
        if (window.isAvailable()) {
            window.startService(fedamb.federateTime);
        } else {
            if (fedamb.federateTime == window.getServiceTime()) {
                window.endService();

                ParameterHandleValueMap parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(1);
                addWindowIdHandle = rtiamb.getParameterHandle(freeWindowID, "windowId");
                HLAinteger32BE windowID = encoderFactory.createHLAinteger32BE(window.getId());

                parameterHandleValueMap.put(addWindowIdHandle, windowID.toByteArray());
                rtiamb.sendInteraction(freeWindowID, parameterHandleValueMap, generateTag());
            }
        }
    }

    private void enableTimePolicy() throws Exception {
        HLAfloat64Interval lookahead = timeFactory.makeInterval(fedamb.federateLookahead);
        this.rtiamb.enableTimeRegulation(lookahead);

        while (fedamb.isRegulating == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        this.rtiamb.enableTimeConstrained();
        while (fedamb.isConstrained == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    private void publishAndSubscribe() throws RTIexception {
        String moveCustomerToWindowName = "HLAinteractionRoot.moveCustomerToWindow";
        this.moveCustomerToWindow = rtiamb.getInteractionClassHandle(moveCustomerToWindowName);
        rtiamb.subscribeInteractionClass(this.moveCustomerToWindow);

        String assignCustomerToWindow = "HLAinteractionRoot.assignCustomerToWindow";
        this.assignCustomerToWindow = rtiamb.getInteractionClassHandle(assignCustomerToWindow);
        rtiamb.publishInteractionClass(this.assignCustomerToWindow);

        String freeWindowName = "HLAinteractionRoot.freeWindow";
        this.freeWindowID = rtiamb.getInteractionClassHandle(freeWindowName);
        rtiamb.publishInteractionClass(this.freeWindowID);
    }

    private void advanceTime(double timestep) throws RTIexception {
        fedamb.isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + timestep);
        rtiamb.timeAdvanceRequest(time);

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

        String federateName = "Window";
        if (args.length != 0) {
            federateName = args[0];
        }

        try {

            new WindowFederate().runFederate(federateName);
        } catch (Exception rtie) {

            rtie.printStackTrace();
        }
    }
}
